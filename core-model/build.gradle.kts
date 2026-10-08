plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(17)

    jvm()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    // For the web client in web/core.
    js { browser(); nodejs() }

    sourceSets {
        commonMain.dependencies {
            // Only for kotlin.time.Instant, which is still experimental in the standard library.
            implementation(libs.kotlinx.datetime)
        }
        all { languageSettings.optIn("kotlin.time.ExperimentalTime") }
    }
}
