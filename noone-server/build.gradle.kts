plugins {
    java
    jacoco
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    alias(libs.plugins.lombok)
}

group = "com.reajason.noone"
version = rootProject.version
description = "noone-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.processResources { filesMatching("**/application.yaml") { expand(project.properties) } }

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-undertow")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    implementation(project(":noone-core:java-core"))
    implementation(libs.bundles.jjwt)
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.common)
    implementation(libs.memshell.party.packer)
    implementation(libs.byte.buddy)
    implementation(libs.asm.commons)
    implementation(libs.commons.lang3)
    implementation(libs.okhttp3)
    implementation(libs.fastjson2)
    implementation(libs.jackson.annotations)
    implementation(libs.reactor.netty.core)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
    implementation(libs.jetbrains.annotations)
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("org.mapstruct.extensions.spring:mapstruct-spring-annotations:2.0.0")
    annotationProcessor("org.mapstruct.extensions.spring:mapstruct-spring-extensions:2.0.0")
    testImplementation("org.mapstruct.extensions.spring:mapstruct-spring-test-extensions:2.0.0")
    testAnnotationProcessor("org.mapstruct.extensions.spring:mapstruct-spring-test-extensions:2.0.0")

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("dev.samstevens.totp:totp:1.7.1")
}

tasks.withType<JacocoReport> {
    val coverageExclusions = listOf(
        "**/*MapperImpl.class",
        "org/mapstruct/extensions/spring/converter/**"
    )

    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) {
                exclude(coverageExclusions)
            }
        }))
    }

    doFirst {
        reports.html.outputLocation.orNull?.asFile?.let { delete(it) }
        reports.xml.outputLocation.orNull?.asFile?.let { delete(it) }
    }

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
