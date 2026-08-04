package com.seekrtech.tools.candidate;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.seekrtech.tools.registry.Registry;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Selects the next robolectric version to publish from the upstream GitHub releases API: the
 * highest stable tag (numeric triple, no pre-release/draft/snapshot) that is neither already
 * published nor blocked in the registry.
 */
public final class NextCandidate {

  private static final Gson GSON = new Gson();
  private static final Pattern STABLE_TAG = Pattern.compile("\\d+\\.\\d+\\.\\d+");

  /** One entry of the GitHub releases API; unknown JSON fields are ignored. */
  public record Release(
      @SerializedName("tag_name") String tagName, boolean prerelease, boolean draft) {}

  private NextCandidate() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  /**
   * CLI: {@code NextCandidate <releases.json> <registry.json>}. Exit codes: 0 = candidate printed,
   * 1 = no candidate (or error), 2 = usage error.
   */
  static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length != 2) {
      err.println("usage: NextCandidate <releases.json> <registry.json>");
      return 2;
    }
    try {
      Release[] releases = GSON.fromJson(Files.readString(Path.of(args[0])), Release[].class);
      String candidate = next(List.of(releases), Registry.load(Path.of(args[1])));
      if (candidate == null) {
        return 1;
      }
      out.println(candidate);
      return 0;
    } catch (IOException | RuntimeException e) {
      err.println("next-candidate error: " + e.getMessage());
      return 1;
    }
  }

  /** Highest eligible stable tag, or null when every stable tag is published or blocked. */
  public static String next(List<Release> releases, Registry registry) {
    String best = null;
    for (Release release : releases) {
      if (release == null || release.prerelease() || release.draft()) {
        continue;
      }
      String tag = normalizeTag(release.tagName());
      if (tag == null || registry.isPublished(tag) || registry.isBlocked(tag)) {
        continue;
      }
      if (best == null || compareVersions(tag, best) > 0) {
        best = tag;
      }
    }
    return best;
  }

  static String normalizeTag(String tag) {
    if (tag == null) {
      return null;
    }
    String stripped = tag.startsWith("v") ? tag.substring(1) : tag;
    return STABLE_TAG.matcher(stripped).matches() ? stripped : null;
  }

  /** Numeric segment comparison: 4.14.1 > 4.9.0, unlike lexicographic ordering. */
  static int compareVersions(String a, String b) {
    String[] aSegments = a.split("\\.");
    String[] bSegments = b.split("\\.");
    for (int i = 0; i < Math.min(aSegments.length, bSegments.length); i++) {
      int cmp = Integer.compare(Integer.parseInt(aSegments[i]), Integer.parseInt(bSegments[i]));
      if (cmp != 0) {
        return cmp;
      }
    }
    return Integer.compare(aSegments.length, bSegments.length);
  }
}
