plugins {
    id("com.android.library") version "8.13.2"
}

android {
    namespace = "com.seekrtech.smoke"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }
}

repositories {
    google()
    mavenCentral()
    val arm64MavenRepo: String? = providers.gradleProperty("arm64MavenRepo").orNull
    if (arm64MavenRepo != null) {
        maven { url = uri(arm64MavenRepo) }
    }
}

val arm64MavenRepo: String? = providers.gradleProperty("arm64MavenRepo").orNull
// Mirror the STForestKit consumer wiring (-Parm64RobolectricSlice): when the patched
// artifacts are available in a local maven repo, substitute the upstream Robolectric
// native loader with the arm64 slices. Without it the suite runs plain upstream
// Robolectric and is expected to fail loudly on linux/aarch64.
if (arm64MavenRepo != null) {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.robolectric:nativeruntime"))
                .using(module("com.seekrtech:robolectric-nativeruntime-arm64:4.14.1"))
            substitute(module("org.robolectric:nativeruntime-dist-compat"))
                .using(module("com.seekrtech:robolectric-nativeruntime-dist-compat-arm64:1.0.16"))
        }
    }
}

dependencies {
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-runtime:2.6.1")
    testAnnotationProcessor("androidx.room:room-compiler:2.6.1")
    // Compile-time android classes; Robolectric downloads the instrumented jar itself at runtime.
    testImplementation("org.robolectric:android-all:14-robolectric-10818077")
}

// Root forces --release 17 on all JavaCompile tasks, which conflicts with AGP's own
// source/target options; AGP manages compatibility for this module.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
}

// Root forces useJUnitPlatform(); Robolectric tests are JUnit 4 runners.
// Registered after the root's subprojects{} action, so useJUnit() wins at realization.
tasks.withType<Test>().configureEach {
    useJUnit()
    maxHeapSize = "2g"
    systemProperty("robolectric.logging.enabled", "false")
    // conscrypt ships no linux-aarch64 JNI; the consumer disables it the same way
    // (STForestKit testOptions unitTests.all systemProperty), so the remaining
    // failure surface on linux/aarch64 is the nativeruntime load itself.
    systemProperty("robolectric.conscryptMode", "OFF")
}
