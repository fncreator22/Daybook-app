<#
    Daybook - build an installable debug APK on Windows.

    Called by build-apk.bat in the project root. Run that instead of this.

    What it does, in order:
      1. finds a JDK 17 or newer
      2. finds an Android SDK and writes local.properties to point at it
      3. finds Gradle 8.13, downloading and checksum-verifying it if needed
      4. runs `gradle assembleDebug`
      5. copies the APK to the project root and tells you where it is

    Nothing here needs administrator rights, and nothing is installed outside
    your user profile.
#>

[CmdletBinding()]
param(
    # Build the release variant instead. Only useful once keystore.properties
    # exists; without it the output is unsigned and will not install.
    [switch] $Release,

    # Skip the download of Gradle and fail instead, for offline machines that
    # already have `gradle` on PATH.
    [switch] $NoDownload
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$GradleVersion = '8.13'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsDir = Join-Path $env:USERPROFILE '.gradle\daybook-tools'

function Write-Step($text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Write-Ok($text)   { Write-Host "    $text" -ForegroundColor Green }
function Write-Warn($text)  { Write-Host "    $text" -ForegroundColor Yellow }

function Fail($text, $code = 1) {
    Write-Host "`nCannot continue: $text" -ForegroundColor Red
    exit $code
}

# ---------------------------------------------------------------------------
# 1. JDK
# ---------------------------------------------------------------------------

function Get-JavaMajorVersion($javaExe) {
    # -XshowSettings prints to stderr, hence the redirect and the 2>&1 merge.
    $lines = & $javaExe '-XshowSettings:properties' '-version' 2>&1
    $line = $lines | Where-Object { $_ -match 'java\.version\s*=' } | Select-Object -First 1
    if (-not $line) { return 0 }
    $value = ($line -split '=', 2)[1].Trim()
    # "17.0.11" -> 17, and the historic "1.8.0_402" -> 8.
    if ($value -match '^1\.(\d+)') { return [int]$Matches[1] }
    if ($value -match '^(\d+)')    { return [int]$Matches[1] }
    return 0
}

Write-Step 'Looking for a JDK 17 or newer'

$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $onPath = Get-Command java -ErrorAction SilentlyContinue
    if ($onPath) { $javaExe = $onPath.Source }
}

if (-not $javaExe) {
    Fail @'
no Java was found.

Install a JDK 17 (or newer) and try again. Either of these works:
  * Android Studio, which bundles one - https://developer.android.com/studio
  * Eclipse Temurin 17 - https://adoptium.net/temurin/releases/?version=17

If a JDK is already installed, set JAVA_HOME to it and reopen your terminal.
'@ 2
}

$javaMajor = Get-JavaMajorVersion $javaExe
if ($javaMajor -lt 17) {
    Fail "Java $javaMajor was found at $javaExe, but this project needs JDK 17 or newer. Set JAVA_HOME to a JDK 17+ installation." 2
}
Write-Ok "JDK $javaMajor at $javaExe"

# ---------------------------------------------------------------------------
# 2. Android SDK
# ---------------------------------------------------------------------------

Write-Step 'Looking for an Android SDK'

$sdkCandidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
    'C:\Android\Sdk'
) | Where-Object { $_ } | Select-Object -Unique

$sdkDir = $sdkCandidates | Where-Object { Test-Path (Join-Path $_ 'platform-tools') -PathType Container } |
    Select-Object -First 1
if (-not $sdkDir) {
    # platform-tools is the usual marker, but a fresh command-line-tools install
    # has not created it yet, so fall back to any directory that looks like an SDK.
    $sdkDir = $sdkCandidates | Where-Object { Test-Path $_ -PathType Container } | Select-Object -First 1
}

if (-not $sdkDir) {
    Fail @'
no Android SDK was found.

The quickest fix is to install Android Studio once - it downloads the SDK for
you, and after that this script works:
  https://developer.android.com/studio

If you already have an SDK somewhere unusual, set ANDROID_HOME to it and
reopen your terminal.

If you would rather install nothing at all, use the GitHub Actions route in
INSTALL.md - it builds the APK in the cloud and you just download it.
'@ 3
}
Write-Ok "SDK at $sdkDir"

