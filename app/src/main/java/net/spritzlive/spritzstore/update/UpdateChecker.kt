package net.spritzlive.spritzstore.update

import android.content.Context
import android.content.pm.PackageManager
import net.spritzlive.spritzstore.github.GitHubApi
import net.spritzlive.spritzstore.github.GitHubApiException
import net.spritzlive.spritzstore.model.AppUpdateState
import net.spritzlive.spritzstore.model.TrackedRepo
import java.io.File

/**
 * Core update logic, shared by the manual "refresh" action and the periodic
 * background worker. Never throws -- every failure mode comes back as
 * [AppUpdateState.Error] so callers don't need their own try/catch per repo.
 */
class UpdateChecker(
    private val context: Context,
    private val api: GitHubApi = GitHubApi(),
) {
    suspend fun check(repo: TrackedRepo, token: String): AppUpdateState {
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
                api.downloadAsset(repo.owner, repo.repo, apkAsset.id, token, apkFile)
            } catch (e: GitHubApiException) {
                return AppUpdateState.Error(e.message ?: "Download failed")
            } catch (e: Exception) {
                return AppUpdateState.Error(e.message ?: "Download failed")
            }
        }

        // The install decision is always made from the APK's own embedded
        // versionCode, never the release tag -- a tagging mistake on the publishing
        // side can then never cause a skipped update or a downgrade. See DESIGN.md.
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
