pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven("https://www.jitpack.io")
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        maven("https://www.jitpack.io")
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

rootProject.name = "noone"
include("noone-server")
include("noone-vul:vul-webapp")
include("noone-vul:vul-webapp-jakarta")

include("noone-core")