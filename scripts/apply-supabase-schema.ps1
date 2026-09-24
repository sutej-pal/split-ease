# Apply docs/sql/migration_db.sql to the linked Supabase project.
#
# Usage (from the app repo root):
#   .\scripts\apply-supabase-schema.ps1
#
# Requires Node.js and SUPABASE_ACCESS_TOKEN + SUPABASE_URL in env or
# gitignored local.properties. See docs/supabase-reset.md.

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$script = Join-Path $PSScriptRoot 'apply-supabase-schema.js'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw 'Node.js is required to POST migration_db.sql (PowerShell ConvertTo-Json corrupts the query).'
}
if (-not (Test-Path $script)) {
    throw "Missing $script"
}

Set-Location $repoRoot
& node $script
if ($LASTEXITCODE -ne 0) {
    throw "apply-supabase-schema.js exited $LASTEXITCODE"
}
