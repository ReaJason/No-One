plugins {
    `java-library`
    jacoco
    alias(libs.plugins.lombok)
}

group = "com.reajason.noone"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.compileTestJava {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}


dependencies {
    implementation(libs.okhttp3)
    // Alibaba Dubbo 2.x MUST be declared before Apache Dubbo 3.x so that
    // the real com.alibaba.dubbo.* classes take precedence over the incomplete
    // backward-compatibility shim baked into org.apache.dubbo:dubbo
    implementation(libs.alibaba.dubbo) {
        exclude(group = "io.netty")
        exclude(group = "org.javassist")
    }
    // Alibaba Dubbo 2.x SPI eagerly loads HttpProtocol which depends on Spring Remoting;
    // implementation (not runtimeOnly) so ProxyHttpProtocol can compile against Spring HTTP invoker types.
    implementation(libs.spring.web)
    implementation(libs.alibaba.dubbo.rpc.hessian) {
        exclude(group = "io.netty")
    }
    implementation(libs.dubbo) {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.dubbo.rpc.hessian)
    implementation(libs.dubbo.remoting.http)
    implementation(libs.jsonrpc4j)
    implementation(libs.jackson.databind)
    implementation(libs.netty.handler.proxy)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.math=ALL-UNNAMED",
        "--add-opens", "java.base/java.net=ALL-UNNAMED"
    )
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    finalizedBy(tasks.named("jacocoTestReport"))
}