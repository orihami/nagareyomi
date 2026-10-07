#!/usr/bin/env bash
#
# Runs the Compose UI instrumented tests against the already-booted emulator,
# pulls the in-test screenshots (captured via Compose's captureToImage() at
# each assertion point, so they show the real "Hello Android" -> tap ->
# "Hello Compose!" states, not just whatever is on screen after the test
# process has already exited), plus a final full-screen capture and logcat,
# and finally exits with the tests' own exit code.
#
# IMPORTANT: reactivecircus/android-emulator-runner's `script:` input runs
# each line of a multi-line block as its OWN separate shell invocation, so
# shell variables (like a captured $? exit code) do NOT persist from one
# line to the next. Keeping all of this logic in a single script file that
# is invoked as one line avoids that trap.

set -uo pipefail

APP_ID="com.orihami.nagareyomi"
# Internal app data dir (not the external/"sdcard" files dir - see the
# comment on ReaderFlowUiTest.saveScreenshot() for why).
DEVICE_SCREENSHOT_DIR="files/ui-test-screenshots"

adb wait-for-device
mkdir -p artifacts/screenshots/ui-test-screenshots artifacts/logs

# -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true keeps the
# app (and test APK) installed after the connected test task finishes.
# Without it, Gradle uninstalls both as part of its normal cleanup, which
# also removes the app's external-files directory - so by the time we try
# to `run-as` into it below, the package no longer exists on the device at
# all ("run-as: unknown package"), even though the tests themselves passed.
# (Confirmed property name via https://github.com/android/nowinandroid/pull/1531,
# which fixed the same issue; a first attempt at this used the similarly
# named but incorrect `leaveApksInstalled`, without the `AfterRun` suffix.)
./gradlew connectedDebugAndroidTest --stacktrace \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
TEST_EXIT_CODE=$?

# --- Diagnostics: kept so future failures of this pull step are visible in
# the uploaded logs instead of silently producing empty files. ---
{
  echo "== id =="
  adb shell id
  echo "== run-as id (as ${APP_ID}) =="
  adb shell run-as "$APP_ID" id
  echo "== run-as ls internal files dir =="
  adb shell run-as "$APP_ID" ls -laR "$DEVICE_SCREENSHOT_DIR" 2>&1
} > artifacts/logs/screenshot-pull-diagnostics.txt 2>&1 || true

# Screenshots saved by GreetingScreenUiTest at each assertion point, under the
# app's own internal data directory. That path isn't reachable by a plain
# `adb pull`/`cat` (only the app's own UID can read it), so pull each file
# via `run-as`, which executes as that (debuggable) app's own UID.
# Pull every screenshot the tests saved (names are listed on the device).
for name in $(adb shell run-as "$APP_ID" ls "$DEVICE_SCREENSHOT_DIR" 2>/dev/null | tr -d '\r'); do
  dest="artifacts/screenshots/ui-test-screenshots/${name}"
  adb shell run-as "$APP_ID" cat "${DEVICE_SCREENSHOT_DIR}/${name}" > "$dest" 2>>artifacts/logs/screenshot-pull-diagnostics.txt
  if [ -s "$dest" ]; then
    echo "Pulled ${name} ($(wc -c < "$dest") bytes)"
  else
    echo "::warning::Could not pull ${name} from the device (empty or missing)."
    rm -f "$dest"
  fi
done

# A generic full-screen capture of whatever is on screen when this runs
# (kept as a secondary sanity check; the pulled UI-test screenshots above
# are the authoritative "before/after tap" evidence).
adb exec-out screencap -p > artifacts/screenshots/emulator-final-state.png

adb logcat -d > artifacts/logs/logcat-full.txt
adb logcat -d -s "System.err" "AndroidRuntime" "TestRunner" > artifacts/logs/logcat-errors.txt || true

exit "$TEST_EXIT_CODE"
