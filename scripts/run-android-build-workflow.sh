#!/usr/bin/env bash
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found. Install from https://cli.github.com/"
  exit 1
fi

repo=${1:-alomriosamah4-lang/my-file}
branch=${2:-main}

echo "Triggering Android build workflow for $repo@$branch"
gh workflow run android-build.yml --repo "$repo" --ref "$branch"

echo "Triggered. Monitor Actions tab for progress."
