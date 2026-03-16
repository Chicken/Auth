plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    }

    implementation(plugin(libs.plugins.shadow))
    implementation(plugin(libs.plugins.fabric.loom))
}
