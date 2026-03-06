# Running the CI workflows and releasing APKs

This document explains how to set up secrets and trigger the Android build and release workflows.

1) Prepare keystore and secrets

- Generate `keystore.jks` locally (see `scripts/generate-keystore.sh` or PowerShell script).
- Produce a base64 file (single line):

  Linux/macOS:

  ```bash
  base64 -w0 keystore.jks > keystore.b64
  ```

  Windows PowerShell:

  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes('keystore.jks')) | Out-File -Encoding ascii keystore.b64
  ```

2) Upload secrets to GitHub

- Use the `scripts/gh-set-secrets.sh` helper (requires `gh` and authentication):

```bash
chmod +x scripts/gh-set-secrets.sh
./scripts/gh-set-secrets.sh alomriosamah4-lang/my-file
```

Or set secrets manually in the repository `Settings` → `Secrets and variables` → `Actions`.

3) Trigger the Android build workflow

- Manual (Actions tab): Open repository → Actions → Android CI - Build APK → Run workflow → select branch `main` → Run.
- CLI (requires `gh`):

```bash
chmod +x scripts/run-android-build-workflow.sh
./scripts/run-android-build-workflow.sh alomriosamah4-lang/my-file main
```

4) Create a Release

- Tag a commit (locally) and push the tag to trigger `android-release.yml`:

```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

- The release workflow will build the release APK and attach `app-release.apk` to the GitHub Release.

5) Monitor and debug

- Open the Actions run details and inspect step logs. If SDK/NDK errors appear, check the `Install Android command-line tools` step and ensure network/firewall allows downloads.
- If keystore signing fails, check that `KEYSTORE_BASE64` is present and that passwords/alias match the keystore.
