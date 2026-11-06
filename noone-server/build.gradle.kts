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

val webDir = rootProject.layout.projectDirectory.dir("noone-web")
val webBuildDir = webDir.dir("build/client")

val webInstall = tasks.register<Exec>("webInstall") {
    group = "frontend"
    description = "Installs frontend dependencies with Bun"
    workingDir = webDir.asFile
    commandLine("bun", "install", "--frozen-lockfile")
    inputs.files(webDir.files("package.json", "bun.lock"))
    outputs.dir(webDir.dir("node_modules"))
}

val webBuild = tasks.register<Exec>("webBuild") {
    group = "frontend"
    description = "Builds the React Router SPA"
    dependsOn(webInstall)
    workingDir = webDir.asFile
    commandLine("bun", "run", "build")
    inputs.files(
        webDir.files(
            "package.json",
            "bun.lock",
            "react-router.config.ts",
            "tsconfig.json",
            "vite.config.ts"
        )
    )
    inputs.dir(webDir.dir("app"))
    inputs.dir(webDir.dir("public"))
    outputs.dir(webBuildDir)
}

tasks.processResources {
    dependsOn(webBuild)
    from(webBuildDir) { into("static") }
    filesMatching("application*.yaml") { expand(project.properties) }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    implementation {
        // Signature stubs are needed to read/generate templates, but must not shadow embedded Tomcat.
        exclude(group = "io.github.reajason", module = "memshell-party-stub-tomcat")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":noone-core"))
    implementation(project(":noone-transport"))
    implementation(libs.bundles.jjwt)
    implementation(platform(libs.memshell.party.bom))
    implementation(platform(libs.memshell.party.server.all))
    implementation(platform(libs.memshell.party.shelltool.all))
    implementation(libs.memshell.party.core)
    implementation(libs.memshell.party.packer) {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.hibernate.orm:hibernate-community-dialects")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
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
