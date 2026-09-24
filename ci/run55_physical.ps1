param(
    [string]$AdbPath = "",
    [switch]$SkipPackageReset,
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^\d+

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkDir = Join-Path $Root "apks"
$EvidenceRoot = Join-Path $Root "evidence"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Evidence = Join-Path $EvidenceRoot $Stamp
New-Item -ItemType Directory -Force -Path $Evidence | Out-Null

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    if (Test-Path (Join-Path (Get-Location) "adb.exe")) {
        $AdbPath = (Join-Path (Get-Location) "adb.exe")
    } elseif (Test-Path (Join-Path $Root "adb.exe")) {
        $AdbPath = (Join-Path $Root "adb.exe")
    } else {
        $cmd = Get-Command adb -ErrorAction SilentlyContinue
        if ($null -eq $cmd) { throw "adb/adb.exe not found. Run this from your platform-tools directory, or pass -AdbPath C:\path\to\adb.exe" }
        $AdbPath = $cmd.Source
    }
}

function ADB {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
    & $AdbPath @Args
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE): adb $($Args -join ' ')" }
}

function Get-Prop([string]$Name) {
    return ((& $AdbPath shell getprop $Name) -join "`n").Trim()
}

function Write-Master([string]$Text) {
    $Text | Tee-Object -FilePath (Join-Path $Evidence "master-evidence.log") -Append
}

function Test-CloneAppInstrumentationIdle {
    foreach ($pkg in @("com.cloneapp.ca.test", "com.cloneapp.testapp.test")) {
        $savedErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $dump = & $AdbPath shell dumpsys activity $pkg 2>&1
        $status = $LASTEXITCODE
        $ErrorActionPreference = $savedErrorActionPreference
        if ($status -ne 0) {
            throw "Unable to inspect instrumentation state for $pkg (adb status $status)"
        }
        if ((($dump -join "`n") -match "(?m)^\s*Active instrumentation:\s*$")) {
            return $false
        }
    }
    return $true
}

function Wait-ForInstrumentationIdle([string]$Phase) {
    $deadline = (Get-Date).AddSeconds(15)
    do {
        if (Test-CloneAppInstrumentationIdle) {
            Write-Master "INSTRUMENTATION_IDLE_GUARD_ACCEPTED phase=$Phase"
            return
        }
        if ((Get-Date) -gt $deadline) {
            foreach ($pkg in @("com.cloneapp.ca.test", "com.cloneapp.testapp.test")) {
                & $AdbPath shell dumpsys activity $pkg |
                    Set-Content -Encoding utf8 (Join-Path $Evidence "instrumentation-active-$($pkg.Replace('.', '-'))-$Phase.txt")
            }
            throw "Instrumentation idle guard timed out during $Phase; refusing to start another single-method invocation"
        }
        Start-Sleep -Milliseconds 250
    } while ($true)
}

function Wait-ForCredentialUnlock {
    Write-Host "Waiting for Android user 0 credential unlock. If the phone is asking for its PIN after reboot, unlock it normally."
    Write-Master "USER_UNLOCK_GUARD_BEGIN=$(Get-Date -Format o)"
    $deadline = (Get-Date).AddMinutes(5)
    do {
        $savedErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $userDump = & $AdbPath shell dumpsys user 2>&1
        $status = $LASTEXITCODE
        $ErrorActionPreference = $savedErrorActionPreference
        if ($status -ne 0) {
            throw "Unable to query Android user state via dumpsys user (adb status $status): $($userDump -join ' ')"
        }

        $userText = ($userDump -join "`n")
        $unlocked = ($userText -match "(?m)^\s*Started users state:\s*\[[^\r\n\]]*\b0=RUNNING_UNLOCKED\b[^\r\n\]]*\]\s*$")
        if ($unlocked) {
            $userDump | Set-Content -Encoding utf8 (Join-Path $Evidence "user-unlock-state.txt")
            Write-Master "USER_UNLOCK_GUARD_ACCEPTED user=0 state=RUNNING_UNLOCKED source=dumpsys-user"
            return
        }

        if ((Get-Date) -gt $deadline) {
            $userDump | Set-Content -Encoding utf8 (Join-Path $Evidence "user-unlock-state-timeout.txt")
            throw "Android user 0 did not reach RUNNING_UNLOCKED within 5 minutes after boot; refusing to start post-reboot instrumentation"
        }
        Start-Sleep -Seconds 1
    } while ($true)
}

