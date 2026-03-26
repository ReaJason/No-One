plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    alias(libs.plugins.lombok)
}

group = "com.reajason.noone"
version = rootProject.version
description = "noone-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.processResources { filesMatching("application*.yaml") { expand(project.properties) } }

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":noone-core")){
        exclude(group = "io.github.reajason", module = "thirdparty-tomcat")
    }
    implementation(project(":noone-transport"))
    implementation(libs.bundles.jjwt)
    implementation(libs.memshell.party.generator) {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "io.github.reajason", module = "thirdparty-tomcat")
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.memshell.party.common)
    implementation(libs.memshell.party.packer){
        exclude(group = "commons-logging", module = "commons-logging")
    }
    runtimeOnly("org.postgresql:postgresql")
    implementation(libs.byte.buddy)
    implementation(libs.asm.commons)
    implementation(libs.commons.lang3)
    implementation(libs.fastjson2)
    implementation(libs.reactor.netty.core)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
    implementation(libs.jetbrains.annotations)
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("org.mapstruct.extensions.spring:mapstruct-spring-annotations:2.0.0")
    annotationProcessor("org.mapstruct.extensions.spring:mapstruct-spring-extensions:2.0.0")
    testImplementation("org.mapstruct.extensions.spring:mapstruct-spring-test-extensions:2.0.0")
    testAnnotationProcessor("org.mapstruct.extensions.spring:mapstruct-spring-test-extensions:2.0.0")
    implementation("nl.basjes.parse.useragent:yauaa:8.1.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.bundles.testcontainers) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
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

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.test {
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}
