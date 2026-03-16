plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = "0.1.0"

dependencies {
    compileOnly(libs.spigot.api)
    compileOnly(libs.luckperms)
    compileOnly(libs.jetbrains.annotations)
}

tasks.jar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")
}
