plugins {
    id("auth.java")
}

repositories {
    maven {
        setUrl("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

tasks.named<ProcessResources>("processResources") {
    from("src/main/resources") {
        include("plugin.yml")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        expand(
            "version" to project.version
        )
    }
}

pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.named("build") {
        dependsOn(tasks.named("shadowJar"))
    }
}
