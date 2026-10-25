package net.spritzlive.spritzstore.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.spritzlive.spritzstore.data.SpritzStorePrefs
import net.spritzlive.spritzstore.model.AppUpdateState
import net.spritzlive.spritzstore.notify.Notifications

/**
 * Periodic background check -- see [net.spritzlive.spritzstore.SpritzStoreApp] for the
 * schedule. Runs on plain `WorkManager` (JobScheduler/AlarmManager under the hood on
 * modern Android), not GCM Network Manager -- no GMS dependency.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = SpritzStorePrefs(applicationContext)
        // No token is fine -- public repos (e.g. ones shared with someone who isn't the
        // repo owner) still check correctly with no Authorization header at all.
        val token = prefs.githubToken()
        val checker = UpdateChecker(applicationContext)

        for (repo in prefs.trackedRepos()) {
            val state = checker.check(repo, token)
            if (state is AppUpdateState.UpdateAvailable && state.latestVersionCode != repo.lastNotifiedVersionCode) {
                Notifications.notifyUpdateAvailable(
                    applicationContext,
                    notificationId = repo.slug.hashCode(),
                    appName = repo.repo,
                    latestVersionName = state.latestVersionName,
                )
                prefs.markNotified(repo.slug, state.latestVersionCode)
            }
        }
        return Result.success()
    }
}
