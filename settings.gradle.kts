pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.extframework.dev/releases")
        }
        gradlePluginPortal()
    }
}


rootProject.name = "minecraft-bootstrapper"
include("def")