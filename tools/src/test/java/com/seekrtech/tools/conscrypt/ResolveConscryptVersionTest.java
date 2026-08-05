package com.seekrtech.tools.conscrypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResolveConscryptVersionTest {

  /** Fixture mirroring the real org.robolectric:robolectric:4.14.1 POM. */
  private static final String POM_WITH_CONSCRYPT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>org.robolectric</groupId>
        <artifactId>robolectric</artifactId>
        <version>4.14.1</version>
        <dependencies>
          <dependency>
            <groupId>org.robolectric</groupId>
            <artifactId>shadowapi</artifactId>
            <version>4.14.1</version>
            <scope>compile</scope>
          </dependency>
          <dependency>
            <groupId>org.conscrypt</groupId>
            <artifactId>conscrypt-openjdk-uber</artifactId>
            <version>2.5.2</version>
            <scope>runtime</scope>
          </dependency>
        </dependencies>
      </project>
      """;

  private static final String POM_WITHOUT_CONSCRYPT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>org.robolectric</groupId>
        <artifactId>robolectric</artifactId>
        <version>4.14.1</version>
        <dependencies>
          <dependency>
            <groupId>org.robolectric</groupId>
            <artifactId>shadowapi</artifactId>
            <version>4.14.1</version>
          </dependency>
        </dependencies>
      </project>
      """;

  @Test
  void fromPomXml_returnsConscryptVersion() {
    assertEquals("2.5.2", ResolveConscryptVersion.fromPomXml(POM_WITH_CONSCRYPT));
  }

  @Test
  void fromPomXml_missingConscryptDep_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () -> ResolveConscryptVersion.fromPomXml(POM_WITHOUT_CONSCRYPT));
  }

  @Test
  void fromPomXml_emptyDependencies_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ResolveConscryptVersion.fromPomXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.robolectric</groupId>
                  <artifactId>robolectric</artifactId>
                  <version>4.14.1</version>
                </project>
                """));
  }

  @Test
  void fromPomXml_wrongConscryptGroup_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ResolveConscryptVersion.fromPomXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.robolectric</groupId>
                  <artifactId>robolectric</artifactId>
                  <version>4.14.1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>conscrypt-openjdk-uber</artifactId>
                      <version>9.9.9</version>
                    </dependency>
                  </dependencies>
                </project>
                """));
  }

  @Test
  void fromPomXml_malformedXml_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () -> ResolveConscryptVersion.fromPomXml("<project><broken>"));
  }

  @Test
  void pomUrl_usesMavenCentralLayout() {
    assertEquals(
        "https://repo1.maven.org/maven2/org/robolectric/robolectric/4.14.1/robolectric-4.14.1.pom",
        ResolveConscryptVersion.pomUrl("4.14.1"));
  }
}
