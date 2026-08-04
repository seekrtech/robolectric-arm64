package com.seekrtech.tools.registry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Append-only registry of published and blocked robolectric versions, persisted as
 * {@code published-versions.json}. Drives the watch workflow's early-exit guard: a version that
 * is already published is skipped; a version that previously failed publish is not retried.
 */
public final class Registry {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Set<String> published = new LinkedHashSet<>();
  private final Set<String> blocked = new LinkedHashSet<>();

  private Registry() {}

  public static Registry empty() {
    return new Registry();
  }

  /** Loads the registry; a missing file yields an empty registry (first run). */
  public static Registry load(Path file) throws IOException {
    Registry registry = new Registry();
    if (Files.exists(file)) {
      JsonFile json = GSON.fromJson(Files.readString(file), JsonFile.class);
      if (json != null) {
        registry.published.addAll(json.published);
        registry.blocked.addAll(json.blocked);
      }
    }
    return registry;
  }

  public boolean isPublished(String version) {
    return published.contains(version);
  }

  public boolean isBlocked(String version) {
    return blocked.contains(version);
  }

  public void recordPublished(String version) {
    published.add(version);
  }

  public void recordBlocked(String version) {
    blocked.add(version);
  }

  public List<String> published() {
    return List.copyOf(published);
  }

  public List<String> blocked() {
    return List.copyOf(blocked);
  }

  /** Writes atomically so a crashed run never leaves a half-written registry. */
  public void save(Path file) throws IOException {
    JsonFile json = new JsonFile();
    json.published = new ArrayList<>(published);
    json.blocked = new ArrayList<>(blocked);
    Path dir = file.toAbsolutePath().getParent();
    Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
    try {
      Files.writeString(tmp, GSON.toJson(json));
      try {
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException | RuntimeException e) {
      Files.deleteIfExists(tmp);
      throw e;
    }
  }

  private static class JsonFile {
    @SerializedName("published") List<String> published = List.of();
    @SerializedName("blocked") List<String> blocked = List.of();
  }
}
