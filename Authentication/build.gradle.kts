plugins {
    java
    id ("com.github.johnrengelman.shadow") version "8.1.1"
    id ("com.modrinth.minotaur") version "2.+"
    id ("io.papermc.hangar-publish-plugin") version "0.0.5"
}

group = "codes.antti.auth"
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
    compileOnly ("org.jetbrains:annotations:23.0.0")
    implementation ("codes.antti.auth:Common")
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

    relocate ("org.sqlite", "codes.antti.shadow.sqlite")
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("NWzrSkMa")
    versionNumber.set(project.version as String)
    changelog.set("View the changelog at [GitHub releases](https://github.com/Chicken/Auth/releases/tag/authentication-v${project.version})")
    uploadFile.set(tasks.findByName("shadowJar"))
    loaders.addAll("spigot", "paper")
    gameVersions.addAll(
            "1.13.2",
            "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
            "1.15", "1.15.1", "1.15.2",
            "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
            "1.17", "1.17.1",
            "1.18", "1.18.1", "1.18.2",
            "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4"
    )
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version as String)
        namespace("Chicken", "Authentication")
        channel.set("Alpha")
        changelog.set("View the changelog at [GitHub releases](https://github.com/Chicken/Auth/releases/tag/authentication-v${project.version})")
        apiKey.set(System.getenv("HANGAR_TOKEN"))
        platforms {
            register(io.papermc.hangarpublishplugin.model.Platforms.PAPER) {
                url.set("https://github.com/Chicken/Auth/releases/download/authentication-v${project.version}/Authentication-${project.version}.jar")
                platformVersions.set(listOf(
                    "1.13.2",
                    "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
                    "1.15", "1.15.1", "1.15.2",
                    "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
                    "1.17", "1.17.1",
                    "1.18", "1.18.1", "1.18.2",
                    "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4"
                ))
            }
        }
    }
}

tasks.register("publish") {
	dependsOn("modrinth")
	dependsOn("publishPluginPublicationToHangar")
}
