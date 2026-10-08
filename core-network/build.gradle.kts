plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    jvm()
    macosArm64()
    iosArm64()
    iosSimulatorArm64()
    // For the web client in web/core. Tests run under Node, which has fetch and WebCrypto.
    js {
        browser { testTask { enabled = false } }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies { api(libs.ktor.client.okhttp) }
        // One Darwin engine serves macOS and iOS alike.
        appleMain.dependencies { api(libs.ktor.client.darwin) }
        // The browser's own fetch, so cookies and TLS are the browser's business.
        jsMain.dependencies { api(libs.ktor.client.js) }
    }
}

// `./gradlew test` is the documented way to run the suite and is what CI runs. A
// multiplatform module has no `test` task of its own, so without this the network tests
// would quietly stop being run at all. The macOS tests need a Mac, so they stay out of it
// and are run with `./gradlew :core-network:macosArm64Test`.
tasks.register("test") {
    group = "verification"
    description = "Runs the JVM unit tests, so the module joins the root `test` task."
    dependsOn("jvmTest")
}
