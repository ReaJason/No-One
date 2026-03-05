import org.gradle.api.tasks.testing.Test

plugins {
    id("java")
}

group = "com.reajason.javaweb"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // Depend on java-plugins for PluginRunner + compiled plugin bytecodes
    implementation(project(":noone-plugins:java-plugins"))
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.common)

    testImplementation(libs.fastjson2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.bundles.testcontainers)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("docker-jdk-matrix", "docker-node-matrix", "docker-dotnet-matrix")
    }
}

// ── Docker JDK Matrix Test (migrated from java-plugins) ─────────────
tasks.register<Test>("dockerJdkMatrixTest") {
    group = "verification"
    description = "Run Java plugin compatibility smoke tests in Docker JDK matrix (Testcontainers)"
    useJUnitPlatform {
        includeTags("docker-jdk-matrix")
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    listOf(
        "noone.test.docker.caseFilter",
        "noone.test.docker.platform"
    ).forEach { key ->
        System.getProperty(key)?.let { value ->
            systemProperty(key, value)
        }
    }
    shouldRunAfter(tasks.test)
}

// ── Docker Node.js Matrix Test ──────────────────────────────────────
tasks.register<Test>("dockerNodeMatrixTest") {
    group = "verification"
    description = "Run Node.js plugin compatibility smoke tests in Docker Node.js LTS matrix (Testcontainers)"
    useJUnitPlatform {
        includeTags("docker-node-matrix")
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    listOf(
        "noone.test.docker.node.caseFilter"
    ).forEach { key ->
        System.getProperty(key)?.let { value ->
            systemProperty(key, value)
        }
    }
    shouldRunAfter(tasks.test)
}

// ── Docker .NET Matrix Test ─────────────────────────────────────────
tasks.register<Test>("dockerDotnetMatrixTest") {
    group = "verification"
    description = "Run .NET plugin compatibility smoke tests in Docker .NET runtime matrix (Testcontainers)"
    useJUnitPlatform {
        includeTags("docker-dotnet-matrix")
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    listOf(
        "noone.test.docker.dotnet.caseFilter"
    ).forEach { key ->
        System.getProperty(key)?.let { value ->
            systemProperty(key, value)
        }
    }
    shouldRunAfter(tasks.test)
}

// ── Run all Docker matrix tests ─────────────────────────────────────
tasks.register("dockerAllMatrixTest") {
    group = "verification"
    description = "Run all plugin Docker matrix tests (JDK + Node.js + .NET)"
    dependsOn("dockerJdkMatrixTest", "dockerNodeMatrixTest", "dockerDotnetMatrixTest")
}
