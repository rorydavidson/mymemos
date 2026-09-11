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
include(":apple-shared")
project(":apple-shared").projectDir = file("apple/shared")
