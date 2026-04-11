import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

evaluationDependsOn(":Authorization:AuthorizationCore")

plugins {
    auth.fabric
}

group = "codes.antti.auth"
version = project(":Authorization:AuthorizationCore").version

dependencies {
    compileOnly(libs.luckperms)
    shadowImpl(project(":Authorization:AuthorizationCore"))
    shadowImpl(project(":Common:Fabric"))
}

tasks.named<ShadowJar>("shadowJar") {
    relocate("codes.antti.auth.common", "codes.antti.auth.authorization.fabric.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authorization.fabric.shadow.gson")
    relocate("org.yaml.snakeyaml", "codes.antti.auth.authorization.fabric.shadow.snakeyaml")
}
