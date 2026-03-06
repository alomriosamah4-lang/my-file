#!/usr/bin/env bash
set -euo pipefail

STORE=keystore.jks
ALIAS=${1:-mykey}
STOREPASS=${2:-changeit}
KEYPASS=${3:-$STOREPASS}

echo "Generating keystore: $STORE (alias=$ALIAS)"
keytool -genkeypair \
  -keystore "$STORE" \
  -storepass "$STOREPASS" \
  -alias "$ALIAS" \
  -keypass "$KEYPASS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Secure Vault, OU=Dev, O=Organization, L=City, S=State, C=US"

echo "Keystore generated: $STORE"
echo "To encode for GitHub secret (base64 single line):"
echo "  base64 -w0 $STORE > keystore.b64"
