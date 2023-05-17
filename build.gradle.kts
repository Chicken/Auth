tasks.register("clean") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":clean"))
    }

    doFirst {
        if (!file("build").deleteRecursively())
            throw java.io.IOException("Failed to delete build directory!")
    }
}

tasks.register("build") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":build"))
    }
}
