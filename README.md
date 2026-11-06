# SpritzStore

Installs and updates the SpritzLive Android apps and SpritzStore itself without
Google Play, pulling signed release APKs from GitHub Releases. The default list is
all in the public store at `cook1991.github.io` (SpritzStore, Messages, AuthVault,
Douay-Rheims Bible, SkyCast, OdoTrack, identifysong, securemessenger), so it works for
anyone with no GitHub token. Private repos can be
added by hand on phones that have one. See `DESIGN.md`
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
3. **Only to track private repos:** create a GitHub fine-grained PAT -- Settings -> Developer settings -> Personal
   access tokens -> Fine-grained tokens on github.com. Scope it to exactly the repos
   you want tracked, **Contents: Read-only** permission, nothing else. Max expiry is
   90 days -- you'll be back to rotate it.
4. **Paste the token** into SpritzStore's Settings screen.
5. **Add/remove tracked repos** as needed with the + button and each row's Remove.
   The default list is `DEFAULT_TRACKED_REPOS` in `model/TrackedRepo.kt`. Upgrading
   keeps a phone's saved list, repointing apps that moved to the store repo. To add
   the Bible there: owner `cook1991`, repo `cook1991.github.io`, applicationId
   `org.drbo.bible`, store app `drb`.

## Publishing an update

The default apps publish to the public `cook1991.github.io` repo, so their source can
stay private. It holds their APKs as releases tagged `<app>-v<version>`, their download
pages, and `store.json`, the list SpritzStore reads. From that repo:

```bash
python scripts/publish.py odotrack
```

Any other app has a repo of its own -- attach the signed release APK to a GitHub
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
- No telemetry, no analytics, talks to nothing but GitHub.
- Doesn't touch Google Play Services at all -- this only replaces Play Store's
  app-install role for this app family, nothing else on the phone.
