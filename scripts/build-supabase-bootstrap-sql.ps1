param(
    [string]$MigrationPath = "docs/sql/migration_db.sql",
    [switch]$CopyToClipboard
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$resolvedMigrationPath = Join-Path $repoRoot $MigrationPath
if (-not (Test-Path $resolvedMigrationPath)) {
    throw "Missing canonical migration: $MigrationPath"
}

$fullText = Get-Content -Path $resolvedMigrationPath -Raw

$accountDeletionPath = Join-Path $repoRoot "docs/sql/phase-account-deletion.sql"
if (Test-Path $accountDeletionPath) {
    $fullText = $fullText.TrimEnd() + "`r`n`r`n" + (Get-Content -Path $accountDeletionPath -Raw)
}

$activitySyncPath = Join-Path $repoRoot "docs/sql/phase-activity-sync.sql"
if (Test-Path $activitySyncPath) {
    $fullText = $fullText.TrimEnd() + "`r`n`r`n" + (Get-Content -Path $activitySyncPath -Raw)
}

if ($CopyToClipboard) {
    Set-Clipboard -Value $fullText
    Write-Host "Copied to clipboard: $MigrationPath + docs/sql/phase-account-deletion.sql + docs/sql/phase-activity-sync.sql"
} else {
    Write-Host "Canonical migration: $MigrationPath (+ docs/sql/phase-account-deletion.sql + docs/sql/phase-activity-sync.sql when present)"
}

Write-Host ""
Write-Host "Next step:"
Write-Host "1) Open docs/sql/migration_db.sql then docs/sql/phase-account-deletion.sql and docs/sql/phase-activity-sync.sql (or paste from clipboard)."
Write-Host "2) Paste into Supabase SQL Editor."
Write-Host "3) Run it once on a fresh database."
Write-Host "   (Optional FCM pg_net triggers are included; they no-op until app.settings are set — see docs/fcm-setup.md.)"
