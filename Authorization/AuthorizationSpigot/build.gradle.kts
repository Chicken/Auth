evaluationDependsOn(":Authorization:AuthorizationCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth"
version = project(":Authorization:AuthorizationCore").version

dependencies {
    compileOnly(libs.luckperms)
    implementation(project(":Authorization:AuthorizationCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.authorization.spigot.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authorization.spigot.shadow.gson")
}
