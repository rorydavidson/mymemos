pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyMemos"
include(":app", ":core-model", ":core-network", ":core-database", ":core-data")
include(":macos-shared")
project(":macos-shared").projectDir = file("macos/shared")
