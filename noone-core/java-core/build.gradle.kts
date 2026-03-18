plugins {
    `java-library`
    jacoco
}

group = "com.reajason.noone"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.thirdparty.tomcat)
    implementation(libs.reactor.netty.core)
    implementation(libs.spring.webmvc)
    implementation(libs.spring.webflux)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JacocoReport> {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}
