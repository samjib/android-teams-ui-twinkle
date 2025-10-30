#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build script for Screen Keeper Android app - works on Windows and Linux

.DESCRIPTION
    This script builds the Screen Keeper Android application using Gradle.
    It supports both debug and release builds and works cross-platform on
    Windows, Linux, and macOS with PowerShell Core.

.PARAMETER BuildType
    Type of build to perform: 'debug' or 'release' (default: 'debug')

.PARAMETER Clean
    Clean build output before building

.PARAMETER Install
    Install the built APK to a connected Android device

.PARAMETER Lint
    Run lint checks

.PARAMETER Test
    Run unit tests

.PARAMETER Help
    Display this help message

.EXAMPLE
    ./build.ps1
    Builds debug APK

.EXAMPLE
    ./build.ps1 -BuildType release
    Builds release APK

.EXAMPLE
    ./build.ps1 -Clean -BuildType debug -Install
    Clean, build debug APK, and install to connected device

.EXAMPLE
    ./build.ps1 -Lint -Test
    Run lint checks and tests

.NOTES
    Requirements:
    - JDK 17 or higher
    - Android SDK (if using -Install)
    - PowerShell 7+ (for cross-platform support)
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory=$false)]
    [ValidateSet('debug', 'release')]
    [string]$BuildType = 'debug',

    [Parameter(Mandatory=$false)]
    [switch]$Clean,

    [Parameter(Mandatory=$false)]
    [switch]$Install,

    [Parameter(Mandatory=$false)]
    [switch]$Lint,

    [Parameter(Mandatory=$false)]
    [switch]$Test,

    [Parameter(Mandatory=$false)]
    [switch]$Help
)

# Display help if requested
if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

# Colors for output (cross-platform compatible)
function Write-ColorOutput {
    param(
        [string]$Message,
        [string]$Color = 'White'
    )
    
    $colorMap = @{
        'Green' = [System.ConsoleColor]::Green
        'Yellow' = [System.ConsoleColor]::Yellow
        'Red' = [System.ConsoleColor]::Red
        'Cyan' = [System.ConsoleColor]::Cyan
        'White' = [System.ConsoleColor]::White
    }
    
    $originalColor = $Host.UI.RawUI.ForegroundColor
    $Host.UI.RawUI.ForegroundColor = $colorMap[$Color]
    Write-Host $Message
    $Host.UI.RawUI.ForegroundColor = $originalColor
}

function Write-Header {
    param([string]$Message)
    Write-ColorOutput "`n========================================" 'Cyan'
    Write-ColorOutput $Message 'Cyan'
    Write-ColorOutput "========================================`n" 'Cyan'
}

function Write-Success {
    param([string]$Message)
    Write-ColorOutput "✓ $Message" 'Green'
}

function Write-Error-Custom {
    param([string]$Message)
    Write-ColorOutput "✗ $Message" 'Red'
}

function Write-Info {
    param([string]$Message)
    Write-ColorOutput "ℹ $Message" 'Yellow'
}

$gradlewScript = if ($isWindows) { ".\gradlew.bat" } else { "./gradlew" }

Write-Header "Screen Keeper Build Script"
Write-Info "Platform: $(if ($isWindows) { 'Windows' } else { 'Linux/macOS' })"
Write-Info "Build Type: $BuildType"
Write-Info "Gradle Wrapper: $gradlewScript"

# Check if gradlew exists
if (-not (Test-Path $gradlewScript)) {
    Write-Error-Custom "Gradle wrapper not found: $gradlewScript"
    Write-Info "Please ensure you're running this script from the project root directory."
    exit 1
}

# Make gradlew executable on Unix systems
if (-not $isWindows) {
    Write-Info "Setting execute permissions on gradlew..."
    chmod +x gradlew
}

