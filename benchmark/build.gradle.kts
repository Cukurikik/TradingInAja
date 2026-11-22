plugins {
    // AGP 9 menyediakan dukungan Kotlin bawaan; plugin `org.jetbrains.kotlin.android`
    // tidak boleh diterapkan lagi (https://kotl.in/gradle/agp-built-in-kotlin).
    alias(libs.plugins.android.test)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

/**
 * Modul Macrobenchmark Fase 8 (startup, scroll chart, tick engine).
 *
 * Jalankan pada perangkat low-end (RAM 3 GB) atau emulator API 29+ dengan profil perangkat tersebut:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest
 *
 * Target: P95 frameDurationCpuMs < 16 ms untuk scroll chart dan tick engine.
 */
android {
    namespace = "com.tradinginaja.benchmark"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.runner)
    implementation(libs.androidx.uiautomator)
}
