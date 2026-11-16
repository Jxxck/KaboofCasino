import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "de.tigges"
version = "0.20.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// The version Paper 26.3 loads itself (META-INF/libraries.list of the server jar): provided at runtime, never shaded.
val sqliteJdbc = "org.xerial:sqlite-jdbc:3.49.1.0"

// The game-port pack delivery (pack.serve: minecraft-port) uses netty and two classes that exist only in the Paper
// server jar (io.papermc.paper.network.ChannelInitializeListener and its Holder). Netty comes from Maven Central in the
// version Paper 26.3 ships; the two classes are compile-only stand-ins in src/paperStubs (signatures only). Neither is
// packaged: at runtime the server provides both.
val nettyVersion = providers.gradleProperty("casino.nettyVersion").get()
val paperApi = "io.papermc.paper:paper-api:26.3.build.205-stable"
val paperStubs: SourceSet = sourceSets.create("paperStubs")

/** The pack tool (subproject) and its runtime dependencies, resolved for the buildPack task. */
val packTool: Configuration = configurations.create("packTool") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(paperApi)
    // Tests use API value types (Component, Material keys, YamlConfiguration), never a running server.
    testImplementation(paperApi)

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
    "paperStubsCompileOnly"(paperApi)
    "paperStubsCompileOnly"("io.netty:netty-transport:$nettyVersion")
    compileOnly(paperStubs.output)
    compileOnly("io.netty:netty-transport:$nettyVersion")
    testImplementation(paperStubs.output)
    testImplementation("io.netty:netty-transport:$nettyVersion")
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
// The pack is built deterministically from the MineVegas Casino jar (read as a zip, never executed) plus pack-src/,
// then embedded into the plugin jar under pack/. Same inputs -> same bytes -> same SHA-1, so clients keep their cached
// copy. The same task writes the optional music pack (KaboofCasino-music.zip + .sha1, the mod's six lounge tracks,
// ~22 MB) next to it; it is embedded the same way (pack/KaboofCasino-music.zip) and offered as a second, non-required
// pack.
//
// The mod jar is not in the repository: fetchModJar downloads it once (casino.modJarUrl) into casino.modJar and
// checks its SHA-1. A jar that is already there with the right SHA-1 is used as it is, so the build also works
// offline after dropping the file into libs/ by hand.
val modJarSha1 = providers.gradleProperty("casino.modJarSha1")

abstract class FetchModJar : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha1: Property<String>

    @get:Internal
    abstract val offline: Property<Boolean>

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun fetch() {
        val file = target.get().asFile
        if (file.isFile && sha1Of(file) == sha1.get().lowercase()) {
            return
        }
        val manual = "Download MineVegas Casino 1.1.0 (Fabric, Minecraft 26.3) from " +
            "https://www.curseforge.com/minecraft/mc-mods/minevegas-casino and save it as " + file
        if (file.isFile) {
            throw GradleException("$file has SHA-1 ${sha1Of(file)}, expected ${sha1.get()}. Delete it, or: $manual")
        }
        if (offline.get()) {
            throw GradleException("$file is missing and Gradle runs offline. $manual")
        }
        file.parentFile.mkdirs()
        val part = File(file.parentFile, file.name + ".part")
        try {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()
            val request = HttpRequest.newBuilder(URI.create(url.get()))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "KaboofCasino-build")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(part.toPath()))
            if (response.statusCode() != 200) {
                throw GradleException("${url.get()} answered HTTP ${response.statusCode()}. $manual")
            }
            val actual = sha1Of(part)
            if (actual != sha1.get().lowercase()) {
                throw GradleException("${url.get()} delivered SHA-1 $actual, expected ${sha1.get()}. $manual")
            }
            Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            logger.lifecycle("Downloaded {} ({} bytes)", file.name, file.length())
        } catch (e: IOException) {
            throw GradleException("Cannot download ${url.get()}: $e. $manual", e)
        } finally {
            part.delete()
        }
    }

    private fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) {
                    break
                }
                digest.update(buffer, 0, n)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}

