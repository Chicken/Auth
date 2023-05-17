plugins {
    java
    `java-library`
    id ("java-library")
}

group = "codes.antti.auth"
version = "0.0.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly ("org.jetbrains:annotations:23.0.0")
    implementation ("org.xerial:sqlite-jdbc:3.41.2.1")
}

val javaTarget = 11
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
