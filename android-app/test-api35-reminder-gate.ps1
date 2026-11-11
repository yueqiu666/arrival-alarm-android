#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial,
    [string]$GradleExecutable = (Join-Path $PSScriptRoot 'gradlew.bat'),
    [switch]$Offline,
    [switch]$Recovery,
    [switch]$Permissions,
    [switch]$Revocation,
    [switch]$All
)

$ErrorActionPreference = 'Stop'
if (@($Recovery, $Permissions, $Revocation, $All).Where({ $_ }).Count -gt 1) {
    throw 'Select one gate mode, or use -All to run all modes'
}
$sdk = (Resolve-Path -LiteralPath $SdkRoot).Path
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$aapt = Join-Path $sdk 'build-tools/36.0.0/aapt.exe'
$package = 'com.navobell.arrivalalarm.debug.p08gate'
$runner = "$package.test/com.example.arrivalalarm.LifecycleInstrumentationRunner"
$evidence = Join-Path ([IO.Path]::GetTempPath()) ("arrival-api35-gate-" + [guid]::NewGuid().ToString('N'))
$build = Join-Path $evidence 'build'
New-Item -ItemType Directory -Path $evidence | Out-Null
Write-Host "Evidence: $evidence"

function Invoke-Device {
    & $adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed (exit $LASTEXITCODE)" }
}

$apiLevel = [int](Invoke-Device shell getprop ro.build.version.sdk)
if ($apiLevel -notin (31..36) -or
    !(Invoke-Device shell getprop ro.boot.qemu.avd_name)) {
    throw 'The gate requires a running API 31–36 emulator'
}
if ((Invoke-Device shell cmd location is-location-enabled) -ne 'true') {
    throw 'Enable Location on the test emulator before running the gate'
}
# Android 12 images do not emit the foreground-service start/stop event tags.
# On them the device assertions still check foreground state and service removal.
$checkForegroundEvents = $apiLevel -ge 33
# Android 12's package manager cannot set or clear shell permission flags.
$canSetPermissionFlags = $apiLevel -ge 33

$previousEnvironment = @{}
foreach ($entry in @{
    ANDROID_HOME = $sdk
    ANDROID_SDK_ROOT = $sdk
    ARRIVAL_BUILD_DIR = $build
    ARRIVAL_AMAP_WEB_KEY = $null
}.GetEnumerator()) {
    $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}
