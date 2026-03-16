import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("auth.java")
    id("fabric-loom")
}

repositories {
    maven("https://maven.fabricmc.net")
}

val shadowImpl: Configuration by configurations.creating
configurations["implementation"].extendsFrom(shadowImpl)

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
    minecraft(libs.findLibrary("minecraft").get())
    mappings(libs.findLibrary("yarn.mappings").get())
    modImplementation(libs.findLibrary("fabric.loader").get())
    modImplementation(libs.findLibrary("fabric.api").get())
    shadowImpl(libs.findLibrary("snakeyaml").get())
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowImpl)
    archiveClassifier.set("dev-shadow")
}

tasks.named<RemapJarTask>("remapJar") {
    inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    dependsOn(tasks.named("shadowJar"))
    archiveClassifier.set("")
    destinationDirectory.set(rootProject.file("./build"))
}

tasks.named("build") {
    dependsOn(tasks.named("remapJar"))
}
