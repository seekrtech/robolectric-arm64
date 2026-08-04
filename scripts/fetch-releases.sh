#!/usr/bin/env bash
# Fetches upstream robolectric releases (GitHub API, newest 100) into a JSON file.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-releases.json}"
curl -fsSL "https://api.github.com/repos/robolectric/robolectric/releases?per_page=100" -o "$OUT"
echo "fetched releases into $OUT"
