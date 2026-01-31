plugins {
    java
    idea
}

idea {
    module {
        excludeDirs.add(file("src"))
    }
}

version = "0.0.1-SNAPSHOT"