evaluationDependsOn(":BlueMap:MarkerEditor:MarkerEditorCore")

plugins {
    auth.spigot
}

group = "codes.antti.auth.bluemap"
version = project(":BlueMap:MarkerEditor:MarkerEditorCore").version

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    implementation(project(":BlueMap:MarkerEditor:MarkerEditorCore"))
    runtimeOnly(libs.sqlite.jdbc) {
        isTransitive = false
    }
}

tasks.shadowJar {
    destinationDirectory.set(rootProject.file("./build"))
    archiveClassifier.set("")
    mergeServiceFiles()

    relocate("codes.antti.auth.common", "codes.antti.auth.bluemap.markereditor.shadow.common")
    relocate("com.google.gson", "codes.antti.auth.bluemap.markereditor.shadow.gson")
    relocate("com.zaxxer.hikari", "codes.antti.auth.bluemap.markereditor.shadow.hikari")
}
