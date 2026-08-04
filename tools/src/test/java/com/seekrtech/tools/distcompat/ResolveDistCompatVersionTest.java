package com.seekrtech.tools.distcompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ResolveDistCompatVersionTest {

  /** Fixture mirroring the real org.robolectric:nativeruntime:4.14.1 POM. */
  private static final String POM_WITH_DIST_COMPAT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>org.robolectric</groupId>
        <artifactId>nativeruntime</artifactId>
        <version>4.14.1</version>
        <dependencies>
          <dependency>
            <groupId>org.robolectric</groupId>
            <artifactId>shadowapi</artifactId>
            <version>4.14.1</version>
            <scope>compile</scope>
          </dependency>
          <dependency>
            <groupId>org.robolectric</groupId>
            <artifactId>nativeruntime-dist-compat</artifactId>
            <version>1.0.16</version>
            <scope>runtime</scope>
          </dependency>
        </dependencies>
      </project>
      """;

  private static final String POM_WITHOUT_DIST_COMPAT =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>org.robolectric</groupId>
        <artifactId>nativeruntime</artifactId>
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
  void fromPomXml_returnsDistCompatVersion() {
    assertEquals("1.0.16", ResolveDistCompatVersion.fromPomXml(POM_WITH_DIST_COMPAT));
  }

  @Test
  void fromPomXml_missingDistCompatDep_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () -> ResolveDistCompatVersion.fromPomXml(POM_WITHOUT_DIST_COMPAT));
  }

  @Test
  void fromPomXml_emptyDependencies_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ResolveDistCompatVersion.fromPomXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.robolectric</groupId>
                  <artifactId>nativeruntime</artifactId>
                  <version>4.14.1</version>
                </project>
                """));
  }

  @Test
  void fromPomXml_wrongDistCompatGroup_failsLoudly() {
    assertThrows(
        IllegalStateException.class,
        () ->
            ResolveDistCompatVersion.fromPomXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.robolectric</groupId>
                  <artifactId>nativeruntime</artifactId>
                  <version>4.14.1</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>nativeruntime-dist-compat</artifactId>
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
        () -> ResolveDistCompatVersion.fromPomXml("<project><broken>"));
  }

  @Test
  void pomUrl_usesMavenCentralLayout() {
    assertEquals(
        "https://repo1.maven.org/maven2/org/robolectric/nativeruntime/4.14.1/nativeruntime-4.14.1.pom",
        ResolveDistCompatVersion.pomUrl("4.14.1"));
  }
}
