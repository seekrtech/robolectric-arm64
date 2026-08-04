package com.seekrtech.tools.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryCliTest {

  @TempDir Path tempDir;

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int run(String... args) {
    return Registry.run(args, new PrintStream(out), new PrintStream(err));
  }

  @Test
  void isPublished_missingVersion_returnsUsageErrorNotOne() {
    Path file = tempDir.resolve("registry.json");
    // Exit code 2, never 1: 1 means "not published" and would make a bash
    // guard proceed with publishing.
    assertEquals(2, run(file.toString(), "is-published"));
  }

  @Test
  void isPublished_presentVersion_returnsExitCodes() {
    Path file = tempDir.resolve("registry.json");
    assertEquals(1, run(file.toString(), "is-published", "4.14.1"));
    assertEquals(0, run(file.toString(), "record-published", "4.14.1"));
    assertEquals(0, run(file.toString(), "is-published", "4.14.1"));
  }

  @Test
  void recordPersistsToDisk() throws Exception {
    Path file = tempDir.resolve("registry.json");
    assertEquals(0, run(file.toString(), "record-blocked", "4.15.0-beta2"));
    Registry registry = Registry.load(file);
    assertTrue(registry.isBlocked("4.15.0-beta2"));
  }

  @Test
  void unknownCommand_returnsUsageError() {
    Path file = tempDir.resolve("registry.json");
    assertEquals(2, run(file.toString(), "frobnicate", "4.14.1"));
  }

  @Test
  void print_listsBothLists() {
    Path file = tempDir.resolve("registry.json");
    assertEquals(0, run(file.toString(), "record-published", "4.14.1"));
    assertEquals(0, run(file.toString(), "record-blocked", "4.12.0"));
    assertEquals(0, run(file.toString(), "print"));
    String printed = out.toString();
    assertTrue(printed.contains("4.14.1"));
    assertTrue(printed.contains("4.12.0"));
  }
}
