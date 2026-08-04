#!/usr/bin/env bash
# Prints the next robolectric version to publish (highest stable, unprocessed), or
# exits 1 when there is none. Fetches releases.json first if not present.
set -euo pipefail
cd "$(dirname "$0")/.."
RELEASES="${1:-releases.json}"
REGISTRY="${2:-published-versions.json}"
if [[ ! -f "$RELEASES" ]]; then
  ./scripts/fetch-releases.sh "$RELEASES"
fi
CP="$(./scripts/classpath.sh)"
java -cp "$CP" com.seekrtech.tools.candidate.NextCandidate "$RELEASES" "$REGISTRY"
