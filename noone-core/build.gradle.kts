plugins {
    `java-library`
    jacoco
    alias(libs.plugins.lombok)
}

group = "com.reajason.noone"
version = rootProject.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
    create("legacy8") {
        java.srcDirs("src/legacy8/java")
    }
    test {
        compileClasspath += sourceSets["legacy8"].output
        runtimeClasspath += sourceSets["legacy8"].output
    }
}

configurations {
    named("legacy8CompileOnly") {
        extendsFrom(configurations["compileOnly"])
    }
    named("legacy8Implementation") {
        extendsFrom(configurations["implementation"])
    }
}

dependencies {
    implementation(libs.byte.buddy)
    implementation(libs.asm.commons)
    implementation(libs.commons.lang3)
    implementation(libs.okhttp3)
    implementation(libs.fastjson2)
    implementation(libs.memshell.party.generator) {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.memshell.party.common)
    implementation(libs.memshell.party.thirdparty.tomcat)
    implementation(libs.dubbo) {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.dubbo.rpc.hessian)
    implementation(libs.dubbo.remoting.http)
    implementation(libs.reactor.netty.core)
    implementation(libs.spring.webflux)
    implementation(libs.spring.webmvc)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
    implementation(libs.jetbrains.annotations)
    implementation(libs.slf4j.api)
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")

    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.bundles.mockito)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.springframework:spring-test:5.3.24")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaCompile>("compileLegacy8Java") {
    javaCompiler.set(
        javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    )
}

tasks.named<JavaCompile>("compileJava") {
    // 直接把 legacy8 的编译输出追加到 classpath，不产生任务依赖循环
    classpath += sourceSets["legacy8"].output
}

tasks.named<Jar>("jar") {
    from(sourceSets["legacy8"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE  // EXCLUDE 优于 INCLUDE，保留先加入的
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
