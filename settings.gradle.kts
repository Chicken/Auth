rootProject.name = "Auth"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven ("https://maven.fabricmc.net/" )
    }
}

include(":Common:Http")
include(":Common:Database")
include(":Common:Fabric")

include(":Authentication:AuthCore")
proj("Authentication-Spigot", "Authentication/AuthSpigot")
proj("Authentication-Fabric", "Authentication/AuthFabric")

proj("Authorization", "Authorization")

proj("BlueMap-Demo", "BlueMap/Demo")

include(":BlueMap:Integration:IntegrationCore")
proj("BlueMap-Auth-Spigot", "BlueMap/Integration/IntegrationSpigot")
proj("BlueMap-Auth-Fabric", "BlueMap/Integration/IntegrationFabric")

proj("BlueMap-PrivateLocation", "BlueMap/PrivateLocation")
proj("BlueMap-PrivateMaps", "BlueMap/PrivateMaps")

include(":BlueMap:Chat:ChatCore")
proj("BlueMap-Chat-Spigot", "BlueMap/Chat/ChatSpigot")
proj("BlueMap-Chat-Fabric", "BlueMap/Chat/ChatFabric")

fun proj(name: String, dir: String) {
    val project = ":$name"
    include(project)
    project(project).projectDir = file(dir)
}
