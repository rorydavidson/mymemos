plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

/**
 * The framework the macOS and iOS apps link against. It exists to give Swift a small, concrete
 * surface over the shared Kotlin: Swift cannot use a generic Flow bridge, and suspend
 * functions arrive as async only when they hang off a plain class.
 *
 * One source set, `appleMain`, serves all three targets: nothing in it is specific to a Mac
 * or a phone, since Foundation, Security and Room behave the same on both.
 */
kotlin {
    listOf(macosArm64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // Without this the header carries only this module's own types, and the app
            // cannot see the interfaces it is meant to implement.
            export(project(":core-model"))
            export(project(":core-data"))
        }
    }

    sourceSets {
        appleMain.dependencies {
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