# Function to run gradle command
function Invoke-GradleCommand {
    param(
        [string]$Command,
        [string]$Description
    )
    
    Write-Header $Description
    
    $arguments = $Command -split ' '
    
    if ($isWindows) {
        $allArgs = @("/c", $gradlewScript) + $arguments
        $process = Start-Process -FilePath "cmd.exe" -ArgumentList $allArgs -NoNewWindow -Wait -PassThru
        $exitCode = $process.ExitCode
    } else {
        $process = Start-Process -FilePath $gradlewScript -ArgumentList $arguments -NoNewWindow -Wait -PassThru
        $exitCode = $process.ExitCode
    }
    
    if ($exitCode -eq 0) {
        Write-Success "$Description completed successfully"
        return $true
    } else {
        Write-Error-Custom "$Description failed with exit code: $exitCode"
        return $false
    }
}

# Track overall success
$overallSuccess = $true

# Clean build if requested
if ($Clean) {
    if (-not (Invoke-GradleCommand "clean" "Cleaning build output")) {
        $overallSuccess = $false
    }
}

# Run lint if requested
if ($Lint) {
    if (-not (Invoke-GradleCommand "lint" "Running lint checks")) {
        $overallSuccess = $false
    }
}

# Run tests if requested
if ($Test) {
    if (-not (Invoke-GradleCommand "test" "Running unit tests")) {
        $overallSuccess = $false
    }
}

# Build the APK
$buildTask = if ($BuildType -eq 'release') { 'assembleRelease' } else { 'assembleDebug' }
$buildDescription = "Building $BuildType APK"

if (-not (Invoke-GradleCommand $buildTask $buildDescription)) {
    $overallSuccess = $false
}

# Install if requested and build was successful
if ($Install -and $overallSuccess) {
    $installTask = if ($BuildType -eq 'release') { 'installRelease' } else { 'installDebug' }
    
    # Check for connected devices
    Write-Info "Checking for connected Android devices..."
    
    $adbPath = $null
    if ($env:ANDROID_HOME) {
        $adbPath = Join-Path $env:ANDROID_HOME "platform-tools/adb$(if ($isWindows) {'.exe'} else {''})"
    }
    
    if ($adbPath -and (Test-Path $adbPath)) {
        $devices = & $adbPath devices
        if ($devices -match "device$") {
            Write-Success "Connected device found"
            if (-not (Invoke-GradleCommand $installTask "Installing $BuildType APK to device")) {
                $overallSuccess = $false
            }
        } else {
            Write-Error-Custom "No Android devices connected"
            Write-Info "Connect a device via USB and enable USB debugging"
            $overallSuccess = $false
        }
    } else {
        Write-Error-Custom "ADB not found. Please set ANDROID_HOME environment variable"
        $overallSuccess = $false
    }
}

# Display build output location
if ($overallSuccess) {
    Write-Header "Build Summary"
    Write-Success "Build completed successfully!"
    
    $apkPath = if ($BuildType -eq 'release') {
        "app/build/outputs/apk/release/app-release-unsigned.apk"
    } else {
        "app/build/outputs/apk/debug/app-debug.apk"
    }
    
    if (Test-Path $apkPath) {
        $apkSize = (Get-Item $apkPath).Length / 1MB
        Write-Info "APK Location: $apkPath"
        Write-Info "APK Size: $([math]::Round($apkSize, 2)) MB"
        
        # Show absolute path
        $absolutePath = Resolve-Path $apkPath
        Write-Info "Absolute Path: $absolutePath"
    } else {
        Write-Info "Expected APK at: $apkPath (may need to check build output)"
    }
    
    # Show next steps
    Write-Header "Next Steps"
    if ($BuildType -eq 'debug') {
        Write-Info "To install on device: ./build.ps1 -BuildType debug -Install"
        Write-Info "Or manually: adb install $apkPath"
    } else {
        Write-Info "Release APK needs to be signed before installation"
        Write-Info "Debug signing: keytool and apksigner required"
    }
} else {
    Write-Header "Build Summary"
    Write-Error-Custom "Build failed! Check the output above for errors."
    exit 1
}

Write-ColorOutput "`n========================================`n" 'Cyan'
exit 0
