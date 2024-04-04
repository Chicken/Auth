plugins {
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
    id ("com.modrinth.minotaur") version "2.+"
}

group = "codes.antti.auth"
version = "0.2.0"

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
    implementation ("codes.antti.auth.common:Http")
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
    destinationDirectory.set(file("../build"))
    archiveClassifier.set("")

    relocate ("codes.antti.auth.common", "codes.antti.auth.authorization.shadow.common")
    relocate ("com.google.gson", "codes.antti.auth.authorization.shadow.gson")
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("8udPoemd")
    versionNumber.set(project.version as String)
    changelog.set("View the changelog at [GitHub releases](https://github.com/Chicken/Auth/releases/tag/authorization-v${project.version})")
    uploadFile.set(tasks.findByName("shadowJar"))
    loaders.addAll("spigot", "paper")
    versionType.set("ALPHA")
    gameVersions.addAll(
            "1.13.2",
            "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
            "1.15", "1.15.1", "1.15.2",
            "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
            "1.17", "1.17.1",
            "1.18", "1.18.1", "1.18.2",
            "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
            "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4",
    )
}

tasks.register("publish") {
    dependsOn("modrinth")
}
