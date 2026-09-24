#!/usr/bin/env bash
set -euo pipefail

adb install dist/ReaderLB-upgrade-baseline.apk

BASELINE_CODE="$(
  adb shell dumpsys package com.readerlb.app |
    tr -d '\r' |
    sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' |
    head -n 1
)"
UID_BEFORE="$(
  adb shell dumpsys package com.readerlb.app |
    tr -d '\r' |
    sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' |
    head -n 1
)"

test "$BASELINE_CODE" = "$READERLB_SMOKE_BASELINE_CODE"
test -n "$UID_BEFORE"

adb shell am start -W   -n com.readerlb.app/.MainActivity >/dev/null

adb install -r dist/ReaderLB-release-candidate.apk

CURRENT_CODE="$(
  adb shell dumpsys package com.readerlb.app |
    tr -d '\r' |
    sed -n 's/.*versionCode=\([0-9][0-9]*\).*/\1/p' |
    head -n 1
)"
UID_AFTER="$(
  adb shell dumpsys package com.readerlb.app |
    tr -d '\r' |
    sed -n 's/.*userId=\([0-9][0-9]*\).*/\1/p' |
    head -n 1
)"

test "$CURRENT_CODE" = "$READERLB_SMOKE_CURRENT_CODE"
test "$UID_AFTER" = "$UID_BEFORE"

adb shell am start -W   -n com.readerlb.app/.MainActivity >/dev/null

echo "Release-signed in-place upgrade smoke passed."
