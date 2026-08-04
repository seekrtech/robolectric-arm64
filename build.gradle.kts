plugins {
    base
}

allprojects {
    group = "com.seekrtech"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
