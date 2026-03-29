plugins {
    id("auth.java")
}

repositories {
    maven {
        setUrl("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    compileOnly(libs.findLibrary("spigot.api").get())
}

tasks.named<ProcessResources>("processResources") {
    from("src/main/resources") {
        include("plugin.yml")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        expand(
            "version" to project.version,
            "api_version" to libs.findLibrary("spigot.api").get().get().version!!.substring(0, 4)
        )
    }
}

pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.named("build") {
        dependsOn(tasks.named("shadowJar"))
    }
}
