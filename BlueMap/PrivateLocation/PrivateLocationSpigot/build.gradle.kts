evaluationDependsOn(":BlueMap:PrivateLocation:PrivateLocationCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:PrivateLocation:PrivateLocationCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    implementation(project(":BlueMap:PrivateLocation:PrivateLocationCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.privatelocation.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.privatelocation.shadow.gson")
}
