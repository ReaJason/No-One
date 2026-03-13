plugins {
    id("java")
}

group = "com.reajason.javaweb"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

buildscript {
    dependencies {
        classpath(libs.memshell.party.generator)
        classpath(libs.memshell.party.common)
    }
}

dependencies {
    implementation(libs.memshell.party.generator)
    implementation(libs.memshell.party.common)
    testImplementation(libs.fastjson2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
}
