# Create the next SplitEase Android release (bumps versionCode + versionName).
#
# Usage:
#   .\scripts\new-release.ps1
#   .\scripts\new-release.ps1 -Bump minor
#   .\scripts\new-release.ps1 -Bump major -Notes "First public Play track"
#
# Forwards to Gradle task `newRelease`. Run from the Android repo root (app\).

param(
    [ValidateSet('patch', 'minor', 'major')]
    [string]$Bump = 'patch',
    [string]$Notes = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$gradlew = Join-Path $repoRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat not found at $repoRoot"
}

$gradleArgs = @('newRelease', "-Pbump=$Bump")
if ($Notes.Trim().Length -gt 0) {
    $gradleArgs += "-Pnotes=$Notes"
}

& $gradlew @gradleArgs
if ($LASTEXITCODE -ne 0) {
    throw "newRelease failed with exit code $LASTEXITCODE"
}
