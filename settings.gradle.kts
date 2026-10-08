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
        // Kotlin/JS fetches its own Node and Yarn for the web client's build and tests.
        // Scoped by content so nothing else can resolve from these.
        ivy("https://nodejs.org/dist") {
            name = "Node.js"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}
rootProject.name = "MyMemos"
include(":app", ":core-model", ":core-network", ":core-database", ":core-data")
include(":apple-shared")
project(":apple-shared").projectDir = file("apple/shared")
include(":web-core")
project(":web-core").projectDir = file("web/core")
