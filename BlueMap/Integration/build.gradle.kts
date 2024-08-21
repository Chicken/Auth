plugins {
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "codes.antti.auth.bluemap"
version = "0.2.0"

repositories {
    mavenCentral()
    maven {
        setUrl ("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        setUrl ("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        setUrl ("https://jitpack.io/")
    }
}

dependencies {
    compileOnly ("org.spigotmc:spigot-api:1.13.2-R0.1-SNAPSHOT")
    compileOnly ("org.jetbrains:annotations:23.0.0")
    compileOnly ("com.github.BlueMap-Minecraft:BlueMapAPI:v2.5.1")
    implementation ("codes.antti.auth.common:Http")
    implementation ("com.google.code.gson:gson:2.10.1")
}

val javaTarget = 11
java {
	sourceCompatibility = JavaVersion.toVersion(javaTarget)
	targetCompatibility = JavaVersion.toVersion(javaTarget)
}

tasks.processResources {
	from("src/main/resources") {
		include("plugin.yml")
		duplicatesStrategy = DuplicatesStrategy.INCLUDE

		expand (
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

    relocate ("codes.antti.auth.common", "codes.antti.auth.bluemap.integration.shadow.common")
    relocate ("com.google.gson", "codes.antti.auth.bluemap.integration.shadow.gson")
}

tasks.register("publish") { }
