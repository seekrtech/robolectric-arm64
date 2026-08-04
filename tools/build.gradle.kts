plugins {
    `java-library`
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Thin `java -cp` invocation surface for scripts/ wrappers and the publish workflow.
// JavaExec keeps the classpath (incl. gson) resolved by Gradle; printRuntimeClasspath
// lets scripts call the tools directly so exit codes reach bash untouched.
tasks.register<JavaExec>("runRegistry") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seekrtech.tools.registry.Registry")
}

tasks.register<JavaExec>("runInjectSlice") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seekrtech.tools.slice.InjectSlice")
}

tasks.register<JavaExec>("runResolveDistCompat") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seekrtech.tools.distcompat.ResolveDistCompatVersion")
}

tasks.register<JavaExec>("runNextCandidate") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.seekrtech.tools.candidate.NextCandidate")
}

tasks.register("printRuntimeClasspath") {
    dependsOn("classes")
    doLast {
        println(sourceSets["main"].runtimeClasspath.asPath)
    }
}
