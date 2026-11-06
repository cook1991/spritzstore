package net.spritzlive.spritzstore.update

import android.content.Context
import android.content.pm.PackageManager
import net.spritzlive.spritzstore.github.GitHubApi
import net.spritzlive.spritzstore.github.GitHubApiException
import net.spritzlive.spritzstore.model.AppUpdateState
import net.spritzlive.spritzstore.model.TrackedRepo
import java.io.File
import java.security.MessageDigest

/**
 * Core update logic, shared by the manual "refresh" action and the periodic
 * background worker. Never throws -- every failure mode comes back as
 * [AppUpdateState.Error] so callers don't need their own try/catch per repo.
 */
class UpdateChecker(
    private val context: Context,
    private val api: GitHubApi = GitHubApi(),
) {
    /** [token] is optional -- null works fine for a public repo, see [GitHubApi]. */
    suspend fun check(repo: TrackedRepo, token: String?): AppUpdateState {
        if (repo.storeApp != null) return checkStoreApp(repo.applicationId, repo.storeApp)

        val installed = installedVersion(repo.applicationId)

        val release = try {
            api.latestRelease(repo.owner, repo.repo, token)
        } catch (e: GitHubApiException) {
            return AppUpdateState.Error(e.message ?: "GitHub API error")
        } catch (e: Exception) {
            return AppUpdateState.Error(e.message ?: "Network error")
        }

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAsset == null) {
            return if (installed != null) {
                AppUpdateState.UpToDate(installed.first, installed.second)
            } else {
                AppUpdateState.Error("Release ${release.tagName} has no .apk asset")
            }
        }

        // Caching under cacheDir, keyed by asset id -- a repeat check for the same
        // release (e.g. a second manual refresh before installing) reuses the file
        // instead of re-downloading.
        val apkFile = File(context.cacheDir, "update-${repo.repo}-${apkAsset.id}.apk")
        if (!apkFile.exists() || apkFile.length() != apkAsset.size) {
            try {
                api.downloadAsset(repo.owner, repo.repo, apkAsset, token, apkFile)
            } catch (e: GitHubApiException) {
                return AppUpdateState.Error(e.message ?: "Download failed")
            } catch (e: Exception) {
                return AppUpdateState.Error(e.message ?: "Download failed")
            }
        }

        return decide(apkFile, installed)
    }

    /**
     * The manifest states each app's versionCode, so checking downloads nothing: opening
     * the app, or a background check, doesn't spend the phone's data on APKs nobody has
     * asked for. [fetchStoreApk] gets the file once someone taps Install.
     */
    private suspend fun checkStoreApp(applicationId: String, storeApp: String): AppUpdateState {
        val installed = installedVersion(applicationId)
        val manifest = try {
            api.storeManifest()
        } catch (e: Exception) {
            return AppUpdateState.Error(e.message ?: "Couldn't reach the store")
        }
        val app = manifest.apps[storeApp]
            ?: return AppUpdateState.Error("$storeApp isn't in the store")
        if (installed != null && app.versionCode <= installed.second) {
            context.cacheDir.listFiles { file -> file.name.startsWith("update-$storeApp-") }?.forEach { it.delete() }
            return AppUpdateState.UpToDate(installed.first, installed.second)
        }
        return AppUpdateState.UpdateAvailable(
            installedVersionName = installed?.first,
            installedVersionCode = installed?.second ?: -1,
            latestVersionName = app.versionName,
            latestVersionCode = app.versionCode,
            apkFile = null,
        )
    }

    /** Downloads and verifies a store app's APK, ready to hand to the installer. */
    suspend fun fetchStoreApk(applicationId: String, storeApp: String): AppUpdateState {
        val installed = installedVersion(applicationId)
        val manifest = try {
            api.storeManifest()
        } catch (e: Exception) {
            return AppUpdateState.Error(e.message ?: "Couldn't reach the store")
        }
        val app = manifest.apps[storeApp]
            ?: return AppUpdateState.Error("$storeApp isn't in the store")

        val apkFile = File(context.cacheDir, "update-$storeApp-${app.versionCode}.apk")
        if (!apkFile.exists() || apkFile.length() != app.size) {
            try {
                api.downloadPublicFile(app.url, apkFile)
            } catch (e: Exception) {
                return AppUpdateState.Error(e.message ?: "Download failed")
            }
        }
        if (sha256(apkFile) != app.sha256) {
            apkFile.delete()
            return AppUpdateState.Error("The download of $storeApp didn't match the store's checksum")
        }
        return decide(apkFile, installed)
    }

    private fun decide(apkFile: File, installed: Pair<String, Long>?): AppUpdateState {
        // The install decision is always made from the APK's own embedded
        // versionCode, never the release tag or the manifest -- a mistake on the
        // publishing side can then never cause a skipped update or a downgrade. See
        // DESIGN.md.
        val archiveInfo = archiveInfo(apkFile.path)
            ?: return AppUpdateState.Error("Couldn't read the downloaded APK's manifest")
        val latestVersionCode = archiveInfo.first
        val latestVersionName = archiveInfo.second

        return when {
            installed == null -> AppUpdateState.UpdateAvailable(
                installedVersionName = null,
                installedVersionCode = -1,
                latestVersionName = latestVersionName,
                latestVersionCode = latestVersionCode,
                apkFile = apkFile,
            )
            latestVersionCode > installed.second -> AppUpdateState.UpdateAvailable(
                installedVersionName = installed.first,
                installedVersionCode = installed.second,
                latestVersionName = latestVersionName,
                latestVersionCode = latestVersionCode,
                apkFile = apkFile,
            )
            else -> {
                apkFile.delete()
                AppUpdateState.UpToDate(installed.first, installed.second)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun installedVersion(applicationId: String): Pair<String, Long>? = try {
        val info = context.packageManager.getPackageInfo(applicationId, 0)
        (info.versionName ?: "?") to info.longVersionCode
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(apkPath: String): Pair<Long, String>? {
        val info = context.packageManager.getPackageArchiveInfo(apkPath, 0) ?: return null
        return info.longVersionCode to (info.versionName ?: "?")
    }
}