Push-Location $PSScriptRoot
try {
    $tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleDebugAndroidTest',
        '-ParrivalApi35ReminderGate=true', '--no-daemon', '--console=plain')
    if ($Offline) { $tasks += '--offline' }
    & $GradleExecutable @tasks
    if ($LASTEXITCODE -ne 0) { throw "Gate build failed (exit $LASTEXITCODE)" }
} finally {
    Pop-Location
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

$appApk = Join-Path $build 'outputs/apk/debug/arrival-alarm-android-debug.apk'
$testApk = Join-Path $build 'outputs/apk/androidTest/debug/arrival-alarm-android-debug-androidTest.apk'
foreach ($artifact in @(@($appApk, $package), @($testApk, "$package.test"))) {
    $actual = & $aapt dump badging $artifact[0]
    $expected = [regex]::Escape($artifact[1])
    if ($LASTEXITCODE -ne 0 -or !($actual -match "^package: name='$expected'")) {
        throw 'Refusing to install an APK without the isolated gate package name'
    }
}
Get-FileHash -LiteralPath @($appApk, $testApk) -Algorithm SHA256 |
    ForEach-Object { "$($_.Hash)  $([IO.Path]::GetFileName($_.Path))" } |
    Set-Content -LiteralPath (Join-Path $evidence 'apk-sha256.txt') -Encoding utf8
Invoke-Device install -r $appApk
Invoke-Device install -r $testApk
$corePermissions = @('ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION')
if ($apiLevel -ge 33) { $corePermissions += 'POST_NOTIFICATIONS' }
function Grant-CorePermissions {
    foreach ($permission in $corePermissions) {
        Invoke-Device shell pm grant $package "android.permission.$permission"
    }
}

function Reset-GateFixture {
    if ((Invoke-Device shell pm clear $package) -ne 'Success') { throw 'Could not reset the isolated fixture app' }
    Grant-CorePermissions
}
Reset-GateFixture
"api=$apiLevel serial=$Serial avd=$(Invoke-Device shell getprop ro.boot.qemu.avd_name)" |
    Set-Content -LiteralPath (Join-Path $evidence 'device.log') -Encoding utf8
function Get-DeviceTime {
    [decimal]::Parse((Invoke-Device shell date +%s.%N), [Globalization.CultureInfo]::InvariantCulture)
}

function Get-GateEvents([decimal]$Since, [string]$LogName) {
    $tags = @('am_foreground_service_start:I', 'am_foreground_service_stop:I', 'am_crash:I', 'am_anr:I')
    $events = @(Invoke-Device logcat -b events -v epoch -d -s @tags | Where-Object {
        $_.Contains($package) -and $_ -match '^\s*(?<time>\d+\.\d+)' -and
            [decimal]::Parse($Matches.time, [Globalization.CultureInfo]::InvariantCulture) -ge $Since
    })
    [IO.File]::WriteAllLines((Join-Path $evidence "$LogName.log"), [string[]]$events)
    $events
}

function Start-GateInstrumentation([string]$Mode, [string]$LogName, [string[]]$Extras = @()) {
    $invocation = [Diagnostics.ProcessStartInfo]::new($adb)
    $invocation.UseShellExecute = $false
    $invocation.CreateNoWindow = $true
    $invocation.RedirectStandardOutput = $true
    $invocation.RedirectStandardError = $true
    foreach ($argument in @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-e', 'api35ReminderGate', $Mode) + $Extras + @($runner)) {
        $invocation.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::Start($invocation)
    [pscustomobject]@{
        Process = $process
        Output = $process.StandardOutput.ReadToEndAsync()
        Errors = $process.StandardError.ReadToEndAsync()
        LogName = $LogName
    }
}

function Save-GateInstrumentation($Session) {
    if (!$Session.Process.HasExited) { $Session.Process.Kill(); $Session.Process.WaitForExit() }
    $Session.Output.Result | Set-Content -LiteralPath (Join-Path $evidence "$($Session.LogName).log") -Encoding utf8
    $Session.Errors.Result | Set-Content -LiteralPath (Join-Path $evidence "$($Session.LogName)-stderr.log") -Encoding utf8
}

function Set-GateLocation {
    # Use a GPS fix far enough from the fixture to stay in monitoring.
    $fix = Invoke-Device emu geo fix -122.084 37.4219
    if ($fix -notcontains 'OK') { throw 'Emulator rejected the GPS fixture' }
}

function Invoke-GateInstrumentation([string]$Mode, [string]$LogName, [string[]]$Extras = @()) {
    $session = Start-GateInstrumentation $Mode $LogName $Extras
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        while (!$session.Process.HasExited -and $timer.Elapsed.TotalSeconds -lt 100) {
            if ($Mode -notin @('permission-denied', 'revocation-verify')) { Set-GateLocation }
            $null = $session.Process.WaitForExit(900)
        }
        if (!$session.Process.HasExited) { throw 'Instrumentation did not return within 100 seconds' }
    } finally {
        Save-GateInstrumentation $session
    }
    $output = $session.Output.Result.Trim()
    Write-Host $output
    if ($session.Process.ExitCode -ne 0 -or $output -notmatch '(?m)^INSTRUMENTATION_CODE: -1\s*$') {
        throw "Instrumentation did not report success. See $LogName.log"
    }
    $output
}

function Get-GateProcessId {
    $processId = & $adb -s $Serial shell pidof $package 2>$null
    if ($LASTEXITCODE -notin @(0, 1)) { throw 'Could not query the isolated process' }
    "$processId".Trim()
}

function Wait-GateBoot([string]$PreviousBootId) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt 240) {
        $state = & $adb -s $Serial get-state 2>$null
        if ($LASTEXITCODE -eq 0 -and $state -eq 'device') {
            $bootId = & $adb -s $Serial shell cat /proc/sys/kernel/random/boot_id 2>$null
            $bootCompleted = & $adb -s $Serial shell getprop sys.boot_completed 2>$null
            $unlocked = & $adb -s $Serial shell getprop sys.user.0.ce_available 2>$null
            if ($bootId -and $bootId -ne $PreviousBootId -and $bootCompleted -eq '1' -and $unlocked -eq 'true') {
                return $bootId
            }
        }
        Start-Sleep -Seconds 1
    }
    throw 'Emulator did not finish a new boot within 240 seconds'
}

