import com.alibaba.fastjson2.JSONObject
import com.alibaba.fastjson2.JSONWriter
import com.reajason.javaweb.buddy.TargetJreVersionVisitorWrapper
import net.bytebuddy.ByteBuddy
import java.net.URLClassLoader
import java.util.Base64

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
    dependencies {
        classpath(libs.memshell.party.generator)
        classpath(libs.memshell.party.common)
        classpath(libs.fastjson2)
    }
}

// ── Plugin id → Java class mapping ─────────────────────────────────────
val javaPluginMapping = mapOf(
    "system-info" to "com.reajason.noone.plugin.SystemInfoCollector",
    "command-execute" to "com.reajason.noone.plugin.CommandExecutor",
    "file-manager" to "com.reajason.noone.plugin.FileManagerPlugin",
    "class-finder" to "com.reajason.noone.plugin.ClassFinder",
    "log-monitor" to "com.reajason.noone.plugin.LogMonitor",
    "port-scanner" to "com.reajason.noone.plugin.PortScanner",
    "task-manager" to "com.reajason.noone.plugin.TaskManager",
)

// ── Resolve all paths at configuration time (configuration cache safe) ──
val releaseDir = layout.projectDirectory.dir("release")
val nodejsPluginsDir = layout.projectDirectory.dir("nodejs-plugins")
val dotnetBuildDir = layout.projectDirectory.dir("dotnet-plugins/build")
val serverPluginsDir = layout.projectDirectory.dir("../noone-server/src/main/resources/plugins")

val javaPluginsProject = project(":noone-plugins:java-plugins")

// ── generateRelease task ────────────────────────────────────────────────
tasks.register("generateRelease") {
    group = "build"
    description = "Regenerate payload in all release JSON files (Java, Node.js, .NET) and sync to server resources"
    dependsOn(":noone-plugins:java-plugins:classes")

    // Capture all values at configuration time
    val javaReleaseDirFile = releaseDir.dir("java").asFile
    val nodejsReleaseDirFile = releaseDir.dir("nodejs").asFile
    val dotnetReleaseDirFile = releaseDir.dir("dotnet").asFile
    val nodejsPluginsDirFile = nodejsPluginsDir.asFile
    val dotnetBuildDirFile = dotnetBuildDir.asFile
    val releaseDirFile = releaseDir.asFile
    val serverPluginsDirFile = serverPluginsDir.asFile
    val classesFiles = javaPluginsProject.the<SourceSetContainer>()["main"].output.classesDirs.files
    val cpFiles = javaPluginsProject.configurations["runtimeClasspath"].files
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

        // ── Sync release JSON to server resources ────────────────────
        logger.lifecycle("Syncing release JSON files to server resources...")
        serverPluginsDirFile.mkdirs()
        releaseDirFile.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            subDir.listFiles { f -> f.extension == "json" }?.forEach { file ->
                file.copyTo(serverPluginsDirFile.resolve(file.name), overwrite = true)
                logger.lifecycle("  ✓ Synced: ${file.name}")
            }
        }

        logger.lifecycle("Release generation complete.")
    }
}