function Run-OneTest([string]$Num,[string]$Class,[string]$Method,[string]$Runner) {
    $name = "$Num-$Method"
    $file = Join-Path $Evidence "$name.txt"
    Write-Host "RUN $name"
    Write-Host ("RUNONETEST_ARGS Num=[{0}] Class=[{1}] Method=[{2}] Runner=[{3}]" -f $Num,$Class,$Method,$Runner)
    Wait-ForInstrumentationIdle "before-$name"
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = & $AdbPath shell am instrument -w -r -e class "$Class#$Method" $Runner 2>&1
    $ErrorActionPreference = $savedErrorActionPreference
    $status = $LASTEXITCODE
    $out | Set-Content -Encoding utf8 $file
    $out | ForEach-Object { Write-Host $_ }
    if ($status -ne 0) { throw "Instrumentation adb status $status for $Class#$Method" }
    $okCount = @($out | Where-Object { $_.ToString().Trim() -eq "OK (1 test)" }).Count
    if ($okCount -ne 1) {
        throw "Exact-count guard rejected ${Class}#${Method}: expected exactly one 'OK (1 test)', observed $okCount. Evidence: $file"
    }
    $guard = "GUARD_POSITIVE_CONTROL_ACCEPTED physical_file=$file expected=OK (1 test)"
    Add-Content -Encoding utf8 -Path $file -Value $guard
    Write-Master "TEST=$Class#$Method | OK (1 test) | $guard"
    Wait-ForInstrumentationIdle "after-$name"
}

Write-Master "ISSUE_55_PHYSICAL_BEGIN=$(Get-Date -Format o)"
Write-Master "SOURCE_RUN=$SourceRunId"
Write-Master "SOURCE_RUN_URL=https://github.com/monikarobinson19813-lgtm/CloneApp/actions/runs/$SourceRunId"
Write-Master "TESTED_CODE_SHA=$($TestedCodeSha.ToLowerInvariant())"
$harnessSha256 = (Get-FileHash -Algorithm SHA256 $MyInvocation.MyCommand.Path).Hash.ToLowerInvariant()
Write-Master "HARNESS_SHA256=$harnessSha256"

ADB wait-for-device
$serial = (& $AdbPath get-serialno).Trim()
$manufacturer = Get-Prop "ro.product.manufacturer"
$model = Get-Prop "ro.product.model"
$release = Get-Prop "ro.build.version.release"
$sdk = Get-Prop "ro.build.version.sdk"
$fingerprint = Get-Prop "ro.build.fingerprint"
$abi = Get-Prop "ro.product.cpu.abi"
$abilist = Get-Prop "ro.product.cpu.abilist"
$device = Get-Prop "ro.product.device"
$bootCompleted = Get-Prop "sys.boot_completed"

@"
serial=$serial
manufacturer=$manufacturer
model=$model
device=$device
android_release=$release
android_sdk=$sdk
build_fingerprint=$fingerprint
abi=$abi
abilist=$abilist
sys_boot_completed=$bootCompleted
"@ | Set-Content -Encoding utf8 (Join-Path $Evidence "device.txt")
Get-Content (Join-Path $Evidence "device.txt") | ForEach-Object { Write-Master $_ }

# Bind evidence to the exact four APK byte streams used in this physical run.
# SourceRunId/TestedCodeSha identify the GitHub build supplied by the operator; the hashes make
# those local bytes independently identifiable in the evidence bundle.
foreach ($name in @(
    "app-debug.apk",
    "app-debug-androidTest.apk",
    "testapp-debug.apk",
    "testapp-debug-androidTest.apk"
)) {
    $p = Join-Path $ApkDir $name
    if (-not (Test-Path $p)) { throw "Required APK missing: $p" }
    $actual = (Get-FileHash -Algorithm SHA256 $p).Hash.ToLowerInvariant()
    Write-Master "APK_SHA256 $name=$actual"
}

