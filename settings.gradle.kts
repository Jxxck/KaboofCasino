plugins {
    // lets Gradle download the JDK 25 toolchain when none is installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "KaboofCasino"

// Plain-Java tool that turns the MineVegas Casino jar (read as a zip, never executed) into the resource pack.
include(":pack-tool")
