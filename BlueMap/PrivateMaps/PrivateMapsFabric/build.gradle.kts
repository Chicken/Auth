import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":BlueMap:PrivateMaps:PrivateMapsCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:PrivateMaps:PrivateMapsCore").version

dependencies {
    compileOnly(libs.luckperms)
    shadowImpl(project(":BlueMap:PrivateMaps:PrivateMapsCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatemaps.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatemaps.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.bluemap.privatemaps.fabric.shadow.snakeyaml")
}
