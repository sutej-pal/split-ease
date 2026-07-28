# SplitEase signing keystore (local only)

`*.jks` / `*.keystore` are gitignored. Do **not** commit this folder’s secrets.

## Files

- `splitease-release.jks` — Play / release upload key (created locally)

## `local.properties` keys (gitignored)

```
KEYSTORE_FILE=keystore/splitease-release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=splitease
KEY_PASSWORD=...
```

`app/build.gradle.kts` reads these for the `release` signingConfig.

## Regenerate (only if you intentionally rotate)

```powershell
keytool -genkeypair -keystore keystore/splitease-release.jks -alias splitease `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass YOUR_STORE_PASS -keypass YOUR_KEY_PASS `
  -dname "CN=SplitEase, OU=Mobile, O=SplitEase, L=Unknown, ST=Unknown, C=IN"
```

Back up the JKS + passwords somewhere safe (password manager). Losing them means you cannot update the same Play signing identity without Play App Signing recovery.
