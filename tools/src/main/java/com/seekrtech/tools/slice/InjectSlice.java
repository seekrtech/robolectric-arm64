package com.seekrtech.tools.slice;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Injects the arm64 native slice into a copy of the upstream dist-compat jar. The native library
 * is written STORED (uncompressed) so loaders can map it directly, matching upstream dist-compat
 * layout. Output is written to a temp file and atomically moved into place, so a failed run
 * never leaves a partial jar.
 */
public final class InjectSlice {

  public static final String SLICE_ENTRY = "native/linux/aarch64/librobolectric-nativeruntime.so";

  private InjectSlice() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 3 && args.length != 4) {
      System.err.println("usage: InjectSlice <in.jar> <slice.so> <out.jar> [entry]");
      System.exit(1);
    }
    String entry = args.length == 4 ? args[3] : SLICE_ENTRY;
    inject(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), entry);
  }

  public static void inject(Path inputJar, Path sliceFile, Path outputJar) throws IOException {
    inject(inputJar, sliceFile, outputJar, SLICE_ENTRY);
  }

  public static void inject(Path inputJar, Path sliceFile, Path outputJar, String entry)
      throws IOException {
    byte[] sliceBytes = Files.readAllBytes(sliceFile);
    Path dir = outputJar.toAbsolutePath().getParent();
    Path tmp = Files.createTempFile(dir, outputJar.getFileName().toString(), ".tmp");
    try {
      rewriteJarWithSlice(inputJar, sliceBytes, tmp, entry);
      try {
        Files.move(tmp, outputJar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, outputJar, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(tmp);
      throw e;
    }
  }

  private static void rewriteJarWithSlice(
      Path inputJar, byte[] sliceBytes, Path outputJar, String entry) throws IOException {
    try (ZipFile in = new ZipFile(inputJar.toFile());
        ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputJar))) {
      if (in.getEntry(entry) != null) {
        throw new IllegalStateException("slice entry already present: " + entry);
      }
      java.util.Enumeration<? extends ZipEntry> entries = in.entries();
      while (entries.hasMoreElements()) {
        ZipEntry copyEntry = entries.nextElement();
        ZipEntry copy = new ZipEntry(copyEntry);
        out.putNextEntry(copy);
        if (!copyEntry.isDirectory()) {
          try (java.io.InputStream content = in.getInputStream(copyEntry)) {
            content.transferTo(out);
          }
        }
        out.closeEntry();
      }
      ZipEntry slice = new ZipEntry(entry);
      slice.setMethod(ZipEntry.STORED);
      CRC32 crc = new CRC32();
      crc.update(sliceBytes);
      slice.setSize(sliceBytes.length);
      slice.setCrc(crc.getValue());
      out.putNextEntry(slice);
      out.write(sliceBytes);
      out.closeEntry();
    }
  }
}
