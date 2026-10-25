package net.spritzlive.spritzstore.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

/**
 * Hands a downloaded APK to the system installer via the `PackageInstaller` session
 * API. This is the modern, non-root way to install from app code -- but it is **not**
 * silent: Android always shows its own "Install this app?" confirmation on top,
 * regardless of which app triggered it (SpritzStore, F-Droid, Obtainium, even the Play
 * Store without its system-level privileges all hit the same wall). See DESIGN.md,
 * "Why installs can't be silent".
 */
object ApkInstaller {
    const val EXTRA_APPLICATION_ID = "application_id"

    /** Caller must already hold [canRequestInstalls] == true. */
    fun install(context: Context, apkFile: File, applicationId: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)

        try {
            apkFile.inputStream().use { input ->
                session.openWrite(apkFile.name, 0, apkFile.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }

            val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                putExtra(EXTRA_APPLICATION_ID, applicationId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()
}
