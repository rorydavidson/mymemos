plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    jvmToolchain(17)

    // Android keeps its own target rather than borrowing a plain JVM one, so the app goes on
    // using the platform's SQLite and Room's Android artifacts exactly as before. Only the
    // macOS build gets the bundled SQLite driver.
    androidLibrary {
        namespace = "com.keltruc.mymemos.database"
        compileSdk = 37
        minSdk = 26
    }
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.room.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // core-data still reaches for withTransaction, which lives here.
            api(libs.room.ktx)
        }
        macosArm64Main.dependencies {
            implementation(libs.sqlite.bundled)
        }
        macosArm64Test.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspMacosArm64", libs.room.compiler)
}