val fetchModJar = tasks.register<FetchModJar>("fetchModJar") {
    group = "build"
    description = "Downloads the MineVegas Casino jar the resource pack is built from (SHA-1 checked)."
    url = providers.gradleProperty("casino.modJarUrl")
    sha1 = modJarSha1
    offline = gradle.startParameter.isOffline
    target = layout.projectDirectory.file(providers.gradleProperty("casino.modJar"))
}
val modJar = fetchModJar.flatMap { it.target }
val packSrc = layout.projectDirectory.dir("pack-src")
val packOut = layout.buildDirectory.dir("pack")

val buildPack = tasks.register<JavaExec>("buildPack") {
    group = "build"
    description = "Builds the KaboofCasino resource packs (one per language, build/pack/en and build/pack/de: zip, " +
        ".sha1, gui-layout.json, guide files) and the optional music pack into build/pack."
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
    // the licences travel with the jar: the plugin's own, the mod's (required by it) and the notices
    from(layout.projectDirectory.file("LICENSE"))
    from(layout.projectDirectory.file("LICENSE-MineVegas.txt"))
    from(layout.projectDirectory.file("NOTICE.md"))
    from(layout.projectDirectory.file("licenses/Apache-2.0.txt")) { into("META-INF/licenses") }
    // the jukebox catalog: the pack tool builds the sound events from the same file
    from(layout.projectDirectory.file("pack-src/jukebox/tracks.json")) { into("jukebox") }
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

// Optional: a Paper server jar (-Pcasino.paperServerJar=.../versions/26.3/paper-26.3.jar, as a started server
// unpacks it) lets PaperStubsTest compare src/paperStubs with the real classes. Read only.
val paperServerJar = providers.gradleProperty("casino.paperServerJar")

/** What both test tasks share; {@code language} is the casino language the tests run in. */
fun Test.casinoTests(language: Provider<String>) {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // fixtures read the mod's own files (lang, blockstates, guide) straight from the jar
    inputs.file(modJar).withPathSensitivity(PathSensitivity.NONE).withPropertyName("modJar")
    inputs.property("paperServerJar", paperServerJar.orElse(""))
    inputs.property("language", language)
    val jarForTests = modJar
    val serverJar = paperServerJar
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-Dkaboofcasino.modJar=" + jarForTests.get().asFile.absolutePath,
            "-Dkaboofcasino.paperServerJar=" + serverJar.getOrElse(""),
            "-Dkaboofcasino.language=" + language.get(),
            // weeks and days are calculated in a fixed zone, whatever machine the tests run on
            "-Dkaboofcasino.time-zone=Europe/Berlin",
        )
    })
    val testTmp = layout.buildDirectory.dir("tmp/test-tmp")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.io.tmpdir=" + testTmp.get().asFile.absolutePath) })
    doFirst { testTmp.get().asFile.mkdirs() }
    testLogging {
        events("skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// The whole suite runs in German: most tests compare with the mod's German texts and the German pack, as they did
// while the plugin was German only. -Pcasino.testLanguage=en runs it in English instead (the tests tagged "german"
// then fail by design).
tasks.test {
    useJUnitPlatform()
    casinoTests(providers.gradleProperty("casino.testLanguage").orElse("de"))
}

// The same tests against the English texts and the English pack: every menu, title and preview is laid out again with
// the English wording. Left out are the tests that assert German wording (tag "german") and the long simulations and
// geometry sweeps, which do not depend on the language.
val testEnglish = tasks.register<Test>("testEnglish") {
    group = "verification"
    description = "Runs the tests against the English texts and the English resource pack."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        excludeTags("german")
    }
    exclude("**/*RtpSimulationTest*", "**/*GeometryTest*", "**/DuckDerbyRaceTest*")
    casinoTests(providers.provider { "en" })
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(testEnglish)
}
