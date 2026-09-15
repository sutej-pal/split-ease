# Clear SplitEase Supabase app data + auth users.
#
# Usage (PowerShell):
#   $env:SUPABASE_SERVICE_ROLE_KEY = "<service_role_secret>"
#   .\scripts\clear-supabase.ps1
#
# Or pass the key once:
#   .\scripts\clear-supabase.ps1 -ServiceRoleKey "<service_role_secret>"
#
# Or add to gitignored local.properties:
#   SUPABASE_SERVICE_ROLE_KEY=...
#   SUPABASE_URL=https://xxxx.supabase.co   (optional; falls back to existing URL key)
#
# Never commit the service role key.

param(
    [string]$ServiceRoleKey = $env:SUPABASE_SERVICE_ROLE_KEY,
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [switch]$SkipAuthUsers
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

if (-not $SupabaseUrl) {
    $SupabaseUrl = Read-LocalProperty 'SUPABASE_URL'
}
if (-not $ServiceRoleKey) {
    $ServiceRoleKey = Read-LocalProperty 'SUPABASE_SERVICE_ROLE_KEY'
}

if (-not $SupabaseUrl) {
    throw 'SUPABASE_URL missing. Set env, -SupabaseUrl, or local.properties.'
}
if (-not $ServiceRoleKey) {
    throw 'SUPABASE_SERVICE_ROLE_KEY missing. Set env, -ServiceRoleKey, or local.properties.'
}

$SupabaseUrl = $SupabaseUrl.TrimEnd('/')
$headers = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
    Prefer = 'return=minimal'
}

function Delete-AllRows([string]$table, [string]$pkColumn = 'id') {
    $uri = "$SupabaseUrl/rest/v1/${table}?${pkColumn}=not.is.null"
    $resp = Invoke-WebRequest -Uri $uri -Method Delete -Headers $headers -UseBasicParsing
    Write-Host "$table -> HTTP $($resp.StatusCode)"
}

Write-Host "Clearing app tables at $SupabaseUrl ..."
# Child tables first (FK order). Includes later-phase tables.
# pin_boards PK is group_id (not id).
$tables = @(
    @{ Name = 'payments'; Pk = 'id' },
    @{ Name = 'expense_splits'; Pk = 'id' },
    @{ Name = 'expenses'; Pk = 'id' },
    @{ Name = 'pin_boards'; Pk = 'group_id' },
    @{ Name = 'device_tokens'; Pk = 'id' },
    @{ Name = 'invites'; Pk = 'id' },
    @{ Name = 'group_members'; Pk = 'id' },
    @{ Name = 'groups'; Pk = 'id' },
    @{ Name = 'friends'; Pk = 'id' },
    @{ Name = 'profiles'; Pk = 'id' }
)
foreach ($t in $tables) {
    Delete-AllRows $t.Name $t.Pk
}

$authHeaders = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
}

$deleted = 0
if (-not $SkipAuthUsers) {
    $page = 1
    do {
        $usersResp = Invoke-RestMethod -Uri "$SupabaseUrl/auth/v1/admin/users?page=$page&per_page=100" -Headers $authHeaders -Method Get
        $users = @($usersResp.users)
        if ($users.Count -eq 0) { break }
        foreach ($u in $users) {
            Invoke-WebRequest -Uri "$SupabaseUrl/auth/v1/admin/users/$($u.id)" -Method Delete -Headers $authHeaders -UseBasicParsing | Out-Null
            $deleted++
        }
        $page++
    } while ($users.Count -gt 0)
    Write-Host "auth.users deleted: $deleted"
} else {
    Write-Host 'Skipped auth.users (-SkipAuthUsers).'
}

$countHeaders = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
    Prefer = 'count=exact'
}
Write-Host 'Verify:'
foreach ($t in $tables) {
    $selectCol = $t.Pk
    $r = Invoke-WebRequest -Uri "$SupabaseUrl/rest/v1/$($t.Name)?select=${selectCol}&limit=1" -Headers $countHeaders -Method Get -UseBasicParsing
    Write-Host "  $($t.Name) Content-Range=$($r.Headers['Content-Range'])"
}
if (-not $SkipAuthUsers) {
    $left = Invoke-RestMethod -Uri "$SupabaseUrl/auth/v1/admin/users?page=1&per_page=50" -Headers $authHeaders -Method Get
    Write-Host ("  auth.users remaining=" + @($left.users).Count)
}

Write-Host 'Done.'
