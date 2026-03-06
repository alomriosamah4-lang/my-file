#!/usr/bin/env bash
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found. Install from https://cli.github.com/"
  exit 1
fi

repo=${1:-alomriosamah4-lang/my-file}

read -p "Path to keystore base64 file (keystore.b64): " kb
read -s -p "Keystore password: " kpass
echo
read -p "Key alias: " kalias
read -s -p "Key password (press enter to use keystore password): " kkeypass
echo
if [ -z "$kkeypass" ]; then kkeypass="$kpass"; fi

echo "Setting secrets on repo $repo"
gh secret set KEYSTORE_BASE64 --body "$(cat "$kb")" -R "$repo"
gh secret set KEYSTORE_PASSWORD --body "$kpass" -R "$repo"
gh secret set KEY_ALIAS --body "$kalias" -R "$repo"
gh secret set KEY_PASSWORD --body "$kkeypass" -R "$repo"

echo "Secrets set. Remove keystore.b64 from any machines when done."
