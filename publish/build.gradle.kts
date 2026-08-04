import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

plugins {
    `maven-publish`
}

/**
 * Publishes the two patched artifacts to GitHub Packages.
 *
 * Coordinates (group com.seekrtech):
 *  - robolectric-nativeruntime-arm64:<nativeruntimeVersion>          artifact: patched nativeruntime jar
 *  - robolectric-nativeruntime-dist-compat-arm64:<distCompatVersion> artifact: dist-compat jar with injected slice
 *
 * Invoked from the publish workflow with:
 *  ./gradlew :publish:publishAllPublicationsToGitHubPackagesRepository \
 *      -PpatchedJar=<path> -PinjectedJar=<path> \
 *      -PnativeruntimeVersion=<v> -PdistCompatVersion=<v>
 *
 * Auth comes from GITHUB_ACTOR/GITHUB_TOKEN (workflow permissions: packages: write).
 *
 * The -P properties are only required when a publish task actually runs, so plain
 * builds (./gradlew build) never need them. Missing properties fail the publish
 * task loudly before anything is uploaded.
 */

val patchedJar = providers.gradleProperty("patchedJar")
val injectedJar = providers.gradleProperty("injectedJar")
val nativeruntimeVersion = providers.gradleProperty("nativeruntimeVersion")
val distCompatVersion = providers.gradleProperty("distCompatVersion")
val publishUsername = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR").orEmpty()
val publishPassword = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN").orEmpty()

// Placeholders are never uploaded: publish tasks are guarded in doFirst below.
val patchedJarFile = File(patchedJar.orNull ?: "MISSING-patchedJar")
val injectedJarFile = File(injectedJar.orNull ?: "MISSING-injectedJar")
val nativeruntimeVersionValue = nativeruntimeVersion.orNull ?: "MISSING-nativeruntimeVersion"
val distCompatVersionValue = distCompatVersion.orNull ?: "MISSING-distCompatVersion"

tasks.withType<AbstractPublishToMaven>().configureEach {
    doFirst {
        require(patchedJar.isPresent) { "required: -PpatchedJar=<path to patched nativeruntime jar>" }
        require(injectedJar.isPresent) { "required: -PinjectedJar=<path to slice-injected dist-compat jar>" }
        require(nativeruntimeVersion.isPresent) {
            "required: -PnativeruntimeVersion=<robolectric nativeruntime version>"
        }
        require(distCompatVersion.isPresent) {
            "required: -PdistCompatVersion=<dist-compat version from upstream POM>"
        }
        require(publishUsername.isNotEmpty()) {
            "missing publish credentials: set GITHUB_ACTOR/GITHUB_TOKEN"
        }
        require(publishPassword.isNotEmpty()) {
            "missing publish credentials: set GITHUB_ACTOR/GITHUB_TOKEN"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("nativeruntimeArm64") {
            artifact(patchedJarFile)
            artifactId = "robolectric-nativeruntime-arm64"
            version = nativeruntimeVersionValue
            pom {
                name.set("robolectric-nativeruntime-arm64")
                description.set("Robolectric nativeruntime patched for linux/aarch64")
                withXml {
                    val deps = asNode().appendNode("dependencies")
                    fun dep(group: String, artifact: String, version: String, scope: String) {
                        val d = deps.appendNode("dependency")
                        d.appendNode("groupId", group)
                        d.appendNode("artifactId", artifact)
                        d.appendNode("version", version)
                        d.appendNode("scope", scope)
                    }
                    dep("org.robolectric", "shadowapi", nativeruntimeVersionValue, "compile")
                    dep("org.robolectric", "utils", nativeruntimeVersionValue, "compile")
                    dep("org.robolectric", "utils-reflector", nativeruntimeVersionValue, "compile")
                    dep("com.google.guava", "guava", "33.3.1-jre", "compile")
                    dep(
                        "com.seekrtech",
                        "robolectric-nativeruntime-dist-compat-arm64",
                        distCompatVersionValue,
                        "runtime",
                    )
                }
            }
        }
        create<MavenPublication>("distCompatArm64") {
            artifact(injectedJarFile)
            artifactId = "robolectric-nativeruntime-dist-compat-arm64"
            version = distCompatVersionValue
            pom {
                name.set("robolectric-nativeruntime-dist-compat-arm64")
                description.set("Robolectric nativeruntime-dist-compat with arm64 native slice")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/seekrtech/robolectric-arm64")
            credentials {
                username = publishUsername
                password = publishPassword
            }
        }
    }
}
