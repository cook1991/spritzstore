#!/usr/bin/env bash
# Template: copy into any sibling app repo, run from its root.
# Builds the signed release APK and attaches it to a new GitHub Release so
# SpritzStore can find and install it. Requires `gh auth login` once per machine.
set -euo pipefail

VERSION="${1:?Usage: scripts/release.sh vX.Y}"

./gradlew assembleRelease

APK=$(find app/build/outputs/apk/release -name "*.apk" | head -n1)
if [ -z "$APK" ]; then
  echo "No release APK found under app/build/outputs/apk/release -- did signing fail?" >&2
  exit 1
fi

gh release create "$VERSION" "$APK" --title "$VERSION" --generate-notes
