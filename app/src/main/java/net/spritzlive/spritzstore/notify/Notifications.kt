package net.spritzlive.spritzstore.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.spritzlive.spritzstore.MainActivity
import net.spritzlive.spritzstore.R

object Notifications {
    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_INSTALLS = "installs"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Update checks", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INSTALLS, "Install results", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notifyUpdateAvailable(context: Context, notificationId: Int, appName: String, latestVersionName: String) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update available")
            .setContentText("$appName $latestVersionName")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun notifyInstallResult(context: Context, notificationId: Int, appLabel: String, success: Boolean, detail: String?) {
        val title = if (success) "Installed" else "Install failed"
        val text = if (success) appLabel else "$appLabel: ${detail ?: "unknown error"}"
        val notification = NotificationCompat.Builder(context, CHANNEL_INSTALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
