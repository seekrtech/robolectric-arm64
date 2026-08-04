#!/usr/bin/env bash
# Full publish pipeline for one robolectric version:
#   guard -> download -> patch -> inject -> publish -> record
# Exit codes: 0 = published or already published, 1 = blocked or any failure.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?usage: publish-pipeline.sh <robolectric-version>}"
REGISTRY="published-versions.json"
SLICE="slices/native/linux/aarch64/librobolectric-nativeruntime.so"

CP="$(./scripts/classpath.sh)"
PATCHER_CP="$(./gradlew -q :patcher:printRuntimeClasspath 2>/dev/null)"

# Early-exit guard: skip already-published versions so re-dispatch is idempotent.
if java -cp "$CP" com.seekrtech.tools.registry.Registry "$REGISTRY" is-published "$VERSION"; then
  echo "already published: $VERSION"
  exit 0
fi
# A blocked version must never be retried automatically.
if java -cp "$CP" com.seekrtech.tools.registry.Registry "$REGISTRY" is-blocked "$VERSION"; then
  echo "blocked, refusing to publish: $VERSION"
  exit 1
fi
if [[ ! -f "$SLICE" ]]; then
  echo "missing slice: $SLICE" >&2
  exit 1
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "downloading nativeruntime $VERSION"
curl -fsSL "https://repo1.maven.org/maven2/org/robolectric/nativeruntime/$VERSION/nativeruntime-$VERSION.jar" -o "$WORK/nativeruntime.jar"
curl -fsSL "https://repo1.maven.org/maven2/org/robolectric/nativeruntime/$VERSION/nativeruntime-$VERSION.pom" -o "$WORK/nativeruntime.pom"

DIST_COMPAT_VERSION="$(java -cp "$CP" com.seekrtech.tools.distcompat.ResolveDistCompatVersion "$WORK/nativeruntime.pom")"
echo "dist-compat: $DIST_COMPAT_VERSION"

echo "downloading dist-compat $DIST_COMPAT_VERSION"
curl -fsSL "https://repo1.maven.org/maven2/org/robolectric/nativeruntime-dist-compat/$DIST_COMPAT_VERSION/nativeruntime-dist-compat-$DIST_COMPAT_VERSION.jar" -o "$WORK/dist-compat.jar"

echo "patching nativeruntime"
java -cp "$PATCHER_CP" com.seekrtech.robolectricarm64.PatchLoader "$WORK/nativeruntime.jar" "$WORK/patched.jar"

echo "injecting arm64 slice"
java -cp "$CP" com.seekrtech.tools.slice.InjectSlice "$WORK/dist-compat.jar" "$SLICE" "$WORK/dist-compat-arm64.jar"

echo "publishing to GitHub Packages"
./gradlew :publish:publishAllPublicationsToGitHubPackagesRepository \
  -PpatchedJar="$WORK/patched.jar" \
  -PinjectedJar="$WORK/dist-compat-arm64.jar" \
  -PnativeruntimeVersion="$VERSION" \
  -PdistCompatVersion="$DIST_COMPAT_VERSION"

echo "recording published version"
java -cp "$CP" com.seekrtech.tools.registry.Registry "$REGISTRY" record-published "$VERSION"
echo "published: $VERSION"
