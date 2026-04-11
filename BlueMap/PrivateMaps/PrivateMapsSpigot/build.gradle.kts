evaluationDependsOn(":BlueMap:PrivateMaps:PrivateMapsCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:PrivateMaps:PrivateMapsCore").version

dependencies {
    compileOnly(libs.luckperms)
    implementation(project(":BlueMap:PrivateMaps:PrivateMapsCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatemaps.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatemaps.shadow.gson")
}
