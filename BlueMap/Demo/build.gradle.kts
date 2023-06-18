plugins {
    java
}

group = "codes.antti.auth.bluemap"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        setUrl ("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        setUrl ("https://oss.sonatype.org/content/groups/public/")
    }
}

dependencies {
    compileOnly ("org.spigotmc:spigot-api:1.13.2-R0.1-SNAPSHOT")
    compileOnly ("net.luckperms:api:5.4")
    compileOnly ("org.jetbrains:annotations:23.0.0")
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

tasks.jar {
    destinationDirectory.set(file("../../build"))
    archiveClassifier.set("")
}

tasks.register("publish") { }
