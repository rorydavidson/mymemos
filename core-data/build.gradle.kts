plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.keltruc.mymemos.data"
        compileSdk = 37
        minSdk = 26
        // Deliberately not in the "test" source-set tree: that would pull commonTest into the
        // instrumented build, and D8 rejects the spaces in its backtick test names below dex 040.
        withDeviceTestBuilder {}
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
        withHostTestBuilder {}.configure {}
    }
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
            implementation(project(":core-network"))
            implementation(project(":core-database"))
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.datastore.preferences.core)
        }
        macosArm64Main.dependencies {
            implementation(project(":core-database"))
        }
        androidMain.dependencies {
            // The Coil image-loader interceptor, the Keystore credential store, and the
            // export, import and backup features, which stay on Android until Phase 5.
            implementation(libs.okhttp)
            implementation(libs.security.crypto)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.test.runner)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
