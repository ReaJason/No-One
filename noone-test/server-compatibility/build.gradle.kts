plugins {
    java
    jacoco
    alias(libs.plugins.lombok)
}

group = "com.reajason.noone"

dependencies {
    testImplementation(project(":noone-core"))
    testImplementation(project(":noone-transport"))
    testImplementation(libs.logback.classic)
    testImplementation(libs.fastjson2)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.hamcrest)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The Node.js test can run against the local node binary instead of Docker.
    // Include the switch in the cache key so both modes are cached separately.
    inputs.property("noOneTestNodeLocal", System.getenv("NO_ONE_TEST_NODE_LOCAL") ?: "false")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}