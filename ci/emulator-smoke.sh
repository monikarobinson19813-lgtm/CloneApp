#!/usr/bin/env bash
set -euo pipefail

mkdir -p ci-artifacts/evidence

capture_evidence() {
  adb logcat -d > ci-artifacts/evidence/logcat.txt || true
  adb shell dumpsys activity activities > ci-artifacts/evidence/dumpsys-activity.txt || true
  adb shell dumpsys package com.cloneapp.ca > ci-artifacts/evidence/package-cloneapp.txt || true
  adb shell dumpsys package com.cloneapp.testapp > ci-artifacts/evidence/package-testapp.txt || true
}
trap capture_evidence EXIT

adb wait-for-device
adb shell getprop sys.boot_completed | grep -q "1"

adb install -r ci-artifacts/cloneapp/app-debug.apk
adb install -r ci-artifacts/testapp/testapp-debug.apk

adb shell am force-stop com.cloneapp.ca || true
adb shell monkey -p com.cloneapp.ca -c android.intent.category.LAUNCHER 1
sleep 3
adb shell pidof com.cloneapp.ca > ci-artifacts/evidence/cloneapp-pid.txt
test -s ci-artifacts/evidence/cloneapp-pid.txt

adb shell am force-stop com.cloneapp.testapp || true
adb shell monkey -p com.cloneapp.testapp -c android.intent.category.LAUNCHER 1
sleep 3
adb shell pidof com.cloneapp.testapp > ci-artifacts/evidence/testapp-pid.txt
test -s ci-artifacts/evidence/testapp-pid.txt

adb shell pm path com.cloneapp.ca > ci-artifacts/evidence/cloneapp-package-path.txt
adb shell pm path com.cloneapp.testapp > ci-artifacts/evidence/testapp-package-path.txt

grep -q "package:" ci-artifacts/evidence/cloneapp-package-path.txt
grep -q "package:" ci-artifacts/evidence/testapp-package-path.txt

echo "CloneApp emulator smoke test PASS" | tee ci-artifacts/evidence/result.txt
