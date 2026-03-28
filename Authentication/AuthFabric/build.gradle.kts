import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":Authentication:AuthCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth"
version = project(":Authentication:AuthCore").version

dependencies {
    shadowImpl(project(":Authentication:AuthCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.authentication.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authentication.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.authentication.fabric.shadow.snakeyaml")
    relocate("com.zaxxer.hikari", "codes.antti.auth.authentication.fabric.shadow.hikari")
}
