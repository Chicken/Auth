plugins {
    java
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
    maven("https://repo.bluecolored.de/releases")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "utf-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
