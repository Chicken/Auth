import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":BlueMap:Chat:ChatCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:Chat:ChatCore").version

dependencies {
    "modCompileOnly"(libs.bluemap.api)
    shadowImpl(project(":BlueMap:Chat:ChatCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.chat.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.chat.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.bluemap.chat.fabric.shadow.snakeyaml")
}
