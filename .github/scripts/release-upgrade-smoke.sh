#!/usr/bin/env bash
set -eu

read_version_code() {
  adb shell dumpsys package com.readerlb.app |
    tr -d '\r' |
    sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' |
    head -n 1
}

read_package_uid() {
  adb shell pm list packages -U com.readerlb.app |
    tr -d '\r' |
    sed -n 's/^package:com\.readerlb\.app[[:space:]]\+uid:\([0-9][0-9]*\).*$/\1/p' |
    head -n 1
}

adb install dist/ReaderLB-upgrade-baseline.apk

BASELINE_CODE="$(read_version_code)"
UID_BEFORE="$(read_package_uid)"

echo "Baseline package: versionCode=$BASELINE_CODE uid=$UID_BEFORE"
test "$BASELINE_CODE" = "$READERLB_SMOKE_BASELINE_CODE"
test -n "$UID_BEFORE"

adb shell am start -W \
  -n com.readerlb.app/.MainActivity >/dev/null

adb install -r dist/ReaderLB-release-candidate.apk

CURRENT_CODE="$(read_version_code)"
UID_AFTER="$(read_package_uid)"

echo "Updated package: versionCode=$CURRENT_CODE uid=$UID_AFTER"
test "$CURRENT_CODE" = "$READERLB_SMOKE_CURRENT_CODE"
test -n "$UID_AFTER"
test "$UID_AFTER" = "$UID_BEFORE"

adb shell am start -W \
  -n com.readerlb.app/.MainActivity >/dev/null

echo "Release-signed in-place upgrade smoke passed."
