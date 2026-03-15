plugins {
    java
    `java-library`
    id("java-library")
}

group = "codes.antti.auth.common"
version = "0.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    implementation("com.google.code.gson:gson:2.10.1")
}

val javaTarget = 21
java {
    sourceCompatibility = JavaVersion.toVersion(javaTarget)
    targetCompatibility = JavaVersion.toVersion(javaTarget)
}

tasks.withType(JavaCompile::class).configureEach {
    options.apply {
        encoding = "utf-8"
    }
}

tasks.withType(AbstractArchiveTask::class).configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
