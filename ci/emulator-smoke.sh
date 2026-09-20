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
adb install -r ci-artifacts/cloneapp-test/app-debug-androidTest.apk
adb install -r ci-artifacts/testapp/testapp-debug.apk

adb shell am force-stop com.cloneapp.ca || true
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2
adb shell pidof com.cloneapp.ca > ci-artifacts/evidence/cloneapp-pid.txt
test -s ci-artifacts/evidence/cloneapp-pid.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importApkThroughDocumentPickerContract'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/apk-import-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after.png || true
adb shell run-as com.cloneapp.ca cat shared_prefs/ca_guest_apks.xml   > ci-artifacts/evidence/apk-import-record.xml
adb shell run-as com.cloneapp.ca ls -l files/guest-apks   > ci-artifacts/evidence/apk-import-private-files.txt

adb shell am force-stop com.cloneapp.ca
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkRecordSurvivesRelaunch'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after-relaunch.png || true

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkMetadataMatchesFixtureAndSurvivesRestart'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/apk-metadata-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#metadataParseFailureIsExplicitAndNonCrashing'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#invalidApkShowsVisibleErrorWithoutCrash'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.VirtualPackageRegistryRuntimeTest#aliceAndBobShareBasePackageButKeepSeparateVirtualInstances'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.GuestProcessHostRuntimeTest#aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/guest-process-host-instrumentation.txt

grep -q '^OK (' ci-artifacts/evidence/guest-process-host-instrumentation.txt
adb shell dumpsys activity services com.cloneapp.ca > ci-artifacts/evidence/guest-stub-services.txt || true

adb shell am force-stop com.cloneapp.testapp || true
adb shell am start -W -n com.cloneapp.testapp/.MainActivity
sleep 2
adb shell pidof com.cloneapp.testapp > ci-artifacts/evidence/testapp-pid.txt
test -s ci-artifacts/evidence/testapp-pid.txt

adb shell pm path com.cloneapp.ca > ci-artifacts/evidence/cloneapp-package-path.txt
adb shell pm path com.cloneapp.testapp > ci-artifacts/evidence/testapp-package-path.txt

grep -q "package:" ci-artifacts/evidence/cloneapp-package-path.txt
grep -q "package:" ci-artifacts/evidence/testapp-package-path.txt

echo "CloneApp emulator smoke + import + metadata + virtual registry + guest stub process acceptance PASS" | tee ci-artifacts/evidence/result.txt
