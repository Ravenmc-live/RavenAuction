rootProject.name = "RavenAuction"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven {
            name = "groupezReleases"
            url = uri("https://repo.groupez.dev/releases")
        }
        gradlePluginPortal()
    }
}


include("API")

file("Hooks").listFiles()?.forEach { file ->
    if (file.isDirectory && file.name == "LuckPerms") {
        println("Include Hooks:${file.name}")
        include(":Hooks:${file.name}")
    }
}
