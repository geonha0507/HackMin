#requires -Version 5.1
<#
.SYNOPSIS
    해킹커넥트(rider_app) 릴리즈 APK를 빌드 → 서명 → LDPlayer에 설치(옵션: 자동 실행)까지 한 번에 처리한다.

.DESCRIPTION
    rider_app 의 release 빌드는 signingConfig 가 없어 assembleRelease 결과물이
    app-release-unsigned.apk(미서명)다. 미서명 APK 는 adb 로 설치되지 않으므로
    이 스크립트가 zipalign → apksigner(디버그 키) 서명까지 해서 설치 가능한
    app-release.apk 를 만든 뒤 LDPlayer 로 install -r 한다.

    빌드는 Android Studio 의 JBR(Java 21)로 강제한다. 머신 기본 JAVA_HOME 이
    Java 25면 JdkImageTransform 이 실패하기 때문이다.

.PARAMETER Port
    LDPlayer 인스턴스의 adb 포트. 1번 인스턴스는 보통 5555, 이후 5557, 5559 ...

.PARAMETER Serial
    설치 대상 adb 시리얼을 직접 지정한다(예: emulator-5554). 주면 connect 를 건너뛴다.

.PARAMETER Launch
    설치 후 앱(LoginActivity)을 자동 실행한다.

.PARAMETER SkipBuild
    빌드를 건너뛰고 마지막에 만든 APK 를 서명·설치만 다시 한다(빠른 재설치용).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File rider_app\deploy_ldplayer.ps1 -Launch
