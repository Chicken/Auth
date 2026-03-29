plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = "0.3.0"

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
    implementation(libs.gson)
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatelocation.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatelocation.shadow.gson")
}
