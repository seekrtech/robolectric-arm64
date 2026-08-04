pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "robolectric-arm64"

include(":patcher")
include(":tools")
include(":publish")
include(":smoke")
