evaluationDependsOn(":Authentication:AuthCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth"
version = project(":Authentication:AuthCore").version

dependencies {
    compileOnly(libs.spigot.api)
    compileOnly(libs.jetbrains.annotations)
    implementation(project(":Authentication:AuthCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.authentication.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authentication.shadow.gson")
    relocate("com.zaxxer.hikari", "codes.antti.auth.authentication.shadow.hikari")
}
