plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Project-root .env first, then process env; a missing key yields "" and never fails the build.
val dotenv: Map<String, String> = rootProject.file(".env").takeIf { it.isFile }?.readLines().orEmpty()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'") }

fun env(name: String, default: String = ""): String =
    dotenv[name]?.takeIf { it.isNotBlank() } ?: System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

fun javaString(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.haiku.wall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.haiku.wall"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "OPENROUTER_API_KEY", javaString(env("OPENROUTER_API_KEY")))
        buildConfigField(
            "String",
            "OPENROUTER_API_URL",
            javaString(env("OPENROUTER_API_URL", "https://openrouter.ai/api/v1/chat/completions")),
        )
        buildConfigField("String", "OPENROUTER_MODEL", javaString(env("OPENROUTER_MODEL", "anthropic/claude-haiku-5.5")))
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        // shortcut: release is unminified and unsigned, add R8 rules + a signing config before shipping to a store
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    // AGP 8.7 lint misreads `value = withContext {..}` inside produceState under Kotlin 2.0 (false positive).
    lint { disable += "ProduceStateDoesNotAssignValue" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.javascriptengine:javascriptengine:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
