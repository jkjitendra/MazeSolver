import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar

plugins {
    application
    // Shadow plugin that supports Gradle 9
    id("com.gradleup.shadow") version "9.1.0"
}

group = "com.drostwades"
version = "1.0.0"

repositories { mavenCentral() }

dependencies {
    // --- JUnit 5 stack ---
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Add AssertJ for fluent assertions
    testImplementation("org.assertj:assertj-core:3.27.0")
}

application {
    // Fully-qualified main class
    mainClass.set("com.drostwades.mazesolver.MazeSolverUI")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
}

/** Configure the fat jar and set Main-Class */
tasks.named<Jar>("shadowJar") {
    archiveClassifier.set("") // build/libs/MazeSolver-1.0.0.jar
    manifest { attributes["Main-Class"] = application.mainClass.get() }
}

/** Gradle 9 fix: declare explicit dependencies so distribution tasks use the fat jar */
// The Shadow plugin’s startShadowScripts reads from :jar
tasks.named("startShadowScripts") {
    dependsOn(tasks.named("jar"))
}

// If you build the “shadow” distributions, ensure they run after the fat jar:
tasks.named("shadowDistZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("shadowDistTar") {
    dependsOn(tasks.named("shadowJar"))
}

// If you also build the standard application distributions, make them use the fat jar first:
tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}



/**
 * jpackage helpers — make native, self-contained apps per OS.
 * These tasks call the JDK's `jpackage` tool directly (no extra plugins).
 *
 * Results will go to: build/dist/<OS>/
 */


val appName = "MazeSolver"
val vendor = "DrostWades"        // any vendor name
val appVersion = project.version.toString()
val mainJar = "${project.name}-${project.version}.jar"
val iconsDir = layout.projectDirectory.dir("packaging/icons")
val distBase = layout.buildDirectory.dir("dist")

// Where's the java home for jpackage?
val javaHome = System.getProperty("java.home")
val jpackageBin = if (OperatingSystem.current().isWindows) {
    "${javaHome}\\bin\\jpackage.exe"
} else {
    "${javaHome}/bin/jpackage"
}

/** Build the fat jar first */
val packageInputs = tasks.register("packageInputs") {
    dependsOn(tasks.named("shadowJar"))
}

/** Common jpackage args for a non-modular (classpath) app */
fun commonArgs(outputDir: String): List<String> = listOf(
    "--name", appName,
    "--app-version", appVersion,
    "--vendor", vendor,
    "--input", "${layout.buildDirectory.get().asFile}/libs",
    "--main-jar", mainJar,
    "--main-class", application.mainClass.get(),
    // Make a self-contained runtime (no external Java required)
    "--runtime-image", "${layout.buildDirectory.get().asFile}/runtime",
    "--dest", outputDir
)

// (re)create the minimal Java runtime with jlink
// --- paths used by jlink/jpackage ---
val runtimeDir = layout.buildDirectory.dir("runtime").get().asFile

tasks.register<Exec>("createRuntime") {
    // Build the fat jar first so the app is ready to package
    dependsOn("shadowJar")

    // Make Gradle understand what this task outputs (better incremental behavior)
    outputs.dir(runtimeDir)

    // Always remove the old runtime image before invoking jlink
    doFirst {
        project.delete(runtimeDir)
    }

    val jlink = if (OperatingSystem.current().isWindows) {
        "${System.getProperty("java.home")}\\bin\\jlink.exe"
    } else {
        "${System.getProperty("java.home")}/bin/jlink"
    }

    commandLine(
        jlink,
        "--no-header-files", "--no-man-pages", "--strip-debug", "--compress=2",
        "--add-modules", "java.base,java.desktop,java.logging,jdk.crypto.ec",
        "--output", runtimeDir.absolutePath
    )
}

// ------- macOS (.dmg) -------
tasks.register<Exec>("jpackageMac") {
    onlyIf { OperatingSystem.current().isMacOsX }
    dependsOn("createRuntime")
    val outDir = distBase.get().dir("mac").asFile.absolutePath
    val args = mutableListOf<String>().apply {
        add(jpackageBin)
        addAll(commonArgs(outDir))
        addAll(listOf("--type", "dmg"))
        val icns = iconsDir.file("mathmaze.icns").asFile
        if (icns.exists()) addAll(listOf("--icon", icns.absolutePath))
    }
    commandLine(args)
}

// ------- Windows (.msi) ---- (requires WiX toolset in PATH) -------
tasks.register<Exec>("jpackageWin") {
    onlyIf { OperatingSystem.current().isWindows }
    dependsOn("createRuntime")
    val outDir = distBase.get().dir("win").asFile.absolutePath
    val args = mutableListOf<String>().apply {
        add(jpackageBin)
        addAll(commonArgs(outDir))
        addAll(listOf("--type", "msi", "--win-dir-chooser", "--win-menu", "--win-shortcut"))
        val ico = iconsDir.file("mathmaze.ico").asFile
        if (ico.exists()) addAll(listOf("--icon", ico.absolutePath))
    }
    commandLine(args)
}

/* ---- Linux .deb ---- */
tasks.register<Exec>("jpackageDeb") {
    onlyIf { OperatingSystem.current().isLinux }
    dependsOn("createRuntime")
    val outDir = distBase.get().dir("linux").asFile.absolutePath
    val args = mutableListOf<String>().apply {
        add(jpackageBin)
        addAll(commonArgs(outDir))
        addAll(listOf("--type", "deb"))
        val png = iconsDir.file("mathmaze.png").asFile
        if (png.exists()) addAll(listOf("--icon", png.absolutePath))
    }
    commandLine(args)
}

/* ---- Linux .rpm ---- */
tasks.register<Exec>("jpackageRpm") {
    onlyIf { OperatingSystem.current().isLinux }
    dependsOn("createRuntime")
    val outDir = distBase.get().dir("linux").asFile.absolutePath
    val args = mutableListOf<String>().apply {
        add(jpackageBin)
        addAll(commonArgs(outDir))
        addAll(listOf("--type", "rpm"))
        val png = iconsDir.file("mathmaze.png").asFile
        if (png.exists()) addAll(listOf("--icon", png.absolutePath))
    }
    commandLine(args)
}

/** Convenience task that runs the right jpackage for the current OS */
tasks.register("packageCurrentOS") {
    dependsOn(
        when {
            OperatingSystem.current().isWindows -> "jpackageWin"
            OperatingSystem.current().isMacOsX  -> "jpackageMac"
            OperatingSystem.current().isLinux   -> "jpackageDeb"
            else -> "createRuntime"
        }
    )
}

// Prints the project version with no extra text (used by CI)
tasks.register("printVersion") {
    doLast { println(project.version.toString()) }
}