plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "codes.antti.auth"
version = "0.3.0"

repositories {
    mavenCentral()
    maven {
        setUrl("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        setUrl("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("org.jetbrains:annotations:23.0.0")
    implementation("codes.antti.auth.common:Http")
}

val javaTarget = 21
java {
    sourceCompatibility = JavaVersion.toVersion(javaTarget)
    targetCompatibility = JavaVersion.toVersion(javaTarget)
}

tasks.processResources {
    from("src/main/resources") {
        include("plugin.yml")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        expand(
            "version" to project.version
        )
    }
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

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    destinationDirectory.set(file("../build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.authorization.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authorization.shadow.gson")
}
