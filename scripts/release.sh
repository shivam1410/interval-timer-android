#!/usr/bin/env bash
# Builds a signed release APK and publishes it as a GitHub release (the app's updater reads it).
# Usage: scripts/release.sh 1.0.1 "What changed"
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=${1:?usage: scripts/release.sh <version> [notes]}
NOTES=${2:-"Release v$VERSION"}
REPO=shivam1410/interval-timer-android
export GH_TOKEN=$(gh auth token -u shivam1410)
export AW_NO_COAUTHOR=1 # no co-author trailers on this repo's commits

sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION/" gradle.properties
./gradlew -q testDebugUnitTest assembleRelease
APK=build/interval-timer-v$VERSION.apk
cp app/build/outputs/apk/release/app-release.apk "$APK"

git add -A
git commit -m "chore: release v$VERSION" || true
git tag "v$VERSION"
git push origin HEAD "v$VERSION"
gh release create "v$VERSION" "$APK" -R "$REPO" --title "v$VERSION" --notes "$NOTES"
