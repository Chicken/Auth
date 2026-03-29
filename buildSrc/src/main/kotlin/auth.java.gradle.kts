plugins {
    java
    id("com.gradleup.shadow")
}

repositories {
    mavenCentral()
    maven("https://repo.bluecolored.de/releases")
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    compileOnly(libs.findLibrary("jetbrains.annotations").get())
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "utf-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
