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
    if (args.length != 3) {
      System.err.println("usage: InjectSlice <in.jar> <slice.so> <out.jar>");
      System.exit(1);
    }
    inject(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
  }

  public static void inject(Path inputJar, Path sliceFile, Path outputJar) throws IOException {
    byte[] sliceBytes = Files.readAllBytes(sliceFile);
    Path dir = outputJar.toAbsolutePath().getParent();
    Path tmp = Files.createTempFile(dir, outputJar.getFileName().toString(), ".tmp");
    try {
      rewriteJarWithSlice(inputJar, sliceBytes, tmp);
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

  private static void rewriteJarWithSlice(Path inputJar, byte[] sliceBytes, Path outputJar)
      throws IOException {
    try (ZipFile in = new ZipFile(inputJar.toFile());
        ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputJar))) {
      if (in.getEntry(SLICE_ENTRY) != null) {
        throw new IllegalStateException("slice entry already present: " + SLICE_ENTRY);
      }
      java.util.Enumeration<? extends ZipEntry> entries = in.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        ZipEntry copy = new ZipEntry(entry);
        out.putNextEntry(copy);
        if (!entry.isDirectory()) {
          try (java.io.InputStream content = in.getInputStream(entry)) {
            content.transferTo(out);
          }
        }
        out.closeEntry();
      }
      ZipEntry slice = new ZipEntry(SLICE_ENTRY);
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