# Gradle reads sdk.dir from local.properties. Backslashes are escape characters
# in a .properties file, so they are doubled here.
$escapedSdk = $sdkDir -replace '\\', '\\\\'
$localProperties = Join-Path $ProjectRoot 'local.properties'
@(
    '# Written by build-apk.bat. Machine-specific, never committed.',
    "sdk.dir=$escapedSdk"
) | Set-Content -Path $localProperties -Encoding ASCII
Write-Ok 'wrote local.properties'

# ---------------------------------------------------------------------------
# 3. Gradle
# ---------------------------------------------------------------------------

function Install-Gradle($version, $destination) {
    $baseUrl = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
    $zipPath = Join-Path $destination "gradle-$version-bin.zip"

    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    # TLS 1.2 is not the default on stock PowerShell 5.1 and services.gradle.org
    # requires it.
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

    Write-Warn "downloading Gradle $version (about 130 MB, one time only)"
    Invoke-WebRequest -Uri $baseUrl -OutFile $zipPath -UseBasicParsing

    Write-Warn 'verifying the download'
    $expected = (Invoke-WebRequest -Uri "$baseUrl.sha256" -UseBasicParsing).Content.Trim()
    $actual = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash
    if ($actual -ne $expected.ToUpperInvariant()) {
        Remove-Item $zipPath -Force
        Fail "the Gradle download did not match its published SHA-256. Expected $expected, got $actual. Nothing was installed." 4
    }
    Write-Ok 'checksum matches'

    Expand-Archive -Path $zipPath -DestinationPath $destination -Force
    Remove-Item $zipPath -Force
}

Write-Step "Looking for Gradle $GradleVersion"

$gradleExe = $null
$onPath = Get-Command gradle -ErrorAction SilentlyContinue
if ($onPath) {
    $gradleExe = $onPath.Source
    Write-Ok "using the gradle already on your PATH ($gradleExe)"
} else {
    $localGradle = Join-Path $ToolsDir "gradle-$GradleVersion\bin\gradle.bat"
    if (-not (Test-Path $localGradle)) {
        if ($NoDownload) {
            Fail "Gradle is not on PATH and -NoDownload was given." 4
        }
        Install-Gradle $GradleVersion $ToolsDir
    }
    if (-not (Test-Path $localGradle)) {
        Fail "Gradle was downloaded but $localGradle is missing. Delete $ToolsDir and try again." 4
    }
    $gradleExe = $localGradle
    Write-Ok "using $gradleExe"
}

# ---------------------------------------------------------------------------
# 4. Build
# ---------------------------------------------------------------------------

$task = if ($Release) { 'assembleRelease' } else { 'assembleDebug' }
$variant = if ($Release) { 'release' } else { 'debug' }

Write-Step "Running gradle $task"
Write-Warn 'the first run downloads the Android build tools and takes a few minutes'

Push-Location $ProjectRoot
try {
    & $gradleExe $task '--no-daemon' '--stacktrace'
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($gradleExit -ne 0) {
    Fail "gradle $task failed with exit code $gradleExit. The output above says why." $gradleExit
}

# ---------------------------------------------------------------------------
# 5. Hand over the APK
# ---------------------------------------------------------------------------

Write-Step 'Collecting the APK'

$outputDir = Join-Path $ProjectRoot "app\build\outputs\apk\$variant"
$apk = Get-ChildItem -Path $outputDir -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1

if (-not $apk) {
    Fail "gradle reported success but no APK was found in $outputDir." 5
}

$handover = Join-Path $ProjectRoot "Daybook-$variant.apk"
Copy-Item -Path $apk.FullName -Destination $handover -Force

$sizeMb = [math]::Round($apk.Length / 1MB, 1)
Write-Host ''
Write-Host '  Built successfully.' -ForegroundColor Green
Write-Host "  APK:  $handover  ($sizeMb MB)"
Write-Host ''
Write-Host '  To get it onto your phone:'
Write-Host '    1. copy the file to the phone over USB, or upload it to your'
Write-Host '       own cloud drive and download it there'
Write-Host '    2. open it on the phone and allow "install unknown apps" when asked'
Write-Host ''
if (-not $Release) {
    Write-Host '  This is a debug build. It installs as "Daybook (debug)" alongside'
    Write-Host '  any future Play Store copy, and it is signed with the standard'
    Write-Host '  Android debug key, which is fine for your own testing.'
}

exit 0


