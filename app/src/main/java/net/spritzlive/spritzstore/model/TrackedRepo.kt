package net.spritzlive.spritzstore.model

import kotlinx.serialization.Serializable

/**
 * One app SpritzStore knows how to install/update. [lastNotifiedVersionCode] is purely
 * a dedupe cache for the background worker (don't re-notify about the same available
 * update on every periodic run) -- the actual install decision is always made by
 * comparing the real versionCode embedded in the downloaded APK against the installed
 * app's versionCode, never from anything cached here. See DESIGN.md.
 */
@Serializable
data class TrackedRepo(
    val owner: String,
    val repo: String,
    val applicationId: String,
    val lastNotifiedVersionCode: Long? = null,
) {
    val slug: String get() = "$owner/$repo"
}

/**
 * Seeded on first launch. Edit in Settings -> Tracked apps as new sibling apps appear
 * (or to drop one). `applicationId` values below are copied verbatim from each repo's
 * own `app/build.gradle.kts` -- they're inconsistent across the family (some
 * `net.spritzlive.*`, some `com.spritzlive.*`, a couple of outliers) because each app
 * picked its own `applicationId` before this project existed; SpritzStore doesn't
 * normalize that, it just has to track whatever each app actually ships as.
 *
 * `stopscammers` is intentionally not here -- it's a PHP web service, not an
 * installable Android app. `bible` is left out for now; its project layout didn't
 * match the other Gradle apps closely enough to copy an `applicationId` with
 * confidence -- add it by hand in Settings once confirmed.
 */
val DEFAULT_TRACKED_REPOS = listOf(
    TrackedRepo("cook1991", "spritzstore", "net.spritzlive.spritzstore"),
    // Local folder is "authapp" but the actual GitHub repo is "authvault" -- verified
    // against its real `git remote -v`, don't assume folder name == repo name.
    TrackedRepo("cook1991", "authvault", "net.spritzlive.authvault"),
    TrackedRepo("cook1991", "smsmmsapp", "net.spritzlive.smsmms"),
    TrackedRepo("cook1991", "vmapp", "net.spritzlive.voicemail"),
    TrackedRepo("cook1991", "mobilebrowser", "net.spritzlive.mobilebrowser"),
    TrackedRepo("cook1991", "pedometer", "com.spritzlive.pedometer"),
    TrackedRepo("cook1991", "speedometer", "com.spritzlive.speedometer"),
    TrackedRepo("cook1991", "weatherapp", "com.spritzlive.weather"),
    TrackedRepo("cook1991", "policescanner", "net.inetvisions.policescanner"),
    TrackedRepo("cook1991", "identifysongapp", "net.spritzlive.identifysong"),
    TrackedRepo("cook1991", "satellitephoneapp", "com.satphone"),
    // Public repo (unlike the rest of this list) -- no PAT needed to check/download
    // it specifically, since it's meant to be installed by Eric too, not just Jason.
    // Eric's phone should have the other, private entries above pruned out via
    // Settings -> Tracked apps (no token on his install -> those would just show
    // failed checks), keeping this one.
    TrackedRepo("cook1991", "securemessenger", "net.spritzlive.securemessenger"),
)
