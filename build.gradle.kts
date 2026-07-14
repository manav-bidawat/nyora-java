import java.io.File as JFile
import java.util.zip.ZipFile

// Standalone JVM SDK: compiles the native kotatsu-parsers engine (nyora-shared
// JVM sources) plus a Java-ergonomic facade (com.nyora.hasan72341.sdk) into a
// publishable library. In-process, no HTTP, no cloud.
plugins {
    kotlin("multiplatform")        version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("app.cash.sqldelight")      version "2.1.0"
    `maven-publish`
}

group = "com.nyora"
version = "2.1.0"

// Engine source lives in the sibling nyora-shared checkout.
val sharedSrc: String = "${projectDir}/../nyora-shared/src"

sqldelight {
    databases {
        create("NyoraDatabase") {
            packageName.set("com.nyora.hasan72341.shared.db")
            srcDirs.from(file("$sharedSrc/commonMain/sqldelight"))
        }
    }
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs("$sharedSrc/commonMain/kotlin")
            resources.srcDirs("$sharedSrc/commonMain/resources")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("app.cash.sqldelight:runtime:2.1.0")
                implementation("app.cash.sqldelight:coroutines-extensions:2.1.0")
            }
        }
        val jvmMain by getting {
            // ADD the SDK facade sources alongside the shared engine sources.
            kotlin.srcDirs("$sharedSrc/jvmMain/kotlin", "src/jvmMain/kotlin")
            resources.srcDirs("$sharedSrc/jvmMain/resources")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("app.cash.sqldelight:sqlite-driver:2.1.0")
                // OkHttp 5 — aligned with kotatsu-parsers-redo.
                implementation("com.squareup.okhttp3:okhttp:5.1.0")
                implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.1.0")
                implementation("org.jsoup:jsoup:1.21.2")
                // Native Kotatsu parsers (the in-process engine).
                implementation("com.github.clquwu:kotatsu-parsers-redo:59c033ecfd")
                implementation("org.json:json:20240303")
            }
        }
    }
}

// --- Optional fat JAR (drop-in: library + runnable HelperMain server) ---

val mergedServicesDir = layout.buildDirectory.dir("merged-services").map { it.asFile }

tasks.register("mergeServiceFiles") {
    group = "build"
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    inputs.files(jvmMain.runtimeDependencyFiles)
    outputs.dir(mergedServicesDir)
    doLast {
        val outDir = mergedServicesDir.get()
        outDir.deleteRecursively()
        val servicesOut = JFile(outDir, "META-INF/services").apply { mkdirs() }
        val accum = mutableMapOf<String, StringBuilder>()
        jvmMain.runtimeDependencyFiles!!
            .filter { it.isFile && it.name.endsWith(".jar") }
            .forEach { jar ->
                ZipFile(jar).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.isDirectory &&
                            entry.name.startsWith("META-INF/services/") &&
                            entry.name.length > "META-INF/services/".length
                        ) {
                            val key = entry.name.removePrefix("META-INF/services/")
                            val text = zf.getInputStream(entry).bufferedReader().readText()
                            accum.getOrPut(key) { StringBuilder() }.append(text).append('\n')
                        }
                    }
                }
            }
        accum.forEach { (name, content) -> JFile(servicesOut, name).writeText(content.toString()) }
    }
}

tasks.register<Jar>("sdkJar") {
    group = "build"
    description = "Fat JAR: the SDK + engine + all deps (also runnable as the helper server)."
    archiveBaseName.set("nyora-java")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to "com.nyora.hasan72341.shared.HelperMain",
            "Multi-Release" to "true",
        )
    }
    val jvmMain = kotlin.targets.getByName("jvm").compilations.getByName("main")
    dependsOn(jvmMain.compileTaskProvider, "jvmProcessResources", "mergeServiceFiles")
    from(jvmMain.output.allOutputs, mergedServicesDir)
    from(provider {
        jvmMain.runtimeDependencyFiles!!
            .filter { it.isDirectory || it.name.endsWith(".jar") }
            .map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude(
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC",
            "META-INF/MANIFEST.MF", "META-INF/LICENSE", "META-INF/LICENSE.txt",
            "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/services/**",
            "module-info.class",
        )
    }
}

// --- Publishing (Maven Local by default; GitHub Packages when env is set) ---

publishing {
    repositories {
        val ghUser = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR"))
        val ghKey = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN"))
        if (ghUser.isPresent && ghKey.isPresent) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Hasan72341/nyora-java")
                credentials {
                    username = ghUser.get()
                    password = ghKey.get()
                }
            }
        }
    }
    // The Kotlin Multiplatform plugin registers the `kotlinMultiplatform` +
    // `jvm` publications automatically; add POM metadata to each.
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("nyora-java")
            description.set("In-process Nyora sources SDK for the JVM — the native kotatsu-parsers engine (960+ manga/manhwa/manhua sources) as a library. No HTTP, no cloud.")
            url.set("https://github.com/Hasan72341/nyora-java")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
        }
    }
}
