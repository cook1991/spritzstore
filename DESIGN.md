# SpritzStore — design

A self-built Android app that installs and updates the other `net.spritzlive.*` apps
(authapp, smsmmsapp, vmapp, mobilebrowser, pedometer, speedometer, weatherapp,
policescanner, identifysongapp, stopscammers, satellitephoneapp, bible, ...) without
Google Play — by pulling signed release APKs straight from the private GitHub repos
they already live in. This file is the plan; nothing is built yet, same convention as
`authapp`'s `DESIGN.md` before it was built.

Working name: **SpritzStore**. Package: `net.spritzlive.spritzstore`. Both are easy to
change now; `applicationId` is permanent once this is ever installed on the phone for
real (same caveat as every sibling app).

## Why this exists

Disabling Google Play Services outright on a stock phone is not a safe move — GMS is
quietly load-bearing for things like RCS/Wi-Fi calling and system WebView updates on
most OEM builds, so "just turn it off" breaks the phone, not just the Google parts.
The actual goal is narrower: **stop depending on the Play *Store*** to install and
update the apps in this family, without touching GMS itself at all. SpritzStore is the
one app that needs network access and the install-packages permission so that none of
the others have to.

## Why GitHub Releases, not a self-hosted manifest

Every sibling repo is already pushed to private repos under `github.com/cook1991` (see
the GitHub migration). GitHub Releases on those repos already gives us, for free: a
place to attach a signed APK, a version history, and an authenticated download API for
private repos. No PHP endpoint, no second signing key, no new thing to keep running on
the Plesk box — the existing GitHub account *is* the backend.

## Repo tracking list

