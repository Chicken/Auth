evaluationDependsOn(":BlueMap:Integration:IntegrationCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:Integration:IntegrationCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    implementation(project(":BlueMap:Integration:IntegrationCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.integration.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.integration.shadow.gson")
}
