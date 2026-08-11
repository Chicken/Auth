plugins {
    `java-library`
    auth.java
}

group = "codes.antti.auth.bluemap.markereditor"
version = "0.1.0"

dependencies {
    compileOnly(libs.bluemap.api)
    compileOnly(libs.luckperms)
    implementation(project(":Common:Http"))
    implementation(project(":Common:Database"))
    implementation(libs.gson)
}

val webDirectory = layout.projectDirectory.dir("../web")

val pnpmInstall by tasks.registering(Exec::class) {
    workingDir(webDirectory)
    commandLine("pnpm", "install", "--frozen-lockfile")
    inputs.files(
        webDirectory.file("package.json"),
        webDirectory.file("pnpm-lock.yaml"),
        webDirectory.file("pnpm-workspace.yaml")
    )
    outputs.dir(webDirectory.dir("node_modules"))
}

val webTypecheck by tasks.registering(Exec::class) {
    dependsOn(pnpmInstall)
    workingDir(webDirectory)
    commandLine("pnpm", "run", "typecheck")
    inputs.dir(webDirectory.dir("src"))
    inputs.files(webDirectory.file("package.json"), webDirectory.file("tsconfig.json"))
}

val webBuild by tasks.registering(Exec::class) {
    dependsOn(pnpmInstall, webTypecheck)
    workingDir(webDirectory)
    commandLine("pnpm", "run", "build")
    inputs.dir(webDirectory.dir("src"))
    inputs.files(
        webDirectory.file("package.json"),
        webDirectory.file("pnpm-lock.yaml"),
        webDirectory.file("tsconfig.json"),
        webDirectory.file("vite.config.ts")
    )
    outputs.dir(webDirectory.dir("dist"))
}

tasks.processResources {
    dependsOn(webBuild)
    from(webDirectory.dir("dist")) {
        into("assets/bluemap-marker-editor")
    }
}
