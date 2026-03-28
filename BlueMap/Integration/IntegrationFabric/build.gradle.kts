import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":BlueMap:Integration:IntegrationCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:Integration:IntegrationCore").version

dependencies {
    "modCompileOnly"(libs.bluemap.api)
    shadowImpl(project(":BlueMap:Integration:IntegrationCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.integration.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.integration.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.bluemap.integration.fabric.shadow.snakeyaml")
}
