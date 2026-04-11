import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":BlueMap:PrivateLocation:PrivateLocationCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:PrivateLocation:PrivateLocationCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    shadowImpl(project(":BlueMap:PrivateLocation:PrivateLocationCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatelocation.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatelocation.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.bluemap.privatelocation.fabric.shadow.snakeyaml")
}
