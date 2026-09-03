# Google Sign-In

Native **Continue with Google** on Login, Sign up, and invite-join. The app uses Credential Manager to get a Google ID token, then exchanges it with Supabase Auth (`signInWith(IDToken)`). Email OTP is skipped — Google already verified the address.

The Web client **secret** stays in the Supabase dashboard. The Android app only stores the Web **client ID** (public).

## 1. Google Cloud

1. Open [Google Auth Platform → Clients](https://console.cloud.google.com/auth/clients).
2. Create an OAuth client of type **Web application**. Copy the client ID and client secret.
3. Create an OAuth client of type **Android**:
   - Package name: `com.splitease.app`
   - SHA-1: debug and release signing certs (see below).
4. Consent screen: add scopes `openid`, `.../auth/userinfo.email`, `.../auth/userinfo.profile`.

### SHA-1 fingerprints

```bash
./gradlew :app:signingReport
```

Use the SHA-1 for the **debug** keystore (day-to-day installs) and the **release** keystore (`KEYSTORE_FILE` in `local.properties`). Play App Signing also has an **app signing** SHA-1 in Play Console → App integrity — add that before a store build.

## 2. Supabase

1. Authentication → Providers → **Google**: enable.
2. **Client IDs**: Web client ID first. If you also paste the Android client ID, comma-separate them (`WEB_ID,ANDROID_ID`).
3. **Client secret**: Web client secret.
4. Leave **Skip nonce check** off (the app sends a SHA-256 nonce).
5. Authentication → URL configuration: keep `splitease://auth-callback` on the allow list (other Auth redirects; not required for this ID-token path).

## 3. Android app

In gitignored `local.properties` (or the same env var):

```
GOOGLE_WEB_CLIENT_ID=xxxxx.apps.googleusercontent.com
```

Use the **Web** client ID, not the Android one. Rebuild so `BuildConfig.GOOGLE_WEB_CLIENT_ID` updates.

## 4. How to test

1. Device/emulator has a Google account and Play Services.
2. Welcome → Log in (or Sign up) → **Continue with Google**.
3. Pick an account → land on Groups (no OTP).
4. Sign out and repeat — returning users skip welcome mail.
5. First-time Google users get the one-time welcome email (same path as signup OTP).

If the button shows “isn't configured on this build”, `GOOGLE_WEB_CLIENT_ID` is missing. If Google returns no accounts, add a Google account on the device.
