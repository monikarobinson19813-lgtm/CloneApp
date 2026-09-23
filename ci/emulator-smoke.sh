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

assert_single_test_evidence() {
  local evidence="$1"
  bash ci/assert-single-instrumentation-test.sh "$evidence"
  echo "GUARD_POSITIVE_CONTROL_ACCEPTED file=$evidence expected=OK (1 test)"
}

adb wait-for-device
adb shell getprop sys.boot_completed | grep -q "1"

git rev-parse HEAD | tee ci-artifacts/evidence/ca-commit.txt
{
  echo "github_sha=${GITHUB_SHA:-unknown}"
  echo "workflow_run_id=${GITHUB_RUN_ID:-unknown}"
  echo "workflow_run_number=${GITHUB_RUN_NUMBER:-unknown}"
  echo "android_release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "android_sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "manufacturer=$(adb shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "device=$(adb shell getprop ro.product.device | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "abilist=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
} | tee ci-artifacts/evidence/environment.txt

sha256sum ci-artifacts/testapp/testapp-debug.apk | tee ci-artifacts/evidence/testapp-apk-sha256.txt

primary_abi="$(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
if [ "$primary_abi" = "arm64-v8a" ]; then
  echo "NATIVE_ARM64_ENVIRONMENT_AVAILABLE" | tee ci-artifacts/evidence/native-arm64-probe-status.txt
else
  echo "NOT_EXECUTED_NATIVE_ARM64_ENVIRONMENT_UNAVAILABLE_PRIMARY_ABI=${primary_abi}" | tee ci-artifacts/evidence/native-arm64-probe-status.txt
fi

printf 'OK (0 tests)\n' > ci-artifacts/evidence/instrumentation-zero-test-probe.txt
if bash ci/assert-single-instrumentation-test.sh \
  ci-artifacts/evidence/instrumentation-zero-test-probe.txt; then
  echo "ERROR: exact-count guard accepted OK (0 tests)" >&2
  exit 1
else
  echo "Exact-count guard rejected OK (0 tests) as required"
fi

validate_missing_method_probe() {
  local adb_status="$1"
  local evidence="$2"

  if [ "$adb_status" -ne 0 ]; then
    echo "PROBE_REJECT_ADB_FAILURE status=$adb_status" >&2
    return 21
  fi

  if [ ! -s "$evidence" ]; then
    echo "PROBE_REJECT_EMPTY_OUTPUT" >&2
    return 22
  fi

  if ! grep -Fxq 'OK (0 tests)' "$evidence"; then
    echo "PROBE_REJECT_UNEXPECTED_OUTPUT" >&2
    return 23
  fi

  echo "PROBE_ACCEPT_EXPLICIT_ZERO_TESTS"
  return 0
}

assert_probe_validator_rejects() {
  local expected_status="$1"
  local adb_status="$2"
  local evidence="$3"
  local expected_reason="$4"
  local label="$5"
  local validator_output
  local observed_status

  set +e
  validator_output="$(validate_missing_method_probe "$adb_status" "$evidence" 2>&1)"
  observed_status=$?
  set -e

  printf '%s\n' "$validator_output"

  if [ "$observed_status" -ne "$expected_status" ]; then
    echo "ERROR: probe validator negative control $label returned $observed_status, expected $expected_status" >&2
    exit 1
  fi

  if ! grep -Fq "$expected_reason" <<< "$validator_output"; then
    echo "ERROR: probe validator negative control $label did not match expected reason $expected_reason" >&2
    exit 1
  fi

  echo "PROBE_VALIDATOR_NEGATIVE_CONTROL label=$label expected_status=$expected_status observed_status=$observed_status reason=$expected_reason PASSED"
}

: > ci-artifacts/evidence/probe-empty-output.txt
printf 'OK (0 tests)\n' > ci-artifacts/evidence/probe-adb-failure.txt
printf 'OK (2 tests)\n' > ci-artifacts/evidence/probe-unexpected-output.txt

assert_probe_validator_rejects 22 0 ci-artifacts/evidence/probe-empty-output.txt PROBE_REJECT_EMPTY_OUTPUT empty-output
assert_probe_validator_rejects 21 7 ci-artifacts/evidence/probe-adb-failure.txt PROBE_REJECT_ADB_FAILURE adb-failure
assert_probe_validator_rejects 23 0 ci-artifacts/evidence/probe-unexpected-output.txt PROBE_REJECT_UNEXPECTED_OUTPUT unexpected-output

adb install -r ci-artifacts/cloneapp/app-debug.apk
adb install -r ci-artifacts/cloneapp-test/app-debug-androidTest.apk
adb install -r ci-artifacts/testapp/testapp-debug.apk
adb install -r ci-artifacts/testapp-test/testapp-debug-androidTest.apk

# Guard proof: a nonexistent filtered test method must produce AndroidJUnitRunner's
# explicit zero-test result. Empty output, adb/instrumentation failure, or unexpected
# output are independently validated as RED conditions above.
set +e
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#__missing_method_guard_probe__' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/instrumentation-missing-method-probe.txt
missing_method_adb_status=${PIPESTATUS[0]}
set -e

echo "PROBE_ADB_STATUS=$missing_method_adb_status"
validate_missing_method_probe \
  "$missing_method_adb_status" \
  ci-artifacts/evidence/instrumentation-missing-method-probe.txt

set +e
guard_output="$(bash ci/assert-single-instrumentation-test.sh \
  ci-artifacts/evidence/instrumentation-missing-method-probe.txt 2>&1)"
guard_status=$?
set -e

printf '%s\n' "$guard_output"

if [ "$guard_status" -ne 1 ]; then
  echo "ERROR: exact-count guard returned $guard_status for explicit OK (0 tests); expected rejection status 1" >&2
  exit 1
fi

expected_guard_reason='expected exactly one executed passing test (OK (1 test)): ci-artifacts/evidence/instrumentation-missing-method-probe.txt'
if ! grep -Fqx "$expected_guard_reason" <<< "$guard_output"; then
  echo "ERROR: exact-count guard did not reject for the expected exact-one-test reason" >&2
  exit 1
fi

if ! grep -Fxq 'OK (0 tests)' <<< "$guard_output"; then
  echo "ERROR: exact-count guard rejection diagnostic did not preserve the observed OK (0 tests)" >&2
  exit 1
fi

echo "HARNESS_MATCHED_GUARD_REASON=ZERO_TESTS guard_status=$guard_status reason=$expected_guard_reason"

adb shell am force-stop com.cloneapp.ca || true
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2
adb shell pidof com.cloneapp.ca > ci-artifacts/evidence/cloneapp-pid.txt
test -s ci-artifacts/evidence/cloneapp-pid.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importApkThroughDocumentPickerContract'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-import-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after.png || true
adb shell run-as com.cloneapp.ca cat shared_prefs/ca_guest_apks.xml   > ci-artifacts/evidence/apk-import-record.xml
adb shell run-as com.cloneapp.ca ls -l files/guest-apks   > ci-artifacts/evidence/apk-import-private-files.txt

adb shell am force-stop com.cloneapp.ca
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkRecordSurvivesRelaunch'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-import-relaunch-instrumentation.txt
adb exec-out screencap -p > ci-artifacts/evidence/apk-import-after-relaunch.png || true

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkPreservesOriginalPackageIdentityAndSigningCertificate' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/apk-import-integrity-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-import-integrity-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkMetadataMatchesFixtureAndSurvivesRestart'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-metadata-instrumentation.txt

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkMetadataRepresentsEveryDeclaredComponentCategory' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/apk-metadata-components-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-metadata-components-instrumentation.txt

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.ApkImportRuntimeTest#importedApkMetadataReportsDeclaredNativeAbiAndLibraryEntries' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/apk-metadata-native-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-metadata-native-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#metadataParseFailureIsExplicitAndNonCrashing'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-metadata-failure-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.ApkImportRuntimeTest#invalidApkShowsVisibleErrorWithoutCrash'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-import-invalid-instrumentation.txt

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.ApkImportRuntimeTest#unreadableApkShowsVisibleErrorWithoutCrash' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/apk-import-unreadable-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/apk-import-unreadable-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.VirtualPackageRegistryRuntimeTest#aliceAndBobShareBasePackageButKeepSeparateVirtualInstances'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/virtual-package-registry-instrumentation.txt

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.GuestProcessHostRuntimeTest#aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/guest-process-host-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/guest-process-host-instrumentation.txt

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.GuestProcessHostRuntimeTest#guestProcessCanRestartAfterDeathWithSameVirtualIdentity' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/guest-process-recovery-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/guest-process-recovery-instrumentation.txt
adb shell dumpsys activity services com.cloneapp.ca > ci-artifacts/evidence/guest-stub-services.txt || true

adb shell am force-stop com.cloneapp.ca || true

adb shell am instrument -w -r   -e class 'com.cloneapp.ca.GuestActivityLaunchRuntimeTest#aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity'   com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner   | tee ci-artifacts/evidence/guest-activity-launch-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/guest-activity-launch-instrumentation.txt
adb shell dumpsys activity activities > ci-artifacts/evidence/guest-activity-launch-activities.txt || true

echo "CA_RESTART_BEGIN $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee ci-artifacts/evidence/ca-restart.txt
adb shell am force-stop com.cloneapp.ca
adb shell am start -W -n com.cloneapp.ca/.MainActivity
sleep 2
echo "CA_RESTART_COMPLETED $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a ci-artifacts/evidence/ca-restart.txt

adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.GuestActivityLaunchRuntimeTest#aliceAndBobLaunchDiagnosticsSurviveCaRestart' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/ca-restart-persistence-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/ca-restart-persistence-instrumentation.txt

adb shell am force-stop com.cloneapp.testapp || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#aliceAndBobPrivateStorageAreIndependent' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/storage-isolation-write-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/storage-isolation-write-instrumentation.txt

echo "DEVICE_REBOOT_BEGIN $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee ci-artifacts/evidence/device-reboot.txt
adb reboot
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
echo "DEVICE_REBOOT_BOOT_COMPLETED $(date -u +%Y-%m-%dT%H:%M:%SZ)" | tee -a ci-artifacts/evidence/device-reboot.txt

adb shell am force-stop com.cloneapp.testapp || true

adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.StorageIsolationRuntimeTest#stateSurvivesRestartAndDeletingAliceLeavesBobIntact' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/storage-isolation-reboot-delete-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/storage-isolation-reboot-delete-instrumentation.txt

adb shell am force-stop com.cloneapp.testapp || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.testapp.ProviderIsolationRuntimeTest#aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers' \
  com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/provider-isolation-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/provider-isolation-instrumentation.txt

adb shell am force-stop com.cloneapp.ca || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.ProviderAuthorityRoutingRuntimeTest#aliceAndBobUseDistinctVirtualAuthoritiesAndRouteToIsolatedPhysicalProviderState' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/provider-authority-routing-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/provider-authority-routing-instrumentation.txt

adb shell am force-stop com.cloneapp.ca || true
adb shell am instrument -w -r \
  -e class 'com.cloneapp.ca.NotificationTranslationRuntimeTest#aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle' \
  com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner \
  | tee ci-artifacts/evidence/notification-translation-instrumentation.txt

assert_single_test_evidence ci-artifacts/evidence/notification-translation-instrumentation.txt
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

echo "ISSUE_9_RUNTIME_RESULT=PASS" | tee ci-artifacts/evidence/result.txt
