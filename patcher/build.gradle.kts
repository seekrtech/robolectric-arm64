plugins {
    `java-library`
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Upstream artifact the patcher operates on, resolved for tests.
    testImplementation("org.robolectric:nativeruntime:4.14.1")
    testImplementation("org.robolectric:shadowapi:4.14.1")
    testImplementation("org.robolectric:utils:4.14.1")
    testImplementation("org.robolectric:utils-reflector:4.14.1")
    testImplementation("com.google.guava:guava:33.3.1-jre")
}

tasks.register<JavaExec>("runPatchLoader") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seekrtech.robolectricarm64.PatchLoader")
}

tasks.register("printRuntimeClasspath") {
    doLast {
        println(sourceSets["main"].runtimeClasspath.asPath)
    }
}
