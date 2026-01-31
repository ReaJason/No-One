plugins {
    java
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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-undertow")
    implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation(project(":noone-core"))
    implementation(libs.bundles.jjwt)
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.common)
    implementation(libs.memshell.party.packer)
    implementation(libs.byte.buddy)
    implementation(libs.asm.commons)
    implementation(libs.commons.lang3)
    implementation(libs.okhttp3)
    implementation(libs.fastjson2)
    implementation(libs.java.websocket)
    implementation(libs.jackson.annotations)
    implementation(libs.reactor.netty.core)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
    implementation(libs.jetbrains.annotations)
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
