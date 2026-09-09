plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.keltruc.mymemos.data"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core-model"))
    implementation(project(":core-network"))
    implementation(project(":core-database"))

    // Only for the Coil image-loader interceptor in ActiveSession. It used to come in
    // transitively through core-network, which no longer speaks OkHttp directly.
    implementation(libs.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.security.crypto)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.diff.utils)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(project(":core-network"))
}
