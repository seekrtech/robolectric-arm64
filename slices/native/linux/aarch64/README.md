# Slices

Native libraries injected into published artifacts, one per file. All slices are
built for linux/aarch64 and shipped uncompressed (STORED) inside their jars.

| File | Artifact | Source |
|---|---|---|
| `librobolectric-nativeruntime.so` | `robolectric-nativeruntime-dist-compat-arm64` | Built on the org's `[self-hosted, jose, linux, arm64]` runner from the release-tag C++ sources (see PLAN.md) |
| `libconscrypt_openjdk_jni-linux-aarch_64.so` | `conscrypt-openjdk-uber-arm64` | openSUSE Tumbleweed `conscrypt-2.5.2-5.1.aarch64.rpm` (`/usr/lib64/conscrypt/libconscrypt_jni.so`), Apache-2.0 |

## conscrypt slice provenance

Conscrypt 2.5.2 (the version Robolectric pins) ships no linux-aarch64 JNI
upstream. openSUSE Tumbleweed builds the same 2.5.2 sources for aarch64:

- RPM: `https://download.opensuse.org/ports/aarch64/tumbleweed/repo/oss/aarch64/conscrypt-2.5.2-5.1.aarch64.rpm`
- Extracted from `/usr/lib64/conscrypt/libconscrypt_jni.so` inside the RPM.
- SHA-256: `a1228732495bd24247ae81b7ac395f88513e7503350a1d6bdd42481d7c2cc765`
- Renamed to `libconscrypt_openjdk_jni-linux-aarch_64.so` on commit: the
  conscrypt loader resolves its native resource as
  `META-INF/native/libconscrypt_openjdk_jni-linux-aarch_64.so`
  (`HostProperties` already maps `aarch64` → `AARCH_64`), so no bytecode patch
  is needed — only the missing resource.

Verified ABI-matched to the Maven Central 2.5.2 jar: the exported
`JNI_OnLoad`/`RegisterNatives` table covers 288/288 `NativeCrypto` native
methods with identical signatures (javap diff against the upstream 2.5.2
`conscrypt-openjdk` jar is empty).
