#!/usr/bin/env bash
set -euo pipefail

mkdir -p ci-artifacts/evidence

capture_evidence() {
  adb logcat -d > ci-artifacts/evidence/logcat.txt || true
  adb shell dumpsys activity activities > ci-artifacts/evidence/dumpsys-activity.txt || true
  adb shell dumpsys package com.cloneapp.ca > ci-artifacts/evidence/package-cloneapp.txt || true
  adb shell dumpsys package com.cloneapp.testapp > ci-artifacts/evidence/package-testapp.txt || true
  adb shell dumpsys notification --noredact > ci-artifacts/evidence/dumpsys-notification.txt || true
}
trap capture_evidence EXIT

adb wait-for-device
adb shell getprop sys.boot_completed | grep -q "1"

printf 'OK (0 tests)\n' > ci-artifacts/evidence/instrumentation-zero-test-probe.txt
if bash ci/assert-single-instrumentation-test.sh \
  ci-artifacts/evidence/instrumentation-zero-test-probe.txt; then
  echo "ERROR: exact-count guard accepted OK (0 tests)" >&2
  exit 1
else
  echo "Exact-count guard rejected OK (0 tests) as required"
fi

adb install -r ci-artifacts/cloneapp/app-debug.apk
adb install -r ci-artifacts/cloneapp-test/app-debug-androidTest.apk
adb install -r ci-artifacts/testapp/testapp-debug.apk
adb install -r ci-artifacts/testapp-test/testapp-debug-androidTest.apk

# Guard proof: a nonexistent filtered test method must produce AndroidJUnitRunner's
# explicit zero-test result. Empty output, adb/instrumentation failure, or any other
# result is a probe failure (RED), not an expected rejection.
set +e
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#__missing_method_guard_probe__' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/instrumentation-missing-method-probe.txt
missing_method_adb_status=${PIPESTATUS[0]}
set -e

if [ "$missing_method_adb_status" -ne 0 ]; then
  echo "ERROR: nonexistent-method probe adb/instrumentation command failed with status $missing_method_adb_status" >&2
  exit 1
fi

if ! grep -Fxq 'OK (0 tests)' ci-artifacts/evidence/instrumentation-missing-method-probe.txt; then
  echo "ERROR: nonexistent-method probe did not explicitly produce OK (0 tests)" >&2
  exit 1
fi

if bash ci/assert-single-instrumentation-test.sh \
  ci-artifacts/evidence/instrumentation-missing-method-probe.txt; then
  echo "ERROR: exact-count guard accepted a nonexistent filtered test method" >&2
  exit 1
else
  echo "Exact-count guard rejected explicit OK (0 tests) nonexistent-method probe as required"
fi

adb shell am force-stop com.cloneapp.ca || true
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2
adb shell pidof com.cloneapp.ca > ci-artifacts/evidence/cloneapp-pid.txt
test -s ci-artifacts/evidence/cloneapp-pid.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importApkThroughDocumentPickerContract'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/apk-import-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after.png || true
adb shell run-as com.cloneapp.ca cat shared_prefs/ca_guest_apks.xml   > ci-artifacts/evidence/apk-import-record.xml
adb shell run-as com.cloneapp.ca ls -l files/guest-apks   > ci-artifacts/evidence/apk-import-private-files.txt

adb shell am force-stop com.cloneapp.ca
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkRecordSurvivesRelaunch'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after-relaunch.png || true

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkMetadataMatchesFixtureAndSurvivesRestart'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/apk-metadata-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#metadataParseFailureIsExplicitAndNonCrashing'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#invalidApkShowsVisibleErrorWithoutCrash'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.VirtualPackageRegistryRuntimeTest#aliceAndBobShareBasePackageButKeepSeparateVirtualInstances'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.GuestProcessHostRuntimeTest#aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/guest-process-host-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/guest-process-host-instrumentation.txt
adb shell dumpsys activity services com.cloneapp.ca > ci-artifacts/evidence/guest-stub-services.txt || true

adb shell am force-stop com.cloneapp.ca || true

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.GuestActivityLaunchRuntimeTest#aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/guest-activity-launch-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/guest-activity-launch-instrumentation.txt
adb shell dumpsys activity activities > ci-artifacts/evidence/guest-activity-launch-activities.txt || true

adb shell am force-stop com.cloneapp.testapp || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#aliceAndBobPrivateStorageAreIndependent' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/storage-isolation-write-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/storage-isolation-write-instrumentation.txt

adb shell am force-stop com.cloneapp.testapp || true

adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#stateSurvivesRestartAndDeletingAliceLeavesBobIntact' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/storage-isolation-restart-delete-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/storage-isolation-restart-delete-instrumentation.txt

adb shell am force-stop com.cloneapp.testapp || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.ProviderIsolationRuntimeTest#aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/provider-isolation-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/provider-isolation-instrumentation.txt

adb shell am force-stop com.cloneapp.ca || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.NotificationTranslationRuntimeTest#aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/notification-translation-instrumentation.txt

bash ci/assert-single-instrumentation-test.sh ci-artifacts/evidence/notification-translation-instrumentation.txt
adb shell dumpsys notification --noredact > ci-artifacts/evidence/notification-translation-dumpsys.txt || true

adb shell am force-stop com.cloneapp.testapp || true
adb shell am start -W -n com.cloneapp.testapp/.MainActivity
sleep 2
adb shell pidof com.cloneapp.testapp > ci-artifacts/evidence/testapp-pid.txt
test -s ci-artifacts/evidence/testapp-pid.txt

adb shell pm path com.cloneapp.ca > ci-artifacts/evidence/cloneapp-package-path.txt
adb shell pm path com.cloneapp.testapp > ci-artifacts/evidence/testapp-package-path.txt

grep -q "package:" ci-artifacts/evidence/cloneapp-package-path.txt
grep -q "package:" ci-artifacts/evidence/testapp-package-path.txt

echo "CloneApp emulator smoke + import + metadata + virtual registry + stub process + controlled guest activity launch + private storage isolation + controlled provider isolation + notification translation acceptance PASS" | tee ci-artifacts/evidence/result.txt