User-editable in-app, not hardcoded, so adding a new sibling app later doesn't require
rebuilding SpritzStore itself. Stored in `DataStore<Preferences>` (same pattern as
`authapp`'s non-secret settings) as a JSON array:

```json
[
  { "owner": "cook1991", "repo": "authapp", "applicationId": "net.spritzlive.authvault" },
  { "owner": "cook1991", "repo": "smsmmsapp", "applicationId": "net.spritzlive.smsmms" },
  { "owner": "cook1991", "repo": "vmapp", "applicationId": "net.spritzlive.vmapp" },
  { "owner": "cook1991", "repo": "spritzstore", "applicationId": "net.spritzlive.spritzstore" }
]
```

Seeded on first launch with the known sibling apps (exact `applicationId` values pulled
from each repo's `app/build.gradle.kts` at build time). **SpritzStore tracks itself** —
same update mechanism updates SpritzStore, no separate bootstrap path once it's past
the very first manual install.

## Auth: GitHub token

A **fine-grained personal access token**, not a classic one — scoped to only these
specific repos, **Contents: Read-only** permission (that's all the Releases API needs),
90-day max expiry. Entered once in Settings, stored via
`androidx.security.crypto.EncryptedSharedPreferences` (Keystore-backed AES, same
security tier as `authapp`'s vault), never logged, never sent anywhere but
`api.github.com` over HTTPS.

The 90-day expiry is a real maintenance item, not a one-time setup step — same shape as
the Cloudflare IP-range and Origin Cert renewal reminders already tracked for other
projects. Worth a calendar reminder; SpritzStore should also surface a plain "token
rejected (401) — go generate a new one" state in the UI rather than failing silently,
since a GitHub 401 and "no update available" look identical if you don't distinguish
them.

## Update check flow

WorkManager periodic job (e.g. every 12h, `NetworkType.CONNECTED` constraint) — no GMS
dependency; WorkManager runs on `JobScheduler`/`AlarmManager` under the hood on modern
Android, not on the old GCM Network Manager fallback.

Per tracked repo:

1. `GET https://api.github.com/repos/{owner}/{repo}/releases/latest`
   header `Authorization: Bearer <token>`.
2. Compare the release's **tag** against the last-seen tag cached for that repo
   (DataStore). Same tag → skip, no download. This is purely a dedupe cache, not the
   update decision.
3. New tag → find the asset whose name ends in `.apk` (convention: exactly one `.apk`
   asset per release; if more than one is found, log a warning and take the first —
   this is a release-hygiene bug on the publishing side, not something to guess
   around).
4. Download that asset via its API URL (`GET .../releases/assets/{id}`, header
   `Accept: application/octet-stream` + the same bearer token). GitHub 302s this to a
   pre-signed S3/Azure blob URL — **do not re-send the Authorization header on that
   redirect** (OkHttp drops it automatically on a cross-host redirect by default; if
   swapping HTTP clients later, this is the one thing that will silently break and
   look like a 400 from the blob store, not a GitHub error).
5. Read the downloaded file's **actual embedded version**, not the tag name, via
   `packageManager.getPackageArchiveInfo(file.path, 0)!!.longVersionCode`. The tag is
   only ever used for the dedupe cache in step 2 — the install decision is always made
   from the real `versionCode` of the installed app (`PackageInfo.longVersionCode`)
   vs. the real `versionCode` inside the downloaded APK. This way a release tagging
   mistake can never cause a downgrade or a skipped update.
6. Newer `versionCode` → surface a notification ("Update available: AuthVault 1.1 →
   1.2"). Tapping it opens SpritzStore to that app's detail screen with an
   **Install** button — not an automatic install. See "Why installs can't be silent"
   below for why there's a tap here at all.

## Install flow

`PackageInstaller` session API (`SessionParams(MODE_FULL_INSTALL)`), streaming the
downloaded APK's bytes into the session, then `commit()` with a `PendingIntent` to
receive the result. This is the standard, non-root way to install an APK from app code
on stock Android.

### Why installs can't be silent

On an unrooted, stock (non-device-owner) phone, **every** install or update through
`PackageInstaller` — regardless of which app triggers it — shows the system's "Install
unknown app?" / package-install confirmation screen once per install. This is true for
SpritzStore exactly as it's true for F-Droid, Obtainium, or Play Store running without
its system-level privileges. The only ways around that one tap are rooting the device,
making SpritzStore the **device owner** (practical only on a dedicated/managed device,
not a daily phone), or pairing with **Shizuku** (grants ADB-shell-level install rights
to a specific app without full root, via a one-time wireless-debugging pairing) — worth
revisiting later as a quality-of-life add-on, out of scope for v1.

One thing Android enforces for free, no code needed: an update **must** be signed with
the same certificate as the currently-installed version, or the install silently fails.
Since every sibling app already has its own stable `upload-keystore.jks`, this means a
tampered or wrongly-built APK simply can't install over the real one — no checksum
plumbing required for that specific risk. (A SHA-256 sidecar asset + verification before
install would still catch a *corrupted* download, but that's an enhancement for later,
not a v1 requirement — HTTPS transport integrity already covers the common case.)

### Permissions

- `INTERNET` — the one sibling app in this family for which that's unavoidable; its
  entire job is talking to `api.github.com`.
- `REQUEST_INSTALL_PACKAGES` — granted once via Settings ("Install unknown apps" for
  SpritzStore specifically), same one-time grant every sideloaded installer needs.
- Nothing else. No contacts, no SMS, no location — this app only ever touches GitHub's
  API and the package installer.

## Bootstrapping a new phone

SpritzStore itself has no installer to fall back on the very first time — it has to be
sideloaded once manually (ADB `install`, or copy the APK over and tap it with "install
unknown apps" allowed for Files/whatever app does the tap). Every app after that,
including SpritzStore's own future updates, goes through SpritzStore. This is the same
one-time bootstrap every alternative app store has (F-Droid and Obtainium both require
one manual sideload to get started too) — not a gap specific to building it ourselves.

## Publishing side: what each sibling repo needs

Nothing new to build there, but a habit to adopt per release: after
`./gradlew assembleRelease`, attach the signed APK to a GitHub Release on that repo —

```bash
gh release create v1.2 app/build/outputs/apk/release/app-release.apk \
  --title "v1.2" --notes "..."
```

A tiny `scripts/release.sh` wrapping that (build + `gh release create`) is a reasonable
follow-up once SpritzStore itself works, but isn't part of this app — it's a one-line
habit change on the existing release process, not new infrastructure.

## Open questions / decisions still mine to make during build

- Exact WorkManager interval (12h default above — fine to tune).
- Whether the asset-picking convention (first `.apk` found) needs to be stricter once
  there's more than one real release to test against.
- UI is a simple list (app name, installed version, latest version, status) + per-app
  detail screen — no need for anything fancier than the existing sibling apps' home
  screens.

## Non-goals

- Not a general-purpose F-Droid replacement — it only ever knows about repos explicitly
  added to its tracking list.
- Not silent/background installs (see "Why installs can't be silent").
- Not a public app store — the GitHub repos stay private; this is personal
  infrastructure for one phone (or a few, all owned by the same account).
