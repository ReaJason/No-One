import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper
import net.bytebuddy.ByteBuddy
import java.net.URLClassLoader
import java.util.Base64

plugins {
    id("java")
}

group = "com.reajason.javaweb"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

buildscript {
    dependencies {
        classpath(libs.memshell.party.generator)
        classpath(libs.memshell.party.common)
        classpath(libs.fastjson2)
    }
}

dependencies {
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.common)
    testImplementation(libs.fastjson2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// ── Plugin id → Java class mapping ─────────────────────────────────────
val javaPluginMapping = mapOf(
    "system-info" to "com.reajason.noone.plugin.SystemInfoCollector",
    "command-execute" to "com.reajason.noone.plugin.CommandExecutor",
    "file-manager" to "com.reajason.noone.plugin.FileManagerPlugin",
    "class-finder" to "com.reajason.noone.plugin.ClassFinder",
)

// ── Resolve all paths at configuration time (configuration cache safe) ──
val releaseDir = layout.projectDirectory.dir("../release")
val nodejsPluginsDir = layout.projectDirectory.dir("../nodejs-plugins")
val dotnetBuildDir = layout.projectDirectory.dir("../dotnet-plugins/build")
val classesDirs = sourceSets["main"].output.classesDirs
val runtimeCp = configurations["runtimeClasspath"]

// ── generateRelease task ────────────────────────────────────────────────
tasks.register("generateRelease") {
    group = "build"
    description = "Regenerate payload in all release JSON files (Java, Node.js, .NET)"
    dependsOn(tasks.classes)

    // Capture all values at configuration time
    val javaReleaseDirFile = releaseDir.dir("java").asFile
    val nodejsReleaseDirFile = releaseDir.dir("nodejs").asFile
    val dotnetReleaseDirFile = releaseDir.dir("dotnet").asFile
    val nodejsPluginsDirFile = nodejsPluginsDir.asFile
    val dotnetBuildDirFile = dotnetBuildDir.asFile
    val classesFiles = classesDirs.files
    val cpFiles = runtimeCp.files
    val mapping = javaPluginMapping

    doLast {
        // Helper: update payload in a JSON file preserving the original formatting
        fun updatePayload(file: java.io.File, newPayload: String) {
            val json = JSONObject.parseObject(file.readText())
            json["payload"] = newPayload
            file.writeText(
                json.toJSONString(JSONWriter.Feature.PrettyFormat)
                    .replace("\t", "  ")
                    .replace(Regex("""(?<=[\[{,])\n\s{2}"""), "\n  ")
                + "\n"
            )
        }

        // Helper: collapse multi-line JS to single-line
        fun collapseJs(source: String): String {
            return source.replace(Regex("\\r\\n?"), "\n")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }

        // ── Java plugins ─────────────────────────────────────────────
        if (javaReleaseDirFile.isDirectory) {
            val classLoader = URLClassLoader(
                (classesFiles.map { it.toURI().toURL() } +
                    cpFiles.map { it.toURI().toURL() }).toTypedArray(),
                Thread.currentThread().contextClassLoader
            )

            javaReleaseDirFile.listFiles { f -> f.extension == "json" }?.forEach { file ->
                val json = JSONObject.parseObject(file.readText())
                val pluginId = json.getString("id") ?: return@forEach
                val className = mapping[pluginId]
                if (className == null) {
                    logger.warn("No Java class mapping for plugin id '$pluginId', skipping ${file.name}")
                    return@forEach
                }

                try {
                    val targetClass = classLoader.loadClass(className)
                    @Suppress("UNCHECKED_CAST")
                    val bytes = ByteBuddy().redefine(targetClass as Class<Any>)
                        .visit(TargetJreVersionVisitorWrapper.DEFAULT)
                        .make().bytes
                    val payload = Base64.getEncoder().encodeToString(bytes)
                    updatePayload(file, payload)
                    logger.lifecycle("  ✓ Java: ${file.name}")
                } catch (e: Exception) {
                    logger.error("  ✗ Java: ${file.name} — ${e.message}")
                }
            }
        }

        // ── Node.js plugins ──────────────────────────────────────────
        if (nodejsReleaseDirFile.isDirectory) {
            nodejsReleaseDirFile.listFiles { f -> f.extension == "json" }?.forEach { file ->
                val json = JSONObject.parseObject(file.readText())
                val pluginId = json.getString("id") ?: return@forEach
                val sourceFile = nodejsPluginsDirFile.resolve("$pluginId.mjs")
                if (!sourceFile.isFile) {
                    logger.warn("  ⚠ Node.js: source file ${sourceFile.name} not found, skipping ${file.name}")
                    return@forEach
                }

                try {
                    val payload = collapseJs(sourceFile.readText())
                    updatePayload(file, payload)
                    logger.lifecycle("  ✓ Node.js: ${file.name}")
                } catch (e: Exception) {
                    logger.error("  ✗ Node.js: ${file.name} — ${e.message}")
                }
            }
        }

        // ── .NET plugins ─────────────────────────────────────────────
        if (dotnetReleaseDirFile.isDirectory) {
            dotnetReleaseDirFile.listFiles { f -> f.extension == "json" }?.forEach { file ->
                val json = JSONObject.parseObject(file.readText())
                val pluginId = json.getString("id") ?: return@forEach
                val base64File = dotnetBuildDirFile.resolve("$pluginId.base64")
                if (!base64File.isFile) {
                    logger.warn("  ⚠ .NET: ${base64File.name} not found (run 'dotnet build' first), skipping ${file.name}")
                    return@forEach
                }

                try {
                    val payload = base64File.readText().trim()
                    updatePayload(file, payload)
                    logger.lifecycle("  ✓ .NET: ${file.name}")
                } catch (e: Exception) {
                    logger.error("  ✗ .NET: ${file.name} — ${e.message}")
                }
            }
        }

        logger.lifecycle("Release generation complete.")
    }
}
