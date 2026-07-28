# Android App Links (verified invite https)

## Why Settings shows “0 verified links”

Android only lists a host under **Open by default → Verified links** after **Digital Asset Links** succeed for that host.

SplitEase declares `https://splitease.app/invite…` (and optionally your mail-service host) with `android:autoVerify="true"`. Verification fails until each host serves:

`https://{host}/.well-known/assetlinks.json`

Until then, https invite links may show an open-with chooser. Custom-scheme links (`splitease://invite/{token}`) open the app without verification.

## Fix (host this file)

1. Copy [assetlinks.json](assetlinks.json) to your domain:
   - `https://splitease.app/.well-known/assetlinks.json`
   - and the same path on `www.splitease.app` if used
   - and on the mail-service host if invites use `MAIL_SERVICE_BASE_URL`
2. Serve as `Content-Type: application/json` (no auth, no redirect chain that drops the file).
3. Reinstall / clear defaults, or wait for Android to re-verify; then open:
   `https://splitease.app/invite/test`
4. Confirm Settings → Apps → SplitEase → Open by default shows verified hosts.

## Fingerprints in the JSON

| Build | Source |
|---|---|
| Debug | Machine debug keystore (`AndroidDebugKey`) |
| Release | `keystore/splitease-release.jks` alias `splitease` |

If you rotate the release keystore, update the SHA-256 in `assetlinks.json` (colons allowed):

```powershell
keytool -list -v -keystore keystore/splitease-release.jks -alias splitease
```

## Quick test (no domain setup)

1. Rebuild/reinstall the app (manifest + share text changes).
2. Share / copy an invite → message should show a single **https** link from `InviteLinks.urlFor` (mail-service host when `MAIL_SERVICE_BASE_URL` is set).
3. On a device with SplitEase installed, open that https page — the bridge should hand off into the app.
4. Legacy `splitease://invite/…` still opens the app if pasted or opened directly; it is inbound-only and not shared by the app.

## Host an https bridge (optional)

Serve [invite-bridge.html](invite-bridge.html) at `https://{host}/invite/{token}` (or rewrite all `/invite/*` to it). The page tries `splitease://` / `intent://` so Chrome can open the installed app even before App Links are verified.

**Important:** Do **not** auto-redirect to Play Store. Until `com.splitease.app` is published, Chrome’s Play fallback shows unrelated apps with a similar name. Keep Play as a manual button only; set `S.browser_fallback_url` back to the invite page (not `market://`).
