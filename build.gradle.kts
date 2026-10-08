plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

// settings.gradle.kts forbids project repositories, and Kotlin/JS adds its own for Node and
// Yarn unless told not to. The same distributions are declared there instead. Every module
// with a js target applies the Node plugin, so this has to reach all of them.
allprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().downloadBaseUrl.set(null as String?)
    }
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec>().downloadBaseUrl.set(null as String?)
    }
}
