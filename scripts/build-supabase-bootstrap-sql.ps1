param(
    [string]$MigrationPath = "docs/sql/migration_db.sql",
    [switch]$IncludeOptionalNotifyTriggers,
    [switch]$CopyToClipboard
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$resolvedMigrationPath = Join-Path $repoRoot $MigrationPath
if (-not (Test-Path $resolvedMigrationPath)) {
    throw "Missing canonical migration: $MigrationPath"
}

$fullText = Get-Content -Path $resolvedMigrationPath -Raw

if ($IncludeOptionalNotifyTriggers) {
    $notifyRelative = "docs/sql/phase-extras-notify-triggers.sql"
    $notifyPath = Join-Path $repoRoot $notifyRelative
    if (-not (Test-Path $notifyPath)) {
        throw "Missing optional SQL file: $notifyRelative"
    }

    $fullText = @(
        $fullText.TrimEnd()
        ""
        "-- ============================================"
        "-- BEGIN: $notifyRelative"
        "-- ============================================"
        (Get-Content -Path $notifyPath -Raw)
    ) -join [Environment]::NewLine
}

if ($CopyToClipboard) {
    Set-Clipboard -Value $fullText
    if ($IncludeOptionalNotifyTriggers) {
        Write-Host "Copied $MigrationPath + notify triggers to clipboard"
    } else {
        Write-Host "Copied to clipboard: $MigrationPath"
    }
} else {
    if ($IncludeOptionalNotifyTriggers) {
        Write-Host "Canonical migration is $MigrationPath (notify triggers not written; use -CopyToClipboard to paste both)."
    } else {
        Write-Host "Canonical migration: $MigrationPath"
    }
}

Write-Host ""
Write-Host "Next step:"
Write-Host "1) Open docs/sql/migration_db.sql (or paste from clipboard)."
Write-Host "2) Paste into Supabase SQL Editor."
Write-Host "3) Run it once on a fresh database."