function Test-Recovery([string]$Interruption, [string]$Permission = '', [string]$Case = '') {
    $runId = [guid]::NewGuid().ToString('N')
    $startedAt = Get-DeviceTime
    $seed = Start-GateInstrumentation 'recovery-seed' "$Interruption-seed" @('-e', 'recoveryRun', $runId)
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $ready = $null
        while (!$seed.Process.HasExited -and $timer.Elapsed.TotalSeconds -lt 25) {
            Set-GateLocation
            $pattern = "RECOVERY_READY $runId (?<processId>\d+) (?<taskId>task-(?:\d+|[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}))$"
            $ready = Invoke-Device logcat -d -s 'ArrivalApi35Gate:I' |
                Select-String -Pattern $pattern | Select-Object -Last 1
            if ($ready) { break }
            $null = $seed.Process.WaitForExit(400)
        }
        if (!$ready) { throw "Live recovery fixture did not become ready. See $Interruption-seed.log" }
        $oldProcessId = $ready.Matches[0].Groups['processId'].Value
        $taskId = $ready.Matches[0].Groups['taskId'].Value
        if ((Get-GateProcessId) -ne $oldProcessId) { throw 'The ready process was not alive at interruption' }
        [xml]$saved = (Invoke-Device shell run-as $package cat shared_prefs/arrival_alarm_private.xml) -join "`n"
        $task = ($saved.map.string | Where-Object name -eq 'task').'#text' | ConvertFrom-Json
        if ($task.id -ne $taskId -or $task.status -ne 'active') { throw 'The active fixture was not persisted before interruption' }

        $oldBootId = Invoke-Device shell cat /proc/sys/kernel/random/boot_id
        $seedEvents = @(Get-GateEvents $startedAt "$Interruption-before")
        if (($checkForegroundEvents -and @($seedEvents -match '\bam_foreground_service_start:.*TrackingService').Count -ne 1) -or
            @($seedEvents -match '\bam_(crash|anr):').Count) {
            throw 'The seeded service did not start normally'
        }
        $interruptedAt = Get-DeviceTime
        if ($Interruption -eq 'process-kill') {
            # Ask the live fixture to send itself SIGKILL without changing package stopped state.
            Invoke-Device shell run-as $package touch "files/gate-kill-$runId"
        } elseif ($Interruption -eq 'reboot') {
            Invoke-Device reboot | Out-Host
        } else {
            Invoke-Device shell pm revoke $package $Permission
        }
        if (!$seed.Process.WaitForExit(15000)) { throw 'The original instrumentation survived the interruption' }
    } finally {
        Save-GateInstrumentation $seed
    }

    if ($Interruption -eq 'reboot') {
        $bootId = Wait-GateBoot $oldBootId
        Invoke-Device shell wm dismiss-keyguard | Out-Host
    } else {
        $bootId = Invoke-Device shell cat /proc/sys/kernel/random/boot_id
        if ($bootId -ne $oldBootId) { throw "Unexpected reboot during $Interruption" }
    }
    Start-Sleep -Seconds 2
    $services = @(Invoke-Device shell dumpsys activity services $package)
    $interruptionEvents = @(Get-GateEvents $interruptedAt "$Interruption-interrupted")
    if ((Get-GateProcessId) -or ($services -match 'ServiceRecord.*TrackingService') -or
        ($interruptionEvents -match '\bam_(foreground_service_start|crash|anr):')) {
        throw 'The isolated app or service restarted, crashed or hung before reopening'
    }
    "interruption=$Interruption old_pid=$oldProcessId old_boot=$oldBootId boot=$bootId task=$taskId before_reopen=process_and_service_absent" |
        Set-Content -LiteralPath (Join-Path $evidence "$Interruption-interruption.log") -Encoding utf8

    if ($Permission) {
        Test-RevokedRestore $Interruption $Permission $Case $taskId
        return
    }
    $reopenedAt = Get-DeviceTime
    try {
        $output = Invoke-GateInstrumentation 'recovery-verify' "$Interruption-recovery" @('-e', 'taskId', $taskId)
    } finally {
        $events = @(Get-GateEvents $reopenedAt "$Interruption-events")
    }
    $newProcess = [regex]::Match($output, '(?m)^INSTRUMENTATION_RESULT: recovery_pid=(\d+)\s*$')
    $unexpectedForegroundEvents = $checkForegroundEvents -and (
        @($events -match '\bam_foreground_service_start:.*TrackingService').Count -ne 1 -or
        @($events -match '\bam_foreground_service_stop:.*TrackingService').Count -ne 1)
    if (!$newProcess.Success -or ($Interruption -eq 'process-kill' -and $newProcess.Groups[1].Value -eq $oldProcessId) -or
        !$output.Contains('INSTRUMENTATION_RESULT: recovered=same task, manual restore, foreground service and location ready') -or
        $unexpectedForegroundEvents -or
        @($events -match '\bam_(crash|anr):').Count) {
        throw "Recovery or crash check failed for $Interruption"
    }
    Write-Host "$Interruption recovery: PASS"
}

