package com.seekrtech.robolectricarm64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchLoaderTest {

  private static final String TARGET =
      "org/robolectric/nativeruntime/DefaultNativeRuntimeLoader.class";

  @TempDir Path tempDir;

  private String savedOsName;
  private String savedOsArch;

  @BeforeEach
  void saveOsProps() {
    savedOsName = System.getProperty("os.name");
    savedOsArch = System.getProperty("os.arch");
  }

  @AfterEach
  void restoreOsProps() {
    System.setProperty("os.name", savedOsName);
    System.setProperty("os.arch", savedOsArch);
  }

  /** Locates a jar on the test runtime classpath by artifact prefix. */
  private static Path classpathJar(String artifactPrefix) {
    for (String entry : System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
      Path p = Path.of(entry);
      if (p.getFileName() != null && p.getFileName().toString().startsWith(artifactPrefix)) {
        return p;
      }
    }
    throw new IllegalStateException("artifact not on test classpath: " + artifactPrefix);
  }

  /** Loads the patched DefaultNativeRuntimeLoader child-first in an isolated loader. */
  private Class<?> loadPatchedLoader(Path patchedJar, List<Path> runtimeJars) throws Exception {
    List<URL> urls = new ArrayList<>();
    urls.add(patchedJar.toUri().toURL());
    for (Path jar : runtimeJars) {
      urls.add(jar.toUri().toURL());
    }
    // Child-first: the patched DefaultNativeRuntimeLoader must win over the
    // unpatched copy on the test runtime classpath.
    URLClassLoader loader =
        new URLClassLoader(urls.toArray(URL[]::new), PatchLoaderTest.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
              Class<?> c = findLoadedClass(name);
              if (c == null) {
                try {
                  c = findClass(name);
                } catch (ClassNotFoundException e) {
                  c = super.loadClass(name, resolve);
                }
              }
              if (resolve) {
                resolveClass(c);
              }
              return c;
            }
          }
        };
    return loader.loadClass("org.robolectric.nativeruntime.DefaultNativeRuntimeLoader");
  }

  private static boolean isSupported(Class<?> loaderClass) throws Exception {
    // Upstream declares isSupported() private; the patched jar keeps that visibility.
    java.lang.reflect.Method m = loaderClass.getDeclaredMethod("isSupported");
    m.setAccessible(true);
    return (boolean) m.invoke(null);
  }

  private static List<Path> runtimeJars() {
    List<Path> jars = new ArrayList<>();
    for (String prefix :
        List.of("shadowapi-", "utils-", "utils-reflector-", "guava-", "failureaccess-", "listenablefuture-")) {
      for (String entry :
          System.getProperty("java.class.path").split(System.getProperty("path.separator"))) {
        Path p = Path.of(entry);
        if (p.getFileName() != null && p.getFileName().toString().startsWith(prefix)) {
          jars.add(p);
        }
      }
    }
    return jars;
  }

  @Test
  void isSupported_acceptsLinuxAarch64_afterPatch() throws Exception {
    Path upstream = classpathJar("nativeruntime-4.14.1");
    Path patched = tempDir.resolve("patched.jar");
    PatchLoader.patch(upstream, patched);

    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "aarch64");

    Class<?> loader = loadPatchedLoader(patched, runtimeJars());
    assertTrue(isSupported(loader), "patched loader must accept linux+aarch64");
  }

  @Test
  void isSupported_keepsUpstreamSemantics_forOtherPlatforms() throws Exception {
    Path upstream = classpathJar("nativeruntime-4.14.1");
    Path patched = tempDir.resolve("patched.jar");
    PatchLoader.patch(upstream, patched);

    List<Path> jars = runtimeJars();

    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "x86_64");
    assertTrue(isSupported(loadPatchedLoader(patched, jars)), "linux+x86_64 stays supported");

    System.setProperty("os.name", "Mac OS X");
    System.setProperty("os.arch", "aarch64");
    assertTrue(isSupported(loadPatchedLoader(patched, jars)), "mac+aarch64 stays supported");

    System.setProperty("os.name", "Mac OS X");
    System.setProperty("os.arch", "x86_64");
    assertTrue(isSupported(loadPatchedLoader(patched, jars)), "mac+x86_64 stays supported");

    System.setProperty("os.name", "Windows 11");
    System.setProperty("os.arch", "x86_64");
    assertTrue(isSupported(loadPatchedLoader(patched, jars)), "windows+x86_64 stays supported");

    System.setProperty("os.name", "Windows 11");
    System.setProperty("os.arch", "aarch64");
    assertFalse(isSupported(loadPatchedLoader(patched, jars)), "windows+aarch64 stays unsupported");

    System.setProperty("os.name", "Linux");
    System.setProperty("os.arch", "s390x");
    assertFalse(isSupported(loadPatchedLoader(patched, jars)), "linux+s390x stays unsupported");
  }

  @Test
  void patch_onlyTouchesTargetClass_leavesOtherEntriesByteIdentical() throws Exception {
    Path upstream = classpathJar("nativeruntime-4.14.1");
    Path patched = tempDir.resolve("patched.jar");
    PatchLoader.patch(upstream, patched);

    try (JarFile original = new JarFile(upstream.toFile());
        JarFile result = new JarFile(patched.toFile())) {
      Enumeration<JarEntry> entries = original.entries();
      int compared = 0;
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().equals(TARGET)) {
          continue;
        }
        JarEntry resultEntry = result.getJarEntry(entry.getName());
        assertTrue(resultEntry != null, "missing entry: " + entry.getName());
        assertArrayEquals(
            original.getInputStream(entry).readAllBytes(),
            result.getInputStream(resultEntry).readAllBytes(),
            "entry changed: " + entry.getName());
        compared++;
      }
      assertTrue(compared > 0, "jar contained no entries to compare");
    }
  }

  @Test
  void patch_preservesStoredEntryCompression() throws Exception {
    Path upstream = classpathJar("nativeruntime-4.14.1");
    Path patched = tempDir.resolve("patched.jar");
    PatchLoader.patch(upstream, patched);

    try (JarFile original = new JarFile(upstream.toFile());
        JarFile result = new JarFile(patched.toFile())) {
      Enumeration<JarEntry> entries = original.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getMethod() != JarEntry.STORED) {
          continue;
        }
        JarEntry resultEntry = result.getJarEntry(entry.getName());
        assertEquals(
            JarEntry.STORED,
            resultEntry.getMethod(),
            "STORED method lost for: " + entry.getName());
        assertEquals(entry.getCrc(), resultEntry.getCrc(), "CRC changed for: " + entry.getName());
        assertEquals(
            entry.getSize(), resultEntry.getSize(), "size changed for: " + entry.getName());
      }
    }
  }

  @Test
  void patch_twice_isIdempotent() throws Exception {
    Path upstream = classpathJar("nativeruntime-4.14.1");
    Path once = tempDir.resolve("once.jar");
    Path twice = tempDir.resolve("twice.jar");
    PatchLoader.patch(upstream, once);
    PatchLoader.patch(once, twice);

    assertArrayEquals(
        Files.readAllBytes(once),
        Files.readAllBytes(twice),
        "re-patching must produce byte-identical output");
  }
}
