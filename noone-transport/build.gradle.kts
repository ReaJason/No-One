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
    implementation(libs.dubbo) {
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.dubbo.rpc.hessian)
    implementation(libs.dubbo.remoting.http)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
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