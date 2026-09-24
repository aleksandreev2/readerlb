#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="com.readerlb.app"
BASELINE_APK="dist/ReaderLB-upgrade-baseline.apk"
CURRENT_APK="dist/ReaderLB-release-candidate.apk"

package_field() {
  local expression="$1"
  adb shell dumpsys package "$PACKAGE_NAME" |
    tr -d '\r' |
    sed -n "$expression" |
    sed -n '1p'
}

version_code() {
  package_field 's/.*versionCode=\([0-9][0-9]*\).*/\1/p'
}

package_uid() {
  package_field 's/.*userId=\([0-9][0-9]*\).*/\1/p'
}

first_install_time() {
  package_field 's/^[[:space:]]*firstInstallTime=//p'
}

echo "Installing release-signed baseline..."
adb install "$BASELINE_APK"

BASELINE_CODE="$(version_code)"
UID_BEFORE="$(package_uid)"
FIRST_INSTALL_BEFORE="$(first_install_time)"

test "$BASELINE_CODE" = "$READERLB_SMOKE_BASELINE_CODE"
test -n "$UID_BEFORE"
test -n "$FIRST_INSTALL_BEFORE"

adb shell am start -W -n "$PACKAGE_NAME/.MainActivity" >/dev/null

echo "Updating baseline in place to current release candidate..."
adb install -r "$CURRENT_APK"

CURRENT_CODE="$(version_code)"
UID_AFTER="$(package_uid)"
FIRST_INSTALL_AFTER="$(first_install_time)"

test "$CURRENT_CODE" = "$READERLB_SMOKE_CURRENT_CODE"
test "$UID_AFTER" = "$UID_BEFORE"
test "$FIRST_INSTALL_AFTER" = "$FIRST_INSTALL_BEFORE"

adb shell am start -W -n "$PACKAGE_NAME/.MainActivity" >/dev/null

echo "Release-signed in-place upgrade smoke passed: versionCode $BASELINE_CODE -> $CURRENT_CODE, UID preserved."
