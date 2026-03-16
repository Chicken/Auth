plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = "0.1.0"

dependencies {
    compileOnly(libs.spigot.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatemaps.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatemaps.shadow.gson")
}
