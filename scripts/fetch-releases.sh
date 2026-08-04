#!/usr/bin/env bash
# Fetches upstream robolectric releases (GitHub API, newest 100) into a JSON file.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="${1:-releases.json}"
AUTH=()
if [[ -n "${GITHUB_TOKEN:-}" ]]; then
  AUTH=(-H "Authorization: Bearer $GITHUB_TOKEN")
fi
curl -fsSL "${AUTH[@]}" "https://api.github.com/repos/robolectric/robolectric/releases?per_page=100" -o "$OUT"
echo "fetched releases into $OUT"
