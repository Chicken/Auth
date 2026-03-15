plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "codes.antti.auth.bluemap"
version = "0.3.0"

repositories {
    mavenCentral()
    maven {
        setUrl("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        setUrl("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        setUrl("https://jitpack.io/")
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("com.github.BlueMap-Minecraft:BlueMapAPI:v2.5.1")
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")
    implementation("codes.antti.auth.common:Http")
    implementation("com.google.code.gson:gson:2.10.1")
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
    destinationDirectory.set(file("../../build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.chat.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.chat.shadow.gson")
}
