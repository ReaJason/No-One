plugins {
    `java-library`
}

group = "com.reajason.noone"
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(libs.memshell.party.generator)
    implementation(libs.reactor.netty.core)
    implementation(libs.spring.webmvc)
    implementation(libs.spring.webflux)
    implementation(libs.javax.servlet.api)
    implementation(libs.javax.websocket.api)
}
