# SpritzStore

Installs and updates the other `net.spritzlive.*` / sibling apps (authapp, smsmmsapp,
vmapp, mobilebrowser, pedometer, speedometer, weatherapp, policescanner,
identifysongapp, satellitephoneapp, and SpritzStore itself) without Google Play --
pulling signed release APKs straight from their private GitHub repos. See `DESIGN.md`
for the full plan and reasoning (why GitHub Releases instead of a self-hosted
manifest, why installs still need one tap, the redirect/auth-header gotcha, etc.).

## One-time setup, per phone

1. **Sideload SpritzStore itself.** There's no installer to bootstrap from -- build
   `app/build/outputs/apk/release/app-release.apk` (or grab it off a GitHub Release once
   one exists) and install it via `adb install` or by copying it over and tapping it.
   This is the one unavoidable manual step; every app after this, including
   SpritzStore's own future updates, installs through SpritzStore itself.
2. **Grant "Install unknown apps"** for SpritzStore -- Settings -> Settings screen in
   the app has a shortcut button, or Android Settings -> Apps -> SpritzStore -> Install
   unknown apps.
3. **Create a GitHub fine-grained PAT** -- Settings -> Developer settings -> Personal
   access tokens -> Fine-grained tokens on github.com. Scope it to exactly the repos
   you want tracked, **Contents: Read-only** permission, nothing else. Max expiry is
   90 days -- you'll be back to rotate it.
4. **Paste the token** into SpritzStore's Settings screen.
5. **Add/remove tracked repos** as needed (Settings seeds a default list of the known
   siblings, each with the `applicationId` from its own `build.gradle.kts` -- see
   `model/TrackedRepo.kt`'s `DEFAULT_TRACKED_REPOS`).

## Publishing an update, per sibling repo

Nothing new to build on that side -- just attach the signed release APK to a GitHub
Release:

```bash
cd <that-repo>
./gradlew assembleRelease
gh release create v1.2 app/build/outputs/apk/release/app-release.apk --title "v1.2" --notes "..."
```

SpritzStore finds the `.apk` asset automatically (first asset ending in `.apk` --
keep exactly one per release) and decides whether it's newer by reading the real
`versionCode` out of the downloaded APK, not by parsing the release tag.

## What it doesn't do (on purpose)

- No silent installs -- Android's own "Install this app?" confirmation shows every
  time, for every installer, on an unrooted stock phone. See DESIGN.md.
- No telemetry, no analytics, talks to nothing but `api.github.com`.
- Doesn't touch Google Play Services at all -- this only replaces Play Store's
  app-install role for this app family, nothing else on the phone.