function Test-RevokedRestore([string]$Interruption, [string]$Permission, [string]$Case, [string]$TaskId) {
    if ($canSetPermissionFlags) {
        Invoke-Device shell pm set-permission-flags $package $Permission user-set user-fixed
    }
    $startedAt = Get-DeviceTime
    try {
        $output = Invoke-GateInstrumentation 'revocation-verify' "$Interruption-restore" @('-e', 'taskId', $TaskId, '-e', 'permissionCase', $Case)
    } finally {
        $events = @(Get-GateEvents $startedAt "$Interruption-events")
    }
    if (!$output.Contains('INSTRUMENTATION_RESULT: revoked=same task needs restore, permission hint shown, service absent') -or
        ($events -match '\bam_(foreground_service_start|crash|anr):')) {
        throw "Permission revocation check failed for $Case"
    }
    Write-Host "$Case revocation while monitoring: PASS"
}

function Test-PermissionDenied([string]$Case, [string[]]$PermissionsToRevoke, [switch]$FirstPrompt) {
    $logName = "permission-$Case"
    try {
        foreach ($permission in $PermissionsToRevoke) {
            Invoke-Device shell pm revoke $package $permission
            if ($canSetPermissionFlags) {
                if ($FirstPrompt) {
                    Invoke-Device shell pm clear-permission-flags $package $permission user-set user-fixed
                } else {
                    Invoke-Device shell pm set-permission-flags $package $permission user-set user-fixed
                }
            }
        }
        $startedAt = Get-DeviceTime
        try {
            $output = Invoke-GateInstrumentation 'permission-denied' $logName @('-e', 'permissionCase', $Case)
        } finally {
            $events = @(Get-GateEvents $startedAt "$logName-events")
        }
        if (!$output.Contains("INSTRUMENTATION_RESULT: permission_case=$Case") -or
            !$output.Contains('INSTRUMENTATION_RESULT: permission_blocked=specific hint shown, task absent, service absent, active card hidden') -or
            ($events -match '\bam_(foreground_service_start|crash|anr):')) {
            throw "Permission denial check failed for $Case"
        }
        Write-Host "$Case permission denial: PASS"
    } finally {
        if ($canSetPermissionFlags) {
            foreach ($permission in $PermissionsToRevoke) {
                Invoke-Device shell pm clear-permission-flags $package $permission user-set user-fixed
            }
        }
        Grant-CorePermissions
    }
}