# Demonstrated lib/ listing from the actual APK files.
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($name in @("app-debug.apk","testapp-debug.apk")) {
    $p = Join-Path $ApkDir $name
    $zip = [System.IO.Compression.ZipFile]::OpenRead($p)
    try {
        $libs = @($zip.Entries | Where-Object { $_.FullName.StartsWith("lib/") } | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
    $libFile = Join-Path $Evidence "$name-lib-listing.txt"
    if ($libs.Count -eq 0) {
        "<EMPTY>" | Set-Content -Encoding utf8 $libFile
    } else {
        $libs | Set-Content -Encoding utf8 $libFile
    }
    Write-Master "APK_LIB_ENTRIES $name=$($libs.Count)"
}

# Clean only CloneApp/Test App packages unless explicitly skipped.
if (-not $SkipPackageReset) {
    foreach ($pkg in @("com.cloneapp.ca.test","com.cloneapp.testapp.test","com.cloneapp.ca","com.cloneapp.testapp")) {
        & $AdbPath uninstall $pkg 2>$null | Out-Null
    }
}

# Exact install order.
ADB install -r -t (Join-Path $ApkDir "app-debug.apk")
ADB install -r -t (Join-Path $ApkDir "app-debug-androidTest.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug-androidTest.apk")

# Negative exact-count proof.
$probeFile = Join-Path $Evidence "missing-method-probe.txt"
$probe = & $AdbPath shell am instrument -w -r -e class "com.cloneapp.testapp.StorageIsolationRuntimeTest#__missing_method_guard_probe__" "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner" 2>&1
$probeStatus = $LASTEXITCODE
$probe | Set-Content -Encoding utf8 $probeFile
$zeroCount = @($probe | Where-Object { $_.ToString().Trim() -eq "OK (0 tests)" }).Count
if ($probeStatus -ne 0 -or $zeroCount -ne 1) {
    throw "Negative probe did not produce explicit OK (0 tests) with adb success. status=$probeStatus zeroCount=$zeroCount"
}
Write-Master "HARNESS_MATCHED_GUARD_REASON=ZERO_TESTS guard_status=1 reason=expected exactly one executed passing test (OK (1 test)); observed OK (0 tests)"
Wait-ForInstrumentationIdle "after-negative-probe"

# Launch verification.
ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity
Start-Sleep -Seconds 2
$appPid = ((& $AdbPath shell pidof com.cloneapp.ca) -join "").Trim()
if ([string]::IsNullOrWhiteSpace($appPid)) { throw "CloneApp did not remain running after launch" }
Write-Master "CLONEAPP_LAUNCH_PID=$appPid"

$tests = @(
    ,@("01", "com.cloneapp.ca.ApkImportRuntimeTest", "importApkThroughDocumentPickerContract", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("02", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkRecordSurvivesRelaunch", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("03", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkPreservesOriginalPackageIdentityAndSigningCertificate", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("04", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataMatchesFixtureAndSurvivesRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("05", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataRepresentsEveryDeclaredComponentCategory", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("06", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataReportsDeclaredNativeAbiAndLibraryEntries", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("07", "com.cloneapp.ca.ApkImportRuntimeTest", "metadataParseFailureIsExplicitAndNonCrashing", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("08", "com.cloneapp.ca.ApkImportRuntimeTest", "invalidApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("09", "com.cloneapp.ca.ApkImportRuntimeTest", "unreadableApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("10", "com.cloneapp.ca.VirtualPackageRegistryRuntimeTest", "aliceAndBobShareBasePackageButKeepSeparateVirtualInstances", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("11", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("12", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "guestProcessCanRestartAfterDeathWithSameVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("13", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("14", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchDiagnosticsSurviveCaRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("15", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "aliceAndBobPrivateStorageAreIndependent", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("16", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "stateSurvivesRestartAndDeletingAliceLeavesBobIntact", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("17", "com.cloneapp.testapp.ProviderIsolationRuntimeTest", "aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("18", "com.cloneapp.ca.ProviderAuthorityRoutingRuntimeTest", "aliceAndBobUseDistinctVirtualAuthoritiesAndRouteToIsolatedPhysicalProviderState", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("19", "com.cloneapp.ca.NotificationTranslationRuntimeTest", "aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
)

# Tests 1-15 before reboot.
foreach ($t in $tests[0..14]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

# Independent physical reboot proof: command + boot_id + uptime + sys.boot_completed.
$bootIdBefore = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeBefore = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_COMMAND=adb reboot"
Write-Master "BOOT_ID_BEFORE=$bootIdBefore"
Write-Master "UPTIME_BEFORE=$uptimeBefore"
Write-Master "PHYSICAL_REBOOT_BEGIN=$(Get-Date -Format o)"

ADB reboot
ADB wait-for-device

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    $boot = ((& $AdbPath shell getprop sys.boot_completed 2>$null) -join "").Trim()
    if ((Get-Date) -gt $deadline) { throw "Phone did not report sys.boot_completed=1 within 5 minutes" }
} until ($boot -eq "1")

$bootIdAfter = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeAfter = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_BOOT_COMPLETED=$(Get-Date -Format o)"
Write-Master "SYS_BOOT_COMPLETED_AFTER=$boot"
Write-Master "BOOT_ID_AFTER=$bootIdAfter"
Write-Master "UPTIME_AFTER=$uptimeAfter"
if ($bootIdBefore -eq $bootIdAfter) { throw "Independent reboot proof failed: boot_id did not change" }
Write-Master "REBOOT_PROOF_ACCEPTED boot_id_changed=true sys.boot_completed=1"

# sys.boot_completed only proves framework boot. Credential-encrypted app data and instrumentation
# must not be exercised until Android reports user 0 as actually unlocked.
Wait-ForCredentialUnlock
Wait-ForInstrumentationIdle "post-reboot-before-test16"

# Tests 16-19 after reboot.
foreach ($t in $tests[15..18]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity

& $AdbPath shell dumpsys package com.cloneapp.ca | Set-Content -Encoding utf8 (Join-Path $Evidence "package-cloneapp.txt")
& $AdbPath shell dumpsys package com.cloneapp.testapp | Set-Content -Encoding utf8 (Join-Path $Evidence "package-testapp.txt")
& $AdbPath logcat -d | Set-Content -Encoding utf8 (Join-Path $Evidence "logcat.txt")

Write-Master "AUTOMATED_PHYSICAL_EVIDENCE_PASS"
Write-Master "MANUAL_D9_DEMO_STILL_REQUIRED=true"
Write-Master "ISSUE_55_PHYSICAL_AUTOMATION_END=$(Get-Date -Format o)"

Write-Host ""
Write-Host "AUTOMATED #55 EVIDENCE PASS."
Write-Host "Evidence folder: $Evidence"
Write-Host ""
Write-Host "Now perform the manual D9 demo in CloneApp:"
Write-Host "1. Import CA Test App APK."
Write-Host "2. Create Alice and Bob."
Write-Host "3. Launch Alice; save Alice/10."
Write-Host "4. Launch Bob; confirm Alice value did not leak; save Bob/50."
Write-Host "5. Close/reopen; confirm Alice=10 and Bob=50."
Write-Host "6. Reboot the phone once more if manual-state reboot persistence is required, then reconfirm both."
Write-Host "7. Delete Alice; confirm Bob remains intact."
)]
    [string]$SourceRunId,
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^[0-9a-fA-F]{40}

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkDir = Join-Path $Root "apks"
$EvidenceRoot = Join-Path $Root "evidence"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Evidence = Join-Path $EvidenceRoot $Stamp
New-Item -ItemType Directory -Force -Path $Evidence | Out-Null

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    if (Test-Path (Join-Path (Get-Location) "adb.exe")) {
        $AdbPath = (Join-Path (Get-Location) "adb.exe")
    } elseif (Test-Path (Join-Path $Root "adb.exe")) {
        $AdbPath = (Join-Path $Root "adb.exe")
    } else {
        $cmd = Get-Command adb -ErrorAction SilentlyContinue
        if ($null -eq $cmd) { throw "adb/adb.exe not found. Run this from your platform-tools directory, or pass -AdbPath C:\path\to\adb.exe" }
        $AdbPath = $cmd.Source
    }
}

function ADB {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
    & $AdbPath @Args
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE): adb $($Args -join ' ')" }
}

function Get-Prop([string]$Name) {
    return ((& $AdbPath shell getprop $Name) -join "`n").Trim()
}

function Write-Master([string]$Text) {
    $Text | Tee-Object -FilePath (Join-Path $Evidence "master-evidence.log") -Append
}

function Run-OneTest([string]$Num,[string]$Class,[string]$Method,[string]$Runner) {
    $name = "$Num-$Method"
    $file = Join-Path $Evidence "$name.txt"
    Write-Host "RUN $name"
    Write-Host ("RUNONETEST_ARGS Num=[{0}] Class=[{1}] Method=[{2}] Runner=[{3}]" -f $Num,$Class,$Method,$Runner)
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = & $AdbPath shell am instrument -w -r -e class "$Class#$Method" $Runner 2>&1
    $ErrorActionPreference = $savedErrorActionPreference
    $status = $LASTEXITCODE
    $out | Set-Content -Encoding utf8 $file
    $out | ForEach-Object { Write-Host $_ }
    if ($status -ne 0) { throw "Instrumentation adb status $status for $Class#$Method" }
    $okCount = @($out | Where-Object { $_.ToString().Trim() -eq "OK (1 test)" }).Count
    if ($okCount -ne 1) {
        throw "Exact-count guard rejected ${Class}#${Method}: expected exactly one 'OK (1 test)', observed $okCount. Evidence: $file"
    }
    $guard = "GUARD_POSITIVE_CONTROL_ACCEPTED physical_file=$file expected=OK (1 test)"
    Add-Content -Encoding utf8 -Path $file -Value $guard
    Write-Master "TEST=$Class#$Method | OK (1 test) | $guard"
}

Write-Master "ISSUE_55_PHYSICAL_BEGIN=$(Get-Date -Format o)"
Write-Master "SOURCE_RUN=35809965282"
Write-Master "SOURCE_RUN_URL=https://github.com/monikarobinson19813-lgtm/CloneApp/actions/runs/35809965282"
Write-Master "TESTED_CODE_SHA=9d024b7b1fb37469cc95a7c8ef23d29ad31c0c4d"

ADB wait-for-device
$serial = (& $AdbPath get-serialno).Trim()
$manufacturer = Get-Prop "ro.product.manufacturer"
$model = Get-Prop "ro.product.model"
$release = Get-Prop "ro.build.version.release"
$sdk = Get-Prop "ro.build.version.sdk"
$fingerprint = Get-Prop "ro.build.fingerprint"
$abi = Get-Prop "ro.product.cpu.abi"
$abilist = Get-Prop "ro.product.cpu.abilist"
$device = Get-Prop "ro.product.device"
$bootCompleted = Get-Prop "sys.boot_completed"

@"
serial=$serial
manufacturer=$manufacturer
model=$model
device=$device
android_release=$release
android_sdk=$sdk
build_fingerprint=$fingerprint
abi=$abi
abilist=$abilist
sys_boot_completed=$bootCompleted
"@ | Set-Content -Encoding utf8 (Join-Path $Evidence "device.txt")
Get-Content (Join-Path $Evidence "device.txt") | ForEach-Object { Write-Master $_ }

# Bind the exact APK bytes.
$expected = @{
    "app-debug.apk" = "2c0fdd03eacb2b3afb08135b642a497399c8608916bd3c0438fe7b2389b3b080"
    "testapp-debug.apk" = "124efd0ecc2da4469e70e485070209b1aaba42aa6d14377e977d1a4d30c4cffa"
}
foreach ($name in $expected.Keys) {
    $p = Join-Path $ApkDir $name
    $actual = (Get-FileHash -Algorithm SHA256 $p).Hash.ToLowerInvariant()
    Write-Master "APK_SHA256 $name=$actual"
    if ($actual -ne $expected[$name]) { throw "APK hash mismatch for $name" }
}

# Demonstrated lib/ listing from the actual APK files.
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($name in @("app-debug.apk","testapp-debug.apk")) {
    $p = Join-Path $ApkDir $name
    $zip = [System.IO.Compression.ZipFile]::OpenRead($p)
    try {
        $libs = @($zip.Entries | Where-Object { $_.FullName.StartsWith("lib/") } | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
    $libFile = Join-Path $Evidence "$name-lib-listing.txt"
    if ($libs.Count -eq 0) {
        "<EMPTY>" | Set-Content -Encoding utf8 $libFile
    } else {
        $libs | Set-Content -Encoding utf8 $libFile
    }
    Write-Master "APK_LIB_ENTRIES $name=$($libs.Count)"
}

# Clean only CloneApp/Test App packages unless explicitly skipped.
if (-not $SkipPackageReset) {
    foreach ($pkg in @("com.cloneapp.ca.test","com.cloneapp.testapp.test","com.cloneapp.ca","com.cloneapp.testapp")) {
        & $AdbPath uninstall $pkg 2>$null | Out-Null
    }
}

# Exact install order.
ADB install -r -t (Join-Path $ApkDir "app-debug.apk")
ADB install -r -t (Join-Path $ApkDir "app-debug-androidTest.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug-androidTest.apk")

# Negative exact-count proof.
$probeFile = Join-Path $Evidence "missing-method-probe.txt"
$probe = & $AdbPath shell am instrument -w -r -e class "com.cloneapp.testapp.StorageIsolationRuntimeTest#__missing_method_guard_probe__" "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner" 2>&1
$probeStatus = $LASTEXITCODE
$probe | Set-Content -Encoding utf8 $probeFile
$zeroCount = @($probe | Where-Object { $_.ToString().Trim() -eq "OK (0 tests)" }).Count
if ($probeStatus -ne 0 -or $zeroCount -ne 1) {
    throw "Negative probe did not produce explicit OK (0 tests) with adb success. status=$probeStatus zeroCount=$zeroCount"
}
Write-Master "HARNESS_MATCHED_GUARD_REASON=ZERO_TESTS guard_status=1 reason=expected exactly one executed passing test (OK (1 test)); observed OK (0 tests)"

# Launch verification.
ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity
Start-Sleep -Seconds 2
$appPid = ((& $AdbPath shell pidof com.cloneapp.ca) -join "").Trim()
if ([string]::IsNullOrWhiteSpace($appPid)) { throw "CloneApp did not remain running after launch" }
Write-Master "CLONEAPP_LAUNCH_PID=$appPid"

$tests = @(
    ,@("01", "com.cloneapp.ca.ApkImportRuntimeTest", "importApkThroughDocumentPickerContract", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("02", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkRecordSurvivesRelaunch", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("03", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkPreservesOriginalPackageIdentityAndSigningCertificate", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("04", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataMatchesFixtureAndSurvivesRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("05", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataRepresentsEveryDeclaredComponentCategory", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("06", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataReportsDeclaredNativeAbiAndLibraryEntries", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("07", "com.cloneapp.ca.ApkImportRuntimeTest", "metadataParseFailureIsExplicitAndNonCrashing", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("08", "com.cloneapp.ca.ApkImportRuntimeTest", "invalidApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("09", "com.cloneapp.ca.ApkImportRuntimeTest", "unreadableApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("10", "com.cloneapp.ca.VirtualPackageRegistryRuntimeTest", "aliceAndBobShareBasePackageButKeepSeparateVirtualInstances", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("11", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("12", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "guestProcessCanRestartAfterDeathWithSameVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("13", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("14", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchDiagnosticsSurviveCaRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("15", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "aliceAndBobPrivateStorageAreIndependent", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("16", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "stateSurvivesRestartAndDeletingAliceLeavesBobIntact", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("17", "com.cloneapp.testapp.ProviderIsolationRuntimeTest", "aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("18", "com.cloneapp.ca.ProviderAuthorityRoutingRuntimeTest", "aliceAndBobUseDistinctVirtualAuthoritiesAndRouteToIsolatedPhysicalProviderState", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("19", "com.cloneapp.ca.NotificationTranslationRuntimeTest", "aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
)

# Tests 1-15 before reboot.
foreach ($t in $tests[0..14]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

# Independent physical reboot proof: command + boot_id + uptime + sys.boot_completed.
$bootIdBefore = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeBefore = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_COMMAND=adb reboot"
Write-Master "BOOT_ID_BEFORE=$bootIdBefore"
Write-Master "UPTIME_BEFORE=$uptimeBefore"
Write-Master "PHYSICAL_REBOOT_BEGIN=$(Get-Date -Format o)"

ADB reboot
ADB wait-for-device

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    $boot = ((& $AdbPath shell getprop sys.boot_completed 2>$null) -join "").Trim()
    if ((Get-Date) -gt $deadline) { throw "Phone did not report sys.boot_completed=1 within 5 minutes" }
} until ($boot -eq "1")

$bootIdAfter = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeAfter = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_BOOT_COMPLETED=$(Get-Date -Format o)"
Write-Master "SYS_BOOT_COMPLETED_AFTER=$boot"
Write-Master "BOOT_ID_AFTER=$bootIdAfter"
Write-Master "UPTIME_AFTER=$uptimeAfter"
if ($bootIdBefore -eq $bootIdAfter) { throw "Independent reboot proof failed: boot_id did not change" }
Write-Master "REBOOT_PROOF_ACCEPTED boot_id_changed=true sys.boot_completed=1"

# Tests 16-19 after reboot.
foreach ($t in $tests[15..18]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity

& $AdbPath shell dumpsys package com.cloneapp.ca | Set-Content -Encoding utf8 (Join-Path $Evidence "package-cloneapp.txt")
& $AdbPath shell dumpsys package com.cloneapp.testapp | Set-Content -Encoding utf8 (Join-Path $Evidence "package-testapp.txt")
& $AdbPath logcat -d | Set-Content -Encoding utf8 (Join-Path $Evidence "logcat.txt")

Write-Master "AUTOMATED_PHYSICAL_EVIDENCE_PASS"
Write-Master "MANUAL_D9_DEMO_STILL_REQUIRED=true"
Write-Master "ISSUE_55_PHYSICAL_AUTOMATION_END=$(Get-Date -Format o)"

Write-Host ""
Write-Host "AUTOMATED #55 EVIDENCE PASS."
Write-Host "Evidence folder: $Evidence"
Write-Host ""
Write-Host "Now perform the manual D9 demo in CloneApp:"
Write-Host "1. Import CA Test App APK."
Write-Host "2. Create Alice and Bob."
Write-Host "3. Launch Alice; save Alice/10."
Write-Host "4. Launch Bob; confirm Alice value did not leak; save Bob/50."
Write-Host "5. Close/reopen; confirm Alice=10 and Bob=50."
Write-Host "6. Reboot the phone once more if manual-state reboot persistence is required, then reconfirm both."
Write-Host "7. Delete Alice; confirm Bob remains intact."
)]
    [string]$TestedCodeSha
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkDir = Join-Path $Root "apks"
$EvidenceRoot = Join-Path $Root "evidence"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Evidence = Join-Path $EvidenceRoot $Stamp
New-Item -ItemType Directory -Force -Path $Evidence | Out-Null

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    if (Test-Path (Join-Path (Get-Location) "adb.exe")) {
        $AdbPath = (Join-Path (Get-Location) "adb.exe")
    } elseif (Test-Path (Join-Path $Root "adb.exe")) {
        $AdbPath = (Join-Path $Root "adb.exe")
    } else {
        $cmd = Get-Command adb -ErrorAction SilentlyContinue
        if ($null -eq $cmd) { throw "adb/adb.exe not found. Run this from your platform-tools directory, or pass -AdbPath C:\path\to\adb.exe" }
        $AdbPath = $cmd.Source
    }
}

function ADB {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
    & $AdbPath @Args
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE): adb $($Args -join ' ')" }
}

function Get-Prop([string]$Name) {
    return ((& $AdbPath shell getprop $Name) -join "`n").Trim()
}

function Write-Master([string]$Text) {
    $Text | Tee-Object -FilePath (Join-Path $Evidence "master-evidence.log") -Append
}

function Run-OneTest([string]$Num,[string]$Class,[string]$Method,[string]$Runner) {
    $name = "$Num-$Method"
    $file = Join-Path $Evidence "$name.txt"
    Write-Host "RUN $name"
    Write-Host ("RUNONETEST_ARGS Num=[{0}] Class=[{1}] Method=[{2}] Runner=[{3}]" -f $Num,$Class,$Method,$Runner)
    $savedErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $out = & $AdbPath shell am instrument -w -r -e class "$Class#$Method" $Runner 2>&1
    $ErrorActionPreference = $savedErrorActionPreference
    $status = $LASTEXITCODE
    $out | Set-Content -Encoding utf8 $file
    $out | ForEach-Object { Write-Host $_ }
    if ($status -ne 0) { throw "Instrumentation adb status $status for $Class#$Method" }
    $okCount = @($out | Where-Object { $_.ToString().Trim() -eq "OK (1 test)" }).Count
    if ($okCount -ne 1) {
        throw "Exact-count guard rejected ${Class}#${Method}: expected exactly one 'OK (1 test)', observed $okCount. Evidence: $file"
    }
    $guard = "GUARD_POSITIVE_CONTROL_ACCEPTED physical_file=$file expected=OK (1 test)"
    Add-Content -Encoding utf8 -Path $file -Value $guard
    Write-Master "TEST=$Class#$Method | OK (1 test) | $guard"
}

Write-Master "ISSUE_55_PHYSICAL_BEGIN=$(Get-Date -Format o)"
Write-Master "SOURCE_RUN=35809965282"
Write-Master "SOURCE_RUN_URL=https://github.com/monikarobinson19813-lgtm/CloneApp/actions/runs/35809965282"
Write-Master "TESTED_CODE_SHA=9d024b7b1fb37469cc95a7c8ef23d29ad31c0c4d"

ADB wait-for-device
$serial = (& $AdbPath get-serialno).Trim()
$manufacturer = Get-Prop "ro.product.manufacturer"
$model = Get-Prop "ro.product.model"
$release = Get-Prop "ro.build.version.release"
$sdk = Get-Prop "ro.build.version.sdk"
$fingerprint = Get-Prop "ro.build.fingerprint"
$abi = Get-Prop "ro.product.cpu.abi"
$abilist = Get-Prop "ro.product.cpu.abilist"
$device = Get-Prop "ro.product.device"
$bootCompleted = Get-Prop "sys.boot_completed"

@"
serial=$serial
manufacturer=$manufacturer
model=$model
device=$device
android_release=$release
android_sdk=$sdk
build_fingerprint=$fingerprint
abi=$abi
abilist=$abilist
sys_boot_completed=$bootCompleted
"@ | Set-Content -Encoding utf8 (Join-Path $Evidence "device.txt")
Get-Content (Join-Path $Evidence "device.txt") | ForEach-Object { Write-Master $_ }

# Bind the exact APK bytes.
$expected = @{
    "app-debug.apk" = "2c0fdd03eacb2b3afb08135b642a497399c8608916bd3c0438fe7b2389b3b080"
    "testapp-debug.apk" = "124efd0ecc2da4469e70e485070209b1aaba42aa6d14377e977d1a4d30c4cffa"
}
foreach ($name in $expected.Keys) {
    $p = Join-Path $ApkDir $name
    $actual = (Get-FileHash -Algorithm SHA256 $p).Hash.ToLowerInvariant()
    Write-Master "APK_SHA256 $name=$actual"
    if ($actual -ne $expected[$name]) { throw "APK hash mismatch for $name" }
}

# Demonstrated lib/ listing from the actual APK files.
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($name in @("app-debug.apk","testapp-debug.apk")) {
    $p = Join-Path $ApkDir $name
    $zip = [System.IO.Compression.ZipFile]::OpenRead($p)
    try {
        $libs = @($zip.Entries | Where-Object { $_.FullName.StartsWith("lib/") } | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
    $libFile = Join-Path $Evidence "$name-lib-listing.txt"
    if ($libs.Count -eq 0) {
        "<EMPTY>" | Set-Content -Encoding utf8 $libFile
    } else {
        $libs | Set-Content -Encoding utf8 $libFile
    }
    Write-Master "APK_LIB_ENTRIES $name=$($libs.Count)"
}

# Clean only CloneApp/Test App packages unless explicitly skipped.
if (-not $SkipPackageReset) {
    foreach ($pkg in @("com.cloneapp.ca.test","com.cloneapp.testapp.test","com.cloneapp.ca","com.cloneapp.testapp")) {
        & $AdbPath uninstall $pkg 2>$null | Out-Null
    }
}

# Exact install order.
ADB install -r -t (Join-Path $ApkDir "app-debug.apk")
ADB install -r -t (Join-Path $ApkDir "app-debug-androidTest.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug.apk")
ADB install -r -t (Join-Path $ApkDir "testapp-debug-androidTest.apk")

# Negative exact-count proof.
$probeFile = Join-Path $Evidence "missing-method-probe.txt"
$probe = & $AdbPath shell am instrument -w -r -e class "com.cloneapp.testapp.StorageIsolationRuntimeTest#__missing_method_guard_probe__" "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner" 2>&1
$probeStatus = $LASTEXITCODE
$probe | Set-Content -Encoding utf8 $probeFile
$zeroCount = @($probe | Where-Object { $_.ToString().Trim() -eq "OK (0 tests)" }).Count
if ($probeStatus -ne 0 -or $zeroCount -ne 1) {
    throw "Negative probe did not produce explicit OK (0 tests) with adb success. status=$probeStatus zeroCount=$zeroCount"
}
Write-Master "HARNESS_MATCHED_GUARD_REASON=ZERO_TESTS guard_status=1 reason=expected exactly one executed passing test (OK (1 test)); observed OK (0 tests)"

# Launch verification.
ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity
Start-Sleep -Seconds 2
$appPid = ((& $AdbPath shell pidof com.cloneapp.ca) -join "").Trim()
if ([string]::IsNullOrWhiteSpace($appPid)) { throw "CloneApp did not remain running after launch" }
Write-Master "CLONEAPP_LAUNCH_PID=$appPid"

$tests = @(
    ,@("01", "com.cloneapp.ca.ApkImportRuntimeTest", "importApkThroughDocumentPickerContract", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("02", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkRecordSurvivesRelaunch", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("03", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkPreservesOriginalPackageIdentityAndSigningCertificate", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("04", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataMatchesFixtureAndSurvivesRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("05", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataRepresentsEveryDeclaredComponentCategory", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("06", "com.cloneapp.ca.ApkImportRuntimeTest", "importedApkMetadataReportsDeclaredNativeAbiAndLibraryEntries", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("07", "com.cloneapp.ca.ApkImportRuntimeTest", "metadataParseFailureIsExplicitAndNonCrashing", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("08", "com.cloneapp.ca.ApkImportRuntimeTest", "invalidApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("09", "com.cloneapp.ca.ApkImportRuntimeTest", "unreadableApkShowsVisibleErrorWithoutCrash", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("10", "com.cloneapp.ca.VirtualPackageRegistryRuntimeTest", "aliceAndBobShareBasePackageButKeepSeparateVirtualInstances", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("11", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "aliceAndBobReceiveDistinctVirtualIdentityAndDeathIsBookkept", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("12", "com.cloneapp.ca.GuestProcessHostRuntimeTest", "guestProcessCanRestartAfterDeathWithSameVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("13", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchSameImportedGuestWithDistinctVirtualIdentity", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("14", "com.cloneapp.ca.GuestActivityLaunchRuntimeTest", "aliceAndBobLaunchDiagnosticsSurviveCaRestart", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("15", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "aliceAndBobPrivateStorageAreIndependent", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("16", "com.cloneapp.testapp.StorageIsolationRuntimeTest", "stateSurvivesRestartAndDeletingAliceLeavesBobIntact", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("17", "com.cloneapp.testapp.ProviderIsolationRuntimeTest", "aliceAndBobProviderStateAreIndependentAndDeleteDoesNotCrossUsers", "com.cloneapp.testapp.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("18", "com.cloneapp.ca.ProviderAuthorityRoutingRuntimeTest", "aliceAndBobUseDistinctVirtualAuthoritiesAndRouteToIsolatedPhysicalProviderState", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
    ,@("19", "com.cloneapp.ca.NotificationTranslationRuntimeTest", "aliceAndBobPostIndependentlyWithVisibleInstanceIdentityAndLifecycle", "com.cloneapp.ca.test/androidx.test.runner.AndroidJUnitRunner")
)

# Tests 1-15 before reboot.
foreach ($t in $tests[0..14]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

# Independent physical reboot proof: command + boot_id + uptime + sys.boot_completed.
$bootIdBefore = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeBefore = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_COMMAND=adb reboot"
Write-Master "BOOT_ID_BEFORE=$bootIdBefore"
Write-Master "UPTIME_BEFORE=$uptimeBefore"
Write-Master "PHYSICAL_REBOOT_BEGIN=$(Get-Date -Format o)"

ADB reboot
ADB wait-for-device

$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 2
    $boot = ((& $AdbPath shell getprop sys.boot_completed 2>$null) -join "").Trim()
    if ((Get-Date) -gt $deadline) { throw "Phone did not report sys.boot_completed=1 within 5 minutes" }
} until ($boot -eq "1")

$bootIdAfter = ((& $AdbPath shell cat /proc/sys/kernel/random/boot_id) -join "").Trim()
$uptimeAfter = ((& $AdbPath shell cat /proc/uptime) -join "").Trim()
Write-Master "PHYSICAL_REBOOT_BOOT_COMPLETED=$(Get-Date -Format o)"
Write-Master "SYS_BOOT_COMPLETED_AFTER=$boot"
Write-Master "BOOT_ID_AFTER=$bootIdAfter"
Write-Master "UPTIME_AFTER=$uptimeAfter"
if ($bootIdBefore -eq $bootIdAfter) { throw "Independent reboot proof failed: boot_id did not change" }
Write-Master "REBOOT_PROOF_ACCEPTED boot_id_changed=true sys.boot_completed=1"

# Tests 16-19 after reboot.
foreach ($t in $tests[15..18]) { Run-OneTest $t[0] $t[1] $t[2] $t[3] }

ADB shell am force-stop com.cloneapp.ca
& $AdbPath shell am start -W -n com.cloneapp.ca/.MainActivity

& $AdbPath shell dumpsys package com.cloneapp.ca | Set-Content -Encoding utf8 (Join-Path $Evidence "package-cloneapp.txt")
& $AdbPath shell dumpsys package com.cloneapp.testapp | Set-Content -Encoding utf8 (Join-Path $Evidence "package-testapp.txt")
& $AdbPath logcat -d | Set-Content -Encoding utf8 (Join-Path $Evidence "logcat.txt")

Write-Master "AUTOMATED_PHYSICAL_EVIDENCE_PASS"
Write-Master "MANUAL_D9_DEMO_STILL_REQUIRED=true"
Write-Master "ISSUE_55_PHYSICAL_AUTOMATION_END=$(Get-Date -Format o)"

Write-Host ""
Write-Host "AUTOMATED #55 EVIDENCE PASS."
Write-Host "Evidence folder: $Evidence"
Write-Host ""
Write-Host "Now perform the manual D9 demo in CloneApp:"
Write-Host "1. Import CA Test App APK."
Write-Host "2. Create Alice and Bob."
Write-Host "3. Launch Alice; save Alice/10."
Write-Host "4. Launch Bob; confirm Alice value did not leak; save Bob/50."
Write-Host "5. Close/reopen; confirm Alice=10 and Bob=50."
Write-Host "6. Reboot the phone once more if manual-state reboot persistence is required, then reconfirm both."
Write-Host "7. Delete Alice; confirm Bob remains intact."
