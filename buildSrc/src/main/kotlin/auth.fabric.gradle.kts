import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("auth.java")
    id("net.fabricmc.fabric-loom")
}

repositories {
    maven("https://maven.fabricmc.net")
}

val shadowImpl: Configuration by configurations.creating
configurations["implementation"].extendsFrom(shadowImpl)

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    minecraft(libs.findLibrary("minecraft").get())
    implementation(libs.findLibrary("fabric.loader").get())
    implementation(libs.findLibrary("fabric.api").get())
    shadowImpl(libs.findLibrary("snakeyaml").get())
}

tasks.named<ProcessResources>("processResources") {
    from("src/main/resources") {
        include("fabric.mod.json")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        expand(
            "version" to project.version,
            "fabric_loader_version" to libs.findLibrary("fabric.loader").get().get().version!!,
            "fabric_api_version" to libs.findLibrary("fabric.api").get().get().version!!.split("+")[0],
        )
    }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowImpl)
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.file("./build"))
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}
