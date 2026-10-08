plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The web client's Kotlin half: the shared rules, the network client, an IndexedDB-backed
 * store with its own outbox and sync engine, and [com.keltruc.mymemos.web.WebSession], the
 * one surface the TypeScript UI in web/app calls. Built as an ES module with .d.ts typings.
 *
 * core-data cannot take a JS target because its repositories are Room DAOs, and Room has no
 * browser build. Rather than split that module, the files in it that only decide things
 * (no Room, no platform) are compiled here straight from their own directory. They stay
 * single-sourced: change a rule in core-data and the web client follows on its next build.
 * Adding a file to [sharedLogic] is the whole cost of sharing another one.
 */
val coreData = rootProject.file("core-data/src")

val sharedLogic = listOf(
    "com/keltruc/mymemos/data/account/AvatarSource.kt",
    "com/keltruc/mymemos/data/config/ConfigCodec.kt",
    "com/keltruc/mymemos/data/crypto/AesGcm.kt",
    "com/keltruc/mymemos/data/crypto/MemoCipher.kt",
    "com/keltruc/mymemos/data/crypto/PasswordSession.kt",
    "com/keltruc/mymemos/data/export/BackupCipher.kt",
    "com/keltruc/mymemos/data/export/MarkdownFormat.kt",
    "com/keltruc/mymemos/data/imports/MarkdownImport.kt",
    "com/keltruc/mymemos/data/notify/Digest.kt",
    "com/keltruc/mymemos/data/notify/Schedule.kt",
    "com/keltruc/mymemos/data/repository/TemplateValues.kt",
    "com/keltruc/mymemos/data/sync/LineDiff.kt",
    "com/keltruc/mymemos/data/sync/SyncState.kt",
    "com/keltruc/mymemos/data/sync/ThreeWayMerge.kt",
    "com/keltruc/mymemos/data/text/**",
    "com/keltruc/mymemos/data/timeline/**",
    "com/keltruc/mymemos/data/zip/**",
)

// Their tests come too, so the rules are checked on the JS compiler as well as the JVM and
// Kotlin/Native ones. Tests that reach a repository stay behind.
val sharedTests = listOf(
    "com/keltruc/mymemos/data/account/**",
    "com/keltruc/mymemos/data/config/**",
    "com/keltruc/mymemos/data/crypto/**",
    "com/keltruc/mymemos/data/notify/**",
    "com/keltruc/mymemos/data/sync/**",
    "com/keltruc/mymemos/data/text/**",
    "com/keltruc/mymemos/data/zip/**",
)

kotlin {
    js {
        browser { testTask { enabled = false } }
        // Tests run under Node, which has WebCrypto and fetch but no IndexedDB. Mocha's 2s
        // default is too short on a cold CI runner for the first test that touches a time
        // zone, since that loads the whole js-joda zone database.
        nodejs { testTask { useMocha { timeout = "30s" } } }
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(coreData.resolve("commonMain/kotlin"))
            kotlin.include(sharedLogic)
            dependencies {
                api(project(":core-model"))
                api(project(":core-network"))
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        jsMain.dependencies {
            // kotlinx-datetime has no time-zone rules of its own in a browser, not even for
            // the system zone; this is the database it expects. Loaded in TimeZones.kt.
            implementation(npm("@js-joda/timezone", "2.22.0"))
        }
        commonTest {
            kotlin.srcDir(coreData.resolve("commonTest/kotlin"))
            kotlin.include(sharedTests)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            languageSettings.optIn("kotlin.io.encoding.ExperimentalEncodingApi")
        }
    }
}

tasks.register("test") {
    group = "verification"
    description = "Runs the shared rules and the web store's tests under Node."
    dependsOn("jsNodeTest")
}

