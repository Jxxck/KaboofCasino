plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "de.tigges"
version = "0.7.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Exactly what the live server ships in server/libraries (META-INF/libraries.list of paper-26.3-205.jar)
val sqliteJdbc = "org.xerial:sqlite-jdbc:3.49.1.0"

// Paper's channel listener API lives only in the server jar: extract io/papermc/paper/network/** into build/server-api
// and compile against that (never shipped). Netty comes from the server's libraries (same version as at runtime).
val paperServerJar = layout.projectDirectory.file(providers.gradleProperty("casino.paperServerJar"))
val serverLibraries = providers.gradleProperty("casino.serverLibraries")
val nettyVersion = providers.gradleProperty("casino.nettyVersion")
val paperNetworkApi = tasks.register<Jar>("paperNetworkApi") {
    description = "Extracts Paper's io.papermc.paper.network classes from the server jar (compile only)."
    from(zipTree(paperServerJar)) {
        include("io/papermc/paper/network/**")
    }
    archiveFileName = "paper-network-api.jar"
    destinationDirectory = layout.buildDirectory.dir("server-api")
}
fun netty(module: String): String =
    serverLibraries.get() + "/io/netty/netty-" + module + "/" + nettyVersion.get() + "/netty-" + module + "-" +
        nettyVersion.get() + ".jar"
val nettyJars = files(netty("buffer"), netty("common"), netty("transport"), netty("resolver"))

/** The pack tool (subproject) and its runtime dependencies, resolved for the buildPack task. */
val packTool: Configuration = configurations.create("packTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.205-stable")
    // Tests use API value types (Component, Material keys, YamlConfiguration), never a running server.
    testImplementation("io.papermc.paper:paper-api:26.3.build.205-stable")

    // Shaded + relocated. Its only compile dependency is slf4j-api, which Paper itself ships -> kept OUT of the jar.
    implementation("com.zaxxer:HikariCP:7.1.0")

    // Provided by the server classpath at runtime; never shade it (native lib + parent-first class loading).
    compileOnly(sqliteJdbc)
    testImplementation(sqliteJdbc)

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")

    packTool(project(":pack-tool"))

    // provided by the server at runtime
    compileOnly(files(paperNetworkApi))
    compileOnly(nettyJars)
    testImplementation(nettyJars)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    // -classfile: JOML (paper-api's Transformation types) ships Java 1.2 class files with type annotations, which
    // javac reports for every use; it says nothing about this code.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-Xlint:-classfile"))
}

// ---- resource pack -------------------------------------------------------------------------------------------------
// The pack is built deterministically from the MineVegas jar (read as a zip, never executed) plus pack-src/, then
// embedded into the plugin jar under pack/. Same inputs -> same bytes -> same SHA-1, so clients keep their cached copy.
// The same task writes the optional music pack (KaboofCasino-music.zip + .sha1, the mod's six lounge tracks, ~22 MB)
// next to it; it is embedded the same way (pack/KaboofCasino-music.zip) and offered as a second, non-required pack.
val modJar = layout.projectDirectory.file(providers.gradleProperty("casino.modJar"))
val modJarSha1 = providers.gradleProperty("casino.modJarSha1")
val packSrc = layout.projectDirectory.dir("pack-src")
val packOut = layout.buildDirectory.dir("pack")

val buildPack = tasks.register<JavaExec>("buildPack") {
    group = "build"
    description = "Builds the KaboofCasino resource pack and the optional music pack into build/pack (zips, .sha1, " +
        "gui-layout.json)."
    classpath = packTool
    mainClass = "de.tigges.kaboofcasino.packtool.PackTool"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    maxHeapSize = "1g"
    inputs.file(modJar).withPathSensitivity(PathSensitivity.NONE).withPropertyName("modJar")
    inputs.property("modJarSha1", modJarSha1)
    inputs.dir(packSrc).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("packSrc")
    outputs.dir(packOut).withPropertyName("packOut")
    outputs.cacheIf { true }
    val jarFile = modJar
    val sha1 = modJarSha1
    val src = packSrc
    val out = packOut
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--mod-jar", jarFile.get().asFile.absolutePath,
            "--mod-sha1", sha1.get(),
            "--src", src.asFile.absolutePath,
            "--out", out.get().asFile.absolutePath,
        )
    })
}

tasks.processResources {
    from(layout.projectDirectory.file("LICENSE-MineVegas.txt"))
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    from(buildPack) {
        into("pack")
    }
}

tasks.jar {
    archiveClassifier = "plain" // the plain jar is not deployable (no Hikari inside)
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("com.zaxxer.hikari", "de.tigges.kaboofcasino.libs.hikari")
    dependencies {
        exclude(dependency("org.slf4j:.*:.*"))
    }
    exclude("META-INF/maven/**", "META-INF/versions/*/module-info.class", "module-info.class")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val testTmp = layout.buildDirectory.dir("tmp/test-tmp")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.io.tmpdir=" + testTmp.get().asFile.absolutePath) })
    doFirst { testTmp.get().asFile.mkdirs() }
    testLogging {
        events("skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