#>
[CmdletBinding()]
param(
    [int]$Port = 5555,
    [string]$Serial,
    [switch]$Launch,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$AppId = 'com.hackmin.connect'
$LauncherComponent = "$AppId/.ui.auth.LoginActivity"
$AppDir = $PSScriptRoot                       # ...\rider_app
$ReleaseDir = Join-Path $AppDir 'app\build\outputs\apk\release'

function Info($m) { Write-Host "[deploy] $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "[deploy] $m" -ForegroundColor Green }
function Die($m)  { Write-Host "[deploy] $m" -ForegroundColor Red; exit 1 }

# ── 1) Java 21 (Android Studio JBR) 확정 ──────────────────────────────────
function Resolve-Jdk21 {
    $candidates = @(
        "$env:JAVA_HOME_21",
        "C:\Program Files\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "C:\Program Files\Eclipse Adoptium\jdk-21*"
    )
    foreach ($c in $candidates) {
        if (-not $c) { continue }
        $hit = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($hit -and (Test-Path (Join-Path $hit.FullName 'bin\java.exe'))) {
            $releaseFile = Join-Path $hit.FullName 'release'
            if (Test-Path $releaseFile) {
                if (Select-String -Path $releaseFile -Pattern 'JAVA_VERSION="21' -Quiet) {
                    return $hit.FullName
                }
            } else {
                return $hit.FullName
            }
        }
    }
    return $null
}

# ── 2) Android SDK / build-tools 확정 ─────────────────────────────────────
function Resolve-Sdk {
    foreach ($p in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")) {
        if ($p -and (Test-Path $p)) { return $p }
    }
    return $null
}
function Resolve-BuildTools($sdk) {
    $bt = Join-Path $sdk 'build-tools'
    if (-not (Test-Path $bt)) { return $null }
    Get-ChildItem $bt -Directory |
        Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

# ── 3) adb 확정 (LDPlayer 자체 adb 우선) ──────────────────────────────────
function Resolve-Adb($sdk) {
    $candidates = @(
        'C:\LDPlayer\LDPlayer9\adb.exe',
        'D:\LDPlayer\LDPlayer9\adb.exe',
        'C:\LDPlayer\LDPlayer4.0\adb.exe'
    )
    if ($sdk) { $candidates += (Join-Path $sdk 'platform-tools\adb.exe') }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    return $null
}

$jdk = Resolve-Jdk21
if (-not $jdk) { Die 'Java 21(JBR)을 찾지 못했습니다. Android Studio 설치 경로를 확인하세요.' }
$sdk = Resolve-Sdk
if (-not $sdk) { Die 'Android SDK 를 찾지 못했습니다. ANDROID_HOME 을 설정하세요.' }
$buildTools = Resolve-BuildTools $sdk
if (-not $buildTools) { Die "build-tools 를 찾지 못했습니다: $sdk\build-tools" }
$adb = Resolve-Adb $sdk
if (-not $adb) { Die 'adb 를 찾지 못했습니다(LDPlayer 또는 platform-tools).' }

$zipalign  = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$keystore  = Join-Path $env:USERPROFILE '.android\debug.keystore'
if (-not (Test-Path $keystore)) {
    Die "디버그 키스토어가 없습니다: $keystore  (Android Studio 로 앱을 한 번 빌드하면 생성됩니다)"
}

Info "JDK21   : $jdk"
Info "SDK     : $sdk"
Info "b-tools : $buildTools"
Info "adb     : $adb"

# ── 4) 릴리즈 빌드 (Java 21 강제) ─────────────────────────────────────────
if (-not $SkipBuild) {
    Info "릴리즈 빌드 시작 ..."
    $env:JAVA_HOME = $jdk
    $gradlew = Join-Path $AppDir 'gradlew.bat'

    # Gradle 9 는 toolchain 자동탐지로 VS Code 번들 JRE(jlink 없음)를 골라
    # JdkImageTransform 을 깨뜨린다. 자동탐지를 끄고 JBR(=JAVA_HOME)만 쓰게 강제한다.
    & $gradlew --stop | Out-Null
    & $gradlew -p $AppDir 'assembleRelease' `
        '--no-configuration-cache' `
        '-Dorg.gradle.java.installations.auto-detect=false' `
        "-Dorg.gradle.java.home=$jdk"
    if ($LASTEXITCODE -ne 0) { Die "gradle 빌드 실패 (exit $LASTEXITCODE)" }
    Ok '빌드 완료'
}

# ── 5) 서명 대상 APK 찾기 ─────────────────────────────────────────────────
$unsigned = Join-Path $ReleaseDir 'app-release-unsigned.apk'
$prebuilt = Join-Path $ReleaseDir 'app-release.apk'
if (Test-Path $unsigned) {
    $srcApk = $unsigned
} elseif (Test-Path $prebuilt) {
    $srcApk = $prebuilt
} else {
    Die "릴리즈 APK 를 찾지 못했습니다: $ReleaseDir (먼저 빌드가 필요할 수 있습니다)"
}

# ── 6) zipalign → apksigner(디버그 키) 서명 ───────────────────────────────
$aligned = Join-Path $ReleaseDir 'app-release-aligned.apk'
$signed  = Join-Path $ReleaseDir 'app-release.apk'

Info "zipalign ..."
& $zipalign -p -f 4 $srcApk $aligned
if ($LASTEXITCODE -ne 0) { Die 'zipalign 실패' }

Info "apksigner 서명(디버그 키) ..."
$env:JAVA_HOME = $jdk   # apksigner.bat 도 JAVA_HOME 사용
& $apksigner sign `
    --ks $keystore `
    --ks-pass pass:android `
    --key-pass pass:android `
    --ks-key-alias androiddebugkey `
    --out $signed `
    $aligned
if ($LASTEXITCODE -ne 0) { Die 'apksigner 서명 실패' }
Remove-Item $aligned -ErrorAction SilentlyContinue
Ok "서명 완료: $signed"

# ── 7) LDPlayer 연결 & 설치 ───────────────────────────────────────────────
if (-not $Serial) {
    Info "adb connect 127.0.0.1:$Port ..."
    & $adb connect "127.0.0.1:$Port" | Out-Null
    $Serial = "127.0.0.1:$Port"
}

$devs = & $adb devices | Select-String -Pattern "$([regex]::Escape($Serial))\s+device"
if (-not $devs) {
    & $adb devices | Write-Host
    Die "LDPlayer($Serial)에 연결되지 않았습니다. LDPlayer 실행 여부와 '로컬 연결(ADB 디버깅)' 설정을 확인하세요. (-Serial 로 직접 지정 가능)"
}

Info "설치(install -r) → $Serial ..."
& $adb -s $Serial install -r -d $signed
if ($LASTEXITCODE -ne 0) { Die 'adb install 실패' }
Ok "설치 완료: $AppId"

# ── 8) 자동 실행(옵션) ────────────────────────────────────────────────────
if ($Launch) {
    Info "앱 실행 ..."
    & $adb -s $Serial shell am start -n $LauncherComponent | Out-Null
    Ok '실행 요청 완료'
}

Ok 'DONE'
