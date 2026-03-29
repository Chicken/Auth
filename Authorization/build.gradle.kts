plugins {
    auth.spigot
}

group = "codes.antti.auth"
version = "0.4.0"

dependencies {
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.authorization.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.authorization.shadow.gson")
}
