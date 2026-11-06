package net.spritzlive.spritzstore.model

import kotlinx.serialization.Serializable

/**
 * One app SpritzStore knows how to install/update. [lastNotifiedVersionCode] is purely
 * a dedupe cache for the background worker (don't re-notify about the same available
 * update on every periodic run) -- the actual install decision is always made by
 * comparing the real versionCode embedded in the downloaded APK against the installed
 * app's versionCode, never from anything cached here. See DESIGN.md.
 *
 * [storeApp] names this app's entry in the store manifest. Null means it's checked
 * through its own repo's Releases on the GitHub API instead -- the way to track a
 * private repo.
 */
@Serializable
data class TrackedRepo(
    val owner: String,
    val repo: String,
    val applicationId: String,
    val lastNotifiedVersionCode: Long? = null,
    val storeApp: String? = null,
) {
    val slug: String get() = if (storeApp == null) "$owner/$repo" else "$owner/$repo#$storeApp"
    val displayName: String get() = storeApp ?: repo
}

private fun storeApp(storeApp: String, applicationId: String) =
    TrackedRepo("cook1991", "cook1991.github.io", applicationId, storeApp = storeApp)

/**
 * Seeded on first launch. Everything here is in the store manifest, so a fresh install
 * works for anyone with no GitHub token -- add private repos by hand with the + button
 * on phones that have one. `applicationId` values below are copied verbatim from each
 * repo's own build file -- they're inconsistent across the family (some
 * `net.spritzlive.*`, some `com.spritzlive.*`, a couple of outliers) because each app
 * picked its own `applicationId` before this project existed; SpritzStore doesn't
 * normalize that, it just has to track whatever each app actually ships as.
 */
val DEFAULT_TRACKED_REPOS = listOf(
    storeApp("spritzstore", "net.spritzlive.spritzstore"),
    storeApp("messages", "net.spritzlive.smsmms"),
    storeApp("authvault", "net.spritzlive.authvault"),
    storeApp("drb", "org.drbo.bible"),
    storeApp("skycast", "com.spritzlive.weather"),
    storeApp("odotrack", "com.spritzlive.speedometer"),
    storeApp("identifysong", "net.spritzlive.identifysong"),
    storeApp("securemessenger", "net.spritzlive.securemessenger"),
)

/**
 * Lists saved before an app moved to the store still point at its source repo, which
 * no longer gets new releases. Repoint those entries by applicationId; anything else
 * on the list, and which apps are on it, stays as the person left it.
 */
fun migrateToStore(saved: List<TrackedRepo>): List<TrackedRepo> = saved.map { tracked ->
    val moved = DEFAULT_TRACKED_REPOS.firstOrNull { it.applicationId == tracked.applicationId }
    moved?.copy(lastNotifiedVersionCode = tracked.lastNotifiedVersionCode) ?: tracked
}
