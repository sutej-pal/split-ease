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

if ($CopyToClipboard) {
    Set-Clipboard -Value $fullText
    Write-Host "Copied to clipboard: $MigrationPath"
} else {
    Write-Host "Canonical migration: $MigrationPath"
}

Write-Host ""
Write-Host "Next step:"
Write-Host "1) Open docs/sql/migration_db.sql (or paste from clipboard)."
Write-Host "2) Paste into Supabase SQL Editor."
Write-Host "3) Run it once on a fresh database."
Write-Host "   (Optional FCM pg_net triggers are included; they no-op until app.settings are set — see docs/fcm-setup.md.)"
