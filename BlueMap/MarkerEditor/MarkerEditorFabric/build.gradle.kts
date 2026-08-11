import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":BlueMap:MarkerEditor:MarkerEditorCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:MarkerEditor:MarkerEditorCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    shadowImpl(project(":BlueMap:MarkerEditor:MarkerEditorCore"))
    shadowImpl(project(":Common:Fabric"))
    shadowImpl(libs.sqlite.jdbc) {
        isTransitive = false
    }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()
    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.markereditor.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.markereditor.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.bluemap.markereditor.fabric.shadow.snakeyaml")
    relocate("com.zaxxer.hikari", "codes.antti.auth.bluemap.markereditor.fabric.shadow.hikari")
}