function Test-StartStop {
    $startedAt = Get-DeviceTime
    try {
        $output = Invoke-GateInstrumentation 'true' 'instrumentation'
    } finally {
        $events = @(Get-GateEvents $startedAt 'service-events')
    }
    $crashes = @($events -match '\bam_(crash|anr):').Count
    $starts = @($events -match '\bam_foreground_service_start:.*TrackingService').Count
    $stops = @($events -match '\bam_foreground_service_stop:.*TrackingService').Count
    if ($checkForegroundEvents) {
        Write-Host "Android events: $starts foreground starts, $stops foreground exits, $crashes crash/ANR events"
    } else {
        Write-Host "Android events: $crashes crash/ANR events (foreground event counts unavailable on API $apiLevel)"
    }
    if ($crashes -ne 0 -or ($checkForegroundEvents -and ($starts -ne 25 -or $stops -ne 25))) {
        throw 'Unexpected Android service events. See service-events.log'
    }
    foreach ($expected in @('normal_start_stop=1', 'immediate_stop=12', 'immediate_clear=12',
        'settled=service absent, task absent, active card hidden')) {
        if (!$output.Contains("INSTRUMENTATION_RESULT: $expected")) { throw "Missing instrumentation result: $expected" }
    }
    Write-Host "API $apiLevel reminder start/stop gate: PASS"
}

$failures = [Collections.Generic.List[string]]::new()
function Invoke-GateCheck([string]$Name, [scriptblock]$Action) {
    try {
        if ($failures.Count) { Reset-GateFixture }
        & $Action
    } catch {
        if (!$All) { throw }
        $failures.Add($Name)
        Write-Host "$Name FAILED: $($_.Exception.Message)"
    }
}

try {
    if ($All -or !($Recovery -or $Permissions -or $Revocation)) {
        Invoke-GateCheck 'start/stop' { Test-StartStop }
    }
    if ($Permissions -or $All) {
        $before = $failures.Count
        if ($apiLevel -ge 33) {
            Invoke-GateCheck 'notification denial' {
                Test-PermissionDenied 'notification' 'android.permission.POST_NOTIFICATIONS'
            }
            Invoke-GateCheck 'first notification denial' {
                Test-PermissionDenied 'notification-first' 'android.permission.POST_NOTIFICATIONS' -FirstPrompt
            }
        }
        if ($canSetPermissionFlags) {
            Invoke-GateCheck 'approximate-only denial' {
                Test-PermissionDenied 'approximate-only' 'android.permission.ACCESS_FINE_LOCATION'
            }
        }
        Invoke-GateCheck 'first approximate-only choice' {
            Test-PermissionDenied 'approximate-only-first' @('android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION') -FirstPrompt
        }
        if ($failures.Count -eq $before) { Write-Host "API $apiLevel reminder permission gate: PASS" }
    }
    if ($Revocation -or $All) {
        $before = $failures.Count
        $revocations = [ordered]@{'approximate-only' = 'android.permission.ACCESS_FINE_LOCATION'}
        if ($apiLevel -ge 33) { $revocations['notification'] = 'android.permission.POST_NOTIFICATIONS' }
        foreach ($case in $revocations.Keys) {
            $permission = $revocations[$case]
            Invoke-GateCheck "revoked-$case" {
                try {
                    Test-Recovery "revoked-$case" $permission $case
                } finally {
                    Invoke-Device shell am force-stop $package
                    if ($canSetPermissionFlags) {
                        Invoke-Device shell pm clear-permission-flags $package $permission user-set user-fixed
                    }
                    Reset-GateFixture
                }
            }
        }
        if ($failures.Count -eq $before) { Write-Host "API $apiLevel reminder revocation gate: PASS" }
    }
    if ($Recovery -or $All) {
        $before = $failures.Count
        Invoke-GateCheck 'process-kill recovery' { Test-Recovery 'process-kill' }
        Invoke-GateCheck 'reboot recovery' { Test-Recovery 'reboot' }
        if ($failures.Count -eq $before) { Write-Host "API $apiLevel reminder recovery gate: PASS" }
    }
    if ($failures.Count) { throw "API $apiLevel gate failures: $($failures -join ', '). See $evidence" }
} finally {
    $state = & $adb -s $Serial get-state 2>$null
    if ($LASTEXITCODE -eq 0 -and $state -eq 'device') { Invoke-Device shell am force-stop $package }
}
