package com.seekrtech.tools.slice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InjectSliceTest {

  private static final String SLICE_ENTRY =
      "native/linux/aarch64/librobolectric-nativeruntime.so";

  @TempDir Path tempDir;

  private Path smallJar(String... entries) throws Exception {
    Path jar = tempDir.resolve("dist-compat-" + entries.length + ".jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      for (int i = 0; i < entries.length; i++) {
        JarEntry entry = new JarEntry(entries[i]);
        entry.setTime(1_700_000_000_000L + i);
        out.putNextEntry(entry);
        out.write(("content-of-" + entries[i]).getBytes());
        out.closeEntry();
      }
    }
    return jar;
  }

  @Test
  void inject_addsSliceEntryWithExactBytes() throws Exception {
    Path input = smallJar("org/robolectric/Example.class");
    Path slice = tempDir.resolve("librobolectric-nativeruntime.so");
    byte[] soBytes = new byte[] {0x7f, 'E', 'L', 'F', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    Files.write(slice, soBytes);
    Path output = tempDir.resolve("dist-compat-arm64.jar");

    InjectSlice.inject(input, slice, output);

    try (JarFile result = new JarFile(output.toFile())) {
      JarEntry injected = result.getJarEntry(SLICE_ENTRY);
      assertTrue(injected != null, "slice entry missing: " + SLICE_ENTRY);
      assertEquals(JarEntry.STORED, injected.getMethod(), "native lib must be STORED");
      assertArrayEquals(soBytes, result.getInputStream(injected).readAllBytes());
    }
  }

  @Test
  void inject_preservesExistingEntriesByteIdentical() throws Exception {
    Path input = smallJar("org/robolectric/Example.class", "META-INF/MANIFEST.MF");
    Path slice = tempDir.resolve("librobolectric-nativeruntime.so");
    Files.write(slice, new byte[] {1, 2, 3});
    Path output = tempDir.resolve("dist-compat-arm64.jar");

    InjectSlice.inject(input, slice, output);

    try (JarFile original = new JarFile(input.toFile());
        JarFile result = new JarFile(output.toFile())) {
      Enumeration<JarEntry> entries = original.entries();
      int compared = 0;
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
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
  void inject_existingSliceEntry_failsLoudly() throws Exception {
    Path input = smallJar(SLICE_ENTRY);
    Path slice = tempDir.resolve("librobolectric-nativeruntime.so");
    Files.write(slice, new byte[] {9, 9, 9});
    Path output = tempDir.resolve("dist-compat-arm64.jar");

    assertThrows(IllegalStateException.class, () -> InjectSlice.inject(input, slice, output));
    assertTrue(Files.notExists(output), "failed injection must not leave an output jar");
  }

  @Test
  void inject_missingSliceFile_failsLoudly() throws Exception {
    Path input = smallJar("org/robolectric/Example.class");
    Path output = tempDir.resolve("dist-compat-arm64.jar");
    assertThrows(
        java.io.IOException.class,
        () -> InjectSlice.inject(input, tempDir.resolve("nope.so"), output));
  }
}
