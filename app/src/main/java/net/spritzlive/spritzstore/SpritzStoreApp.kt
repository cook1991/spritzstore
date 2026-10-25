package net.spritzlive.spritzstore

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import net.spritzlive.spritzstore.notify.Notifications
import net.spritzlive.spritzstore.update.UpdateCheckWorker
import java.util.concurrent.TimeUnit

class SpritzStoreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // KEEP: don't reset the schedule/backoff on every app launch, only on first install.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
