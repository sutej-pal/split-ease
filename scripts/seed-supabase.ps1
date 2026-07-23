# Seed SplitEase Supabase with Admin + Member test accounts.
#
# Creates (idempotent):
#   - Auth users (email confirmed) + profiles
#   - Mutual friendship rows
#   - Group "Seed Group" with Admin=OWNER, Member=MEMBER
#
# Usage:
#   .\scripts\seed-supabase.ps1
#
# Requires SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY in env or local.properties.
#
# Optional: -SkipGroup / -SkipFriends to only create users+profiles.

param(
    [string]$ServiceRoleKey = $env:SUPABASE_SERVICE_ROLE_KEY,
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [switch]$SkipFriends,
    [switch]$SkipGroup
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

if (-not $SupabaseUrl) { $SupabaseUrl = Read-LocalProperty 'SUPABASE_URL' }
if (-not $ServiceRoleKey) { $ServiceRoleKey = Read-LocalProperty 'SUPABASE_SERVICE_ROLE_KEY' }
if (-not $SupabaseUrl) { throw 'SUPABASE_URL missing (env or local.properties).' }
if (-not $ServiceRoleKey) { throw 'SUPABASE_SERVICE_ROLE_KEY missing (env or local.properties).' }

$SupabaseUrl = $SupabaseUrl.TrimEnd('/')
$authHeaders = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
    'Content-Type' = 'application/json'
}
$restHeaders = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
    'Content-Type' = 'application/json'
    Prefer = 'resolution=merge-duplicates,return=representation'
}

# Test accounts (override via params if needed later)
$seedUsers = @(
    @{
        Name = 'Admin'
        Email = 'sutejpal234@gmail.com'
        Password = 'Default@234'
        Role = 'OWNER'
    },
    @{
        Name = 'Member'
        Email = 'sutejpal135@gmail.com'
        Password = 'Default@135'
        Role = 'MEMBER'
    }
)

function Get-AuthUserByEmail([string]$email) {
    $page = 1
    do {
        $resp = Invoke-RestMethod -Uri "$SupabaseUrl/auth/v1/admin/users?page=$page&per_page=100" -Headers $authHeaders -Method Get
        $users = @($resp.users)
        if ($users.Count -eq 0) { return $null }
        $match = $users | Where-Object { $_.email -and $_.email.ToLower() -eq $email.ToLower() } | Select-Object -First 1
        if ($match) { return $match }
        $page++
    } while ($users.Count -gt 0)
    return $null
}

function Ensure-AuthUser($account) {
    $existing = Get-AuthUserByEmail $account.Email
    if ($existing) {
        $body = @{
            password = $account.Password
            email_confirm = $true
            user_metadata = @{ display_name = $account.Name }
        } | ConvertTo-Json -Compress
        $updated = Invoke-RestMethod -Uri "$SupabaseUrl/auth/v1/admin/users/$($existing.id)" -Headers $authHeaders -Method Put -Body $body
        Write-Host "Updated auth user $($account.Name) <$($account.Email)> id=$($updated.id)"
        return $updated
    }

    $createBody = @{
        email = $account.Email
        password = $account.Password
        email_confirm = $true
        user_metadata = @{ display_name = $account.Name }
    } | ConvertTo-Json -Compress
    $created = Invoke-RestMethod -Uri "$SupabaseUrl/auth/v1/admin/users" -Headers $authHeaders -Method Post -Body $createBody
    Write-Host "Created auth user $($account.Name) <$($account.Email)> id=$($created.id)"
    return $created
}

function Upsert-Profile($userId, $email, $displayName) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $row = @(
        @{
            id = $userId
            email = $email
            display_name = $displayName
            photo_url = $null
            updated_at_epoch_ms = $now
        }
    ) | ConvertTo-Json -Compress
    $resp = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/profiles?on_conflict=id" -Headers $restHeaders -Method Post -Body $row
    Write-Host "Upserted profile $displayName"
    return $resp
}

Write-Host "Seeding $SupabaseUrl ..."
$created = @{}
foreach ($account in $seedUsers) {
    $user = Ensure-AuthUser $account
    Upsert-Profile $user.id $account.Email $account.Name | Out-Null
    $created[$account.Name] = @{
        Id = $user.id
        Email = $account.Email
        Name = $account.Name
        Role = $account.Role
    }
}

$admin = $created['Admin']
$member = $created['Member']
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

if (-not $SkipFriends) {
    $friendRows = @(
        @{
            id = [guid]::NewGuid().ToString()
            owner_user_id = $admin.Id
            friend_user_id = $member.Id
            email_snapshot = $member.Email
            display_name_snapshot = $member.Name
            updated_at_epoch_ms = $now
        },
        @{
            id = [guid]::NewGuid().ToString()
            owner_user_id = $member.Id
            friend_user_id = $admin.Id
            email_snapshot = $admin.Email
            display_name_snapshot = $admin.Name
            updated_at_epoch_ms = $now
        }
    )

    # Avoid duplicates: delete existing friendship pairs for these two, then insert
    $delHeaders = @{
        apikey = $ServiceRoleKey
        Authorization = "Bearer $ServiceRoleKey"
        Prefer = 'return=minimal'
    }
    Invoke-WebRequest -Uri "$SupabaseUrl/rest/v1/friends?or=(and(owner_user_id.eq.$($admin.Id),friend_user_id.eq.$($member.Id)),and(owner_user_id.eq.$($member.Id),friend_user_id.eq.$($admin.Id)))" `
        -Method Delete -Headers $delHeaders -UseBasicParsing | Out-Null

    $body = $friendRows | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/friends" -Headers $restHeaders -Method Post -Body $body | Out-Null
    Write-Host 'Seeded mutual friendship (Admin <-> Member)'
}

if (-not $SkipGroup) {
    $delHeaders = @{
        apikey = $ServiceRoleKey
        Authorization = "Bearer $ServiceRoleKey"
        Prefer = 'return=minimal'
    }
    # Remove prior seed group(s) owned by Admin named "Seed Group"
    $existingGroups = Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/groups?select=id&name=eq.Seed%20Group&created_by_user_id=eq.$($admin.Id)" `
        -Headers $authHeaders -Method Get
    foreach ($g in @($existingGroups)) {
        Invoke-WebRequest -Uri "$SupabaseUrl/rest/v1/groups?id=eq.$($g.id)" -Method Delete -Headers $delHeaders -UseBasicParsing | Out-Null
    }

    $groupId = [guid]::NewGuid().ToString()
    $groupBody = @(
        @{
            id = $groupId
            name = 'Seed Group'
            default_currency_code = 'INR'
            created_by_user_id = $admin.Id
            updated_at_epoch_ms = $now
        }
    ) | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/groups" -Headers $restHeaders -Method Post -Body $groupBody | Out-Null

    $memberBody = @(
        @{
            id = [guid]::NewGuid().ToString()
            group_id = $groupId
            user_id = $admin.Id
            role = 'OWNER'
            joined_at_epoch_ms = $now
        },
        @{
            id = [guid]::NewGuid().ToString()
            group_id = $groupId
            user_id = $member.Id
            role = 'MEMBER'
            joined_at_epoch_ms = $now
        }
    ) | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$SupabaseUrl/rest/v1/group_members" -Headers $restHeaders -Method Post -Body $memberBody | Out-Null
    Write-Host "Seeded group 'Seed Group' ($groupId) Admin=OWNER Member=MEMBER"
}

Write-Host ''
Write-Host 'Seed complete. Sign in with:'
Write-Host "  Admin  $($admin.Email) / Default@234"
Write-Host "  Member $($member.Email) / Default@135"
