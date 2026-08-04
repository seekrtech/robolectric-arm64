package com.seekrtech.tools.candidate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seekrtech.tools.candidate.NextCandidate.Release;
import com.seekrtech.tools.registry.Registry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextCandidateTest {

  private static Release rel(String tag, boolean prerelease, boolean draft) {
    return new Release(tag, prerelease, draft);
  }

  @Test
  void next_picksHighestStableVersion() {
    Registry registry = Registry.empty();
    assertEquals(
        "4.14.1",
        NextCandidate.next(
            List.of(rel("4.9.0", false, false), rel("4.13.0", false, false), rel("4.14.1", false, false)),
            registry));
  }

  @Test
  void next_skipsPublished() {
    Registry registry = Registry.empty();
    registry.recordPublished("4.14.1");
    assertEquals(
        "4.13.0",
        NextCandidate.next(List.of(rel("4.14.1", false, false), rel("4.13.0", false, false)), registry));
  }

  @Test
  void next_skipsBlocked() {
    Registry registry = Registry.empty();
    registry.recordBlocked("4.13.0");
    assertEquals(
        "4.14.1",
        NextCandidate.next(List.of(rel("4.14.1", false, false), rel("4.13.0", false, false)), registry));
  }

  @Test
  void next_skipsPrereleaseAndDraft() {
    Registry registry = Registry.empty();
    assertEquals(
        "4.14.1",
        NextCandidate.next(
            List.of(rel("4.15-beta-1", true, false), rel("4.16.0", false, true), rel("4.14.1", false, false)),
            registry));
  }

  @Test
  void next_skipsSnapshotTags() {
    Registry registry = Registry.empty();
    assertEquals(
        "4.14.1",
        NextCandidate.next(List.of(rel("4.15.0-SNAPSHOT", false, false), rel("4.14.1", false, false)), registry));
  }

  @Test
  void next_versionComparisonIsNumericNotLexicographic() {
    Registry registry = Registry.empty();
    assertEquals(
        "4.14.1",
        NextCandidate.next(List.of(rel("4.14.1", false, false), rel("4.9.0", false, false)), registry));
  }

  @Test
  void next_allEligiblePublished_returnsNull() {
    Registry registry = Registry.empty();
    registry.recordPublished("4.14.1");
    registry.recordPublished("4.13.0");
    assertNull(
        NextCandidate.next(List.of(rel("4.14.1", false, false), rel("4.13.0", false, false)), registry));
  }

  @Test
  void next_emptyReleases_returnsNull() {
    assertNull(NextCandidate.next(List.of(), Registry.empty()));
  }

  @Test
  void next_stripsLeadingV() {
    Registry registry = Registry.empty();
    assertEquals("4.14.1", NextCandidate.next(List.of(rel("v4.14.1", false, false)), registry));
  }

  @TempDir Path tempDir;

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();

  private int run(String... args) {
    return NextCandidate.run(args, new PrintStream(out), new PrintStream(err));
  }

  @Test
  void run_parsesGithubReleasesJson_printsHighestStable() throws Exception {
    Path releases = tempDir.resolve("releases.json");
    Files.writeString(
        releases,
        """
        [
          {"tag_name": "4.15-beta-1", "prerelease": true, "draft": false},
          {"tag_name": "4.14.1", "prerelease": false, "draft": false},
          {"tag_name": "4.13.0", "prerelease": false, "draft": false}
        ]
        """);
    Path registryFile = tempDir.resolve("published-versions.json");
    Files.writeString(registryFile, "{}");
    assertEquals(0, run(releases.toString(), registryFile.toString()));
    assertEquals("4.14.1", out.toString().trim());
  }

  @Test
  void run_noCandidate_exitsOneAndPrintsNothing() throws Exception {
    Path releases = tempDir.resolve("releases.json");
    Files.writeString(releases, """
        [
          {"tag_name": "4.14.1", "prerelease": false, "draft": false}
        ]
        """);
    Path registryFile = tempDir.resolve("published-versions.json");
    Files.writeString(registryFile, "{\"published\": [\"4.14.1\"]}");
    assertEquals(1, run(releases.toString(), registryFile.toString()));
    assertEquals("", out.toString());
  }

  @Test
  void run_malformedReleasesJson_exitsOne() throws Exception {
    Path releases = tempDir.resolve("releases.json");
    Files.writeString(releases, "not json at all");
    Path registryFile = tempDir.resolve("published-versions.json");
    Files.writeString(registryFile, "{}");
    assertEquals(1, run(releases.toString(), registryFile.toString()));
  }
}
