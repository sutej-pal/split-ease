# Configure SplitEase signup confirmation email to send a 6-digit OTP
# instead of (only) a "confirm email" link.
#
# The Android app cannot change Supabase email content — templates live in
# the Auth project config. This script updates them via the Management API.
#
# Prerequisites:
#   1. Create a personal access token: https://supabase.com/dashboard/account/tokens
#   2. Set it once:
#        $env:SUPABASE_ACCESS_TOKEN = "sbp_..."
#      or add to gitignored local.properties:
#        SUPABASE_ACCESS_TOKEN=sbp_...
#
# Usage:
#   .\scripts\configure-signup-otp-email.ps1
#
# Project ref is taken from SUPABASE_URL (env or local.properties).

param(
    [string]$AccessToken = $env:SUPABASE_ACCESS_TOKEN,
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [string]$ProjectRef = $env:SUPABASE_PROJECT_REF
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$localProps = Join-Path $repoRoot 'local.properties'

function Read-LocalProperty([string]$name) {
    if (-not (Test-Path $localProps)) { return $null }
    $line = Get-Content $localProps | Where-Object { $_ -match "^\s*$([regex]::Escape($name))\s*=" } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

if (-not $AccessToken) { $AccessToken = Read-LocalProperty 'SUPABASE_ACCESS_TOKEN' }
if (-not $SupabaseUrl) { $SupabaseUrl = Read-LocalProperty 'SUPABASE_URL' }

if (-not $ProjectRef) {
    if ($SupabaseUrl -match 'https?://([a-z0-9]+)\.supabase\.co') {
        $ProjectRef = $Matches[1]
    }
}

if (-not $AccessToken) {
    throw @'
SUPABASE_ACCESS_TOKEN missing.

1. Open https://supabase.com/dashboard/account/tokens
2. Generate a token
3. Run:  $env:SUPABASE_ACCESS_TOKEN = "sbp_..."
   or add SUPABASE_ACCESS_TOKEN=... to local.properties (gitignored)
4. Re-run this script

Or paste the OTP template manually:
  https://supabase.com/dashboard/project/<ref>/auth/templates
  → Confirm signup → replace body with docs/supabase-confirm-signup-otp.html
'@
}
if (-not $ProjectRef) {
    throw 'Could not resolve project ref. Set SUPABASE_URL or -ProjectRef.'
}

$subject = 'Your SplitEase verification code'
# Keep subject + body OTP-first; no ConfirmationURL so clients are not steered to a link.
$body = @'
<h2>Confirm your SplitEase account</h2>
<p>Enter this 6-digit code in the app to activate your account:</p>
<p style="font-size:24px;letter-spacing:4px;font-weight:bold;">{{ .Token }}</p>
<p>If you did not create a SplitEase account, you can ignore this email.</p>
'@

$payload = @{
    mailer_subjects_confirmation = $subject
    mailer_templates_confirmation_content = $body
} | ConvertTo-Json -Compress

$uri = "https://api.supabase.com/v1/projects/$ProjectRef/config/auth"
$headers = @{
    Authorization = "Bearer $AccessToken"
    'Content-Type' = 'application/json'
}

Write-Host "Updating Confirm signup template for project $ProjectRef ..."
Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $payload | Out-Null
Write-Host 'Done. New signups will email {{ .Token }} (6-digit OTP).'
Write-Host "Dashboard: https://supabase.com/dashboard/project/$ProjectRef/auth/templates"
