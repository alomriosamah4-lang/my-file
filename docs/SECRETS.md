# GitHub Secrets for Android signing

This repository's CI can sign the release APK when the following GitHub repository secrets are set:

- `KEYSTORE_BASE64` — Base64 encoding of your `keystore.jks` file (single-line, no newlines).
- `KEYSTORE_PASSWORD` — Password for the keystore.
- `KEY_ALIAS` — Alias of the key inside the keystore.
- `KEY_PASSWORD` — Password for the key (often same as store password).

How to generate `KEYSTORE_BASE64`:

On macOS / Linux:

```bash
# create keystore locally (example)
keytool -genkeypair -v -keystore keystore.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000 

# then encode to base64 (no line breaks)
base64 -w0 keystore.jks > keystore.b64
cat keystore.b64
```

On Windows PowerShell:

```powershell
# generate keystore with keytool (from JDK)
keytool -genkeypair -v -keystore keystore.jks -alias mykey -keyalg RSA -keysize 2048 -validity 10000

# produce base64 string
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.jks")) | Out-File -Encoding ascii keystore.b64
Get-Content keystore.b64
```

Copy the single-line content and paste into the `KEYSTORE_BASE64` secret value in GitHub repository Settings → Secrets → Actions. Add the other secrets (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) accordingly.

Security notes:

- Never commit your keystore or passwords to Git. Use repository secrets only.
- Rotate keys/passwords if they become exposed. Use Google Play App Signing for production rollouts.
