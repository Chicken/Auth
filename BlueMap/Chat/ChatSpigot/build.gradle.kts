evaluationDependsOn(":BlueMap:Chat:ChatCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:Chat:ChatCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    implementation(project(":BlueMap:Chat:ChatCore"))
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.chat.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.chat.shadow.gson")
}
