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

include(":Authorization:AuthorizationCore")
proj("Authorization-Spigot", "Authorization/AuthorizationSpigot")
proj("Authorization-Fabric", "Authorization/AuthorizationFabric")

proj("BlueMap-Demo", "BlueMap/Demo")

include(":BlueMap:Integration:IntegrationCore")
proj("BlueMap-Auth-Spigot", "BlueMap/Integration/IntegrationSpigot")
proj("BlueMap-Auth-Fabric", "BlueMap/Integration/IntegrationFabric")

include(":BlueMap:PrivateLocation:PrivateLocationCore")
proj("BlueMap-PrivateLocation-Spigot", "BlueMap/PrivateLocation/PrivateLocationSpigot")
proj("BlueMap-PrivateLocation-Fabric", "BlueMap/PrivateLocation/PrivateLocationFabric")

include(":BlueMap:PrivateMaps:PrivateMapsCore")
proj("BlueMap-PrivateMaps-Spigot", "BlueMap/PrivateMaps/PrivateMapsSpigot")
proj("BlueMap-PrivateMaps-Fabric", "BlueMap/PrivateMaps/PrivateMapsFabric")

include(":BlueMap:Chat:ChatCore")
proj("BlueMap-Chat-Spigot", "BlueMap/Chat/ChatSpigot")
proj("BlueMap-Chat-Fabric", "BlueMap/Chat/ChatFabric")

include(":BlueMap:MarkerEditor:MarkerEditorCore")
proj("BlueMap-MarkerEditor-Spigot", "BlueMap/MarkerEditor/MarkerEditorSpigot")
proj("BlueMap-MarkerEditor-Fabric", "BlueMap/MarkerEditor/MarkerEditorFabric")

fun proj(name: String, dir: String) {
    val project = ":$name"
    include(project)
    project(project).projectDir = file(dir)
}
