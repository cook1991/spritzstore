package net.spritzlive.spritzstore.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import net.spritzlive.spritzstore.notify.Notifications

/**
 * Target of the `PendingIntent` passed to `PackageInstaller.Session.commit()`. Android
 * delivers the session result here regardless of whether SpritzStore's UI is open.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val applicationId = intent.getStringExtra(ApkInstaller.EXTRA_APPLICATION_ID) ?: "unknown app"
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The one tap that can't be skipped on a stock, unrooted phone -- see
                // DESIGN.md "Why installs can't be silent". This confirmation Intent
                // comes from the system installer itself.
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmIntent?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Notifications.notifyInstallResult(context, applicationId.hashCode(), applicationId, success = true, detail = null)
            }
            else -> {
                Notifications.notifyInstallResult(context, applicationId.hashCode(), applicationId, success = false, detail = message)
            }
        }
    }
}
