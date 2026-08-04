package com.seekrtech.tools.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryTest {

  @TempDir Path tempDir;

  @Test
  void load_missingFile_returnsEmptyRegistry() throws Exception {
    Registry registry = Registry.load(tempDir.resolve("does-not-exist.json"));
    assertFalse(registry.isPublished("4.14.1"));
    assertFalse(registry.isBlocked("4.14.1"));
  }

  @Test
  void recordPublished_thenIsPublished() {
    Registry registry = Registry.empty();
    registry.recordPublished("4.14.1");
    assertTrue(registry.isPublished("4.14.1"));
    assertFalse(registry.isPublished("4.13.0"));
  }

  @Test
  void recordBlocked_thenIsBlocked() {
    Registry registry = Registry.empty();
    registry.recordBlocked("4.14.1");
    assertTrue(registry.isBlocked("4.14.1"));
  }

  @Test
  void recordDuplicate_doesNotDuplicateEntries() {
    Registry registry = Registry.empty();
    registry.recordPublished("4.14.1");
    registry.recordPublished("4.14.1");
    registry.recordPublished("4.14.1");
    assertEquals(1, registry.published().size());
  }

  @Test
  void save_thenLoad_roundtripsAllState() throws Exception {
    Registry registry = Registry.empty();
    registry.recordPublished("4.14.1");
    registry.recordPublished("4.13.0");
    registry.recordBlocked("4.12.0");
    Path file = tempDir.resolve("published-versions.json");
    registry.save(file);

    Registry reloaded = Registry.load(file);
    assertEquals(registry.published(), reloaded.published());
    assertEquals(registry.blocked(), reloaded.blocked());
    assertTrue(reloaded.isPublished("4.14.1"));
    assertTrue(reloaded.isBlocked("4.12.0"));
  }

  @Test
  void load_existingFile_parsesBothLists() throws Exception {
    Path file = tempDir.resolve("published-versions.json");
    Files.writeString(
        file, "{\"published\": [\"4.14.1\"], \"blocked\": [\"4.12.0\", \"4.11.0\"]}");
    Registry registry = Registry.load(file);
    assertTrue(registry.isPublished("4.14.1"));
    assertTrue(registry.isBlocked("4.12.0"));
    assertTrue(registry.isBlocked("4.11.0"));
    assertFalse(registry.isPublished("4.11.0"));
  }

  @Test
  void load_partialJson_missingListsTreatAsEmpty() throws Exception {
    Path file = tempDir.resolve("published-versions.json");
    Files.writeString(file, "{\"published\": [\"4.14.1\"]}");
    Registry registry = Registry.load(file);
    assertTrue(registry.isPublished("4.14.1"));
    assertTrue(registry.blocked().isEmpty());
  }

  @Test
  void load_emptyJsonObject_returnsEmptyRegistry() throws Exception {
    Path file = tempDir.resolve("published-versions.json");
    Files.writeString(file, "{}");
    Registry registry = Registry.load(file);
    assertTrue(registry.published().isEmpty());
    assertTrue(registry.blocked().isEmpty());
  }

  @Test
  void save_overwritesExistingFile() throws Exception {
    Path file = tempDir.resolve("published-versions.json");
    Registry first = Registry.empty();
    first.recordPublished("4.14.1");
    first.save(file);

    Registry second = Registry.empty();
    second.recordPublished("4.15.0");
    second.save(file);

    Registry reloaded = Registry.load(file);
    assertTrue(reloaded.isPublished("4.15.0"));
    assertFalse(reloaded.isPublished("4.14.1"));
  }
}
