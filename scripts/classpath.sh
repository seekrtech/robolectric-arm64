#!/usr/bin/env bash
# Prints the tools runtime classpath (classes dir + deps) for direct `java -cp` calls.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :tools:printRuntimeClasspath 2>/dev/null
