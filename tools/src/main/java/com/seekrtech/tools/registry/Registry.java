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

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println(
          "usage: Registry <file.json> is-published|is-blocked|record-published|record-blocked <version>"
              + " | Registry <file.json> print");
      System.exit(2);
    }
    Path file = Path.of(args[0]);
    String command = args[1];
    try {
      Registry registry = load(file);
      switch (command) {
        case "print":
          System.out.println("published: " + registry.published());
          System.out.println("blocked: " + registry.blocked());
          return;
        case "is-published":
          System.exit(registry.isPublished(args[2]) ? 0 : 1);
          return;
        case "is-blocked":
          System.exit(registry.isBlocked(args[2]) ? 0 : 1);
          return;
        case "record-published":
          registry.recordPublished(args[2]);
          registry.save(file);
          return;
        case "record-blocked":
          registry.recordBlocked(args[2]);
          registry.save(file);
          return;
        default:
          System.err.println("unknown command: " + command);
          System.exit(2);
      }
    } catch (IOException | RuntimeException e) {
      System.err.println("registry error: " + e.getMessage());
      System.exit(1);
    }
  }

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
