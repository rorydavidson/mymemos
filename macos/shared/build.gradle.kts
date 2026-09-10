plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

/**
 * The framework the macOS app links against. It exists to give Swift a small, concrete
 * surface over the shared Kotlin: Swift cannot use a generic Flow bridge, and suspend
 * functions arrive as async only when they hang off a plain class.
 */
kotlin {
    macosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        macosArm64Main.dependencies {
            api(project(":core-model"))
            api(project(":core-network"))
            api(project(":core-data"))
            api(project(":core-database"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.datastore.preferences.core)
        }
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
    }
}
