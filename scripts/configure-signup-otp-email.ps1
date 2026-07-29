# Configure SplitEase signup confirmation email delivery.
#
# Preferred path (Free tier friendly):
#   Use the Render mail-service as a Supabase "Send Email" Auth Hook.
#   That bypasses the Free-tier restriction that blocks editing Auth email templates
#   when using Supabase's default mail provider.
#
# Fallback path (Custom SMTP / Pro):
#   Patch Confirm signup template to include {{ .Token }} and set OTP length = 6.
#
# Prerequisites:
#   1. Create a personal access token: https://supabase.com/dashboard/account/tokens
#   2. Set it once:
#        $env:SUPABASE_ACCESS_TOKEN = "sbp_..."
#      or add to gitignored local.properties:
#        SUPABASE_ACCESS_TOKEN=sbp_...
#   3. Redeploy mail-service with POST /supabase/send-email-hook
#
# Usage:
#   .\scripts\configure-signup-otp-email.ps1
#   .\scripts\configure-signup-otp-email.ps1 -DisableHook   # turn hook off
#   .\scripts\configure-signup-otp-email.ps1 -TemplateOnly  # try template patch only

param(
    [string]$AccessToken = $env:SUPABASE_ACCESS_TOKEN,
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [string]$ProjectRef = $env:SUPABASE_PROJECT_REF,
    [string]$MailServiceBaseUrl = $env:MAIL_SERVICE_BASE_URL,
    [string]$HookSecret = $env:SEND_EMAIL_HOOK_SECRET,
    [switch]$DisableHook,
    [switch]$TemplateOnly
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$localProps = Join-Path $repoRoot 'local.properties'
$templatePath = Join-Path $repoRoot 'docs\supabase-confirm-signup-otp.html'

function Read-LocalProperty([string]$name) {
    if (-not (Test-Path $localProps)) { return $null }
    $line = Get-Content $localProps | Where-Object { $_ -match "^\s*$([regex]::Escape($name))\s*=" } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line -split '=', 2)[1].Trim()
}

if (-not $AccessToken) { $AccessToken = Read-LocalProperty 'SUPABASE_ACCESS_TOKEN' }
if (-not $SupabaseUrl) { $SupabaseUrl = Read-LocalProperty 'SUPABASE_URL' }
if (-not $MailServiceBaseUrl) { $MailServiceBaseUrl = Read-LocalProperty 'MAIL_SERVICE_BASE_URL' }
if (-not $HookSecret) { $HookSecret = Read-LocalProperty 'SEND_EMAIL_HOOK_SECRET' }

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
'@
}
if (-not $ProjectRef) {
    throw 'Could not resolve project ref. Set SUPABASE_URL or -ProjectRef.'
}

$uri = "https://api.supabase.com/v1/projects/$ProjectRef/config/auth"
$headers = @{
    Authorization = "Bearer $AccessToken"
    'Content-Type' = 'application/json'
}

if ($DisableHook) {
    $payload = @{
        hook_send_email_enabled = $false
        mailer_otp_length = 6
    } | ConvertTo-Json -Compress
    Write-Host "Disabling Send Email hook for project $ProjectRef ..."
    Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $payload | Out-Null
    Write-Host 'Done. Hook disabled.'
    return
}

if (-not $TemplateOnly) {
    $base = ($MailServiceBaseUrl).Trim().TrimEnd('/')
    if (-not $base) {
        throw 'MAIL_SERVICE_BASE_URL missing (env or local.properties).'
    }
    $hookUri = "$base/supabase/send-email-hook"

    # Confirm endpoint is live before enabling the hook (avoids signup 500s).
    try {
        $probe = Invoke-WebRequest -Uri $hookUri -Method Post -ContentType 'application/json' `
            -Body '{"user":{"email":"probe@example.com"},"email_data":{"email_action_type":"signup","token":"000000"}}' `
            -UseBasicParsing -TimeoutSec 60
        if ($probe.StatusCode -ge 500) {
            throw "Hook probe returned HTTP $($probe.StatusCode)"
        }
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match '404' -or $msg -match 'Cannot POST') {
            throw @"
Mail-service hook endpoint is not deployed yet:
  $hookUri

Redeploy mail-service (sutej-pal/mail-service) on Render, then re-run this script.
"@
        }
        # 401 (signature required) still means the route exists.
        if ($msg -notmatch '401' -and $msg -notmatch '400') {
            Write-Warning "Hook probe warning: $msg (continuing if route likely exists)"
        }
    }

    $payloadObj = @{
        mailer_otp_length = 6
        hook_send_email_enabled = $true
        hook_send_email_uri = $hookUri
    }
    if ($HookSecret) {
        $payloadObj.hook_send_email_secrets = $HookSecret
    }

    $payload = $payloadObj | ConvertTo-Json -Compress
    Write-Host "Enabling Send Email hook -> $hookUri (OTP length=6) ..."
    Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $payload | Out-Null
    Write-Host 'Done. Signup confirmation emails will be delivered by mail-service as 6-digit OTPs.'
    Write-Host "Dashboard: https://supabase.com/dashboard/project/$ProjectRef/auth/hooks"
    if (-not $HookSecret) {
        Write-Host 'Note: set SEND_EMAIL_HOOK_SECRET on Render + as hook secret in Supabase for signature verification.'
    }
    return
}

# Template-only fallback (requires Custom SMTP or Pro).
if (-not (Test-Path $templatePath)) {
    throw "Template not found: $templatePath"
}

$subject = 'Your SplitEase 6-digit verification code'
$body = (Get-Content -Path $templatePath -Raw).Trim()
$payload = @{
    mailer_otp_length = 6
    mailer_subjects_confirmation = $subject
    mailer_templates_confirmation_content = $body
} | ConvertTo-Json -Compress

Write-Host "Updating Confirm signup template + OTP length=6 for project $ProjectRef ..."
try {
    Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $payload | Out-Null
    Write-Host 'Done. New signups will email {{ .Token }} as a 6-digit OTP.'
} catch {
    throw @"
Template edit failed (common on Free tier without Custom SMTP).

Use the mail-service Auth Hook instead (default mode of this script):
  .\scripts\configure-signup-otp-email.ps1

Details: $($_.Exception.Message)
"@
}
