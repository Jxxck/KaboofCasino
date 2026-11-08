plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

// The tests build the real pack from the pinned mod jar (read as a zip only) twice and compare the bytes.
val modJar = rootProject.layout.projectDirectory.file(providers.gradleProperty("casino.modJar"))
val modJarSha1 = providers.gradleProperty("casino.modJarSha1")
val packSrc = rootProject.layout.projectDirectory.dir("pack-src")
// Optional: a vanilla client jar (-Pcasino.clientJar=...) lets GuidePicturesTest check the vanilla textures the guide's
// recipe pictures name. Read only; nothing of it goes into the pack.
val clientJar = providers.gradleProperty("casino.clientJar")

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    inputs.file(modJar).withPathSensitivity(PathSensitivity.NONE)
    inputs.dir(packSrc).withPathSensitivity(PathSensitivity.RELATIVE)
    val jar = modJar
    val sha1 = modJarSha1
    val src = packSrc
    val client = clientJar
    inputs.property("clientJar", client.orElse(""))
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dkaboofcasino.modJar=" + jar.get().asFile.absolutePath,
            "-Dkaboofcasino.modJarSha1=" + sha1.get(),
            "-Dkaboofcasino.packSrc=" + src.asFile.absolutePath,
            "-Dkaboofcasino.clientJar=" + client.getOrElse(""),
        )
    })
    testLogging {
        events("skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
