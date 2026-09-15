import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.kolipri.uzzap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kolipri.uzzap"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "1.0-beta.3"

        // Client-side Supabase configuration.
        // Only the publishable/anon key belongs in an Android client.
        // Never put a service-role/secret key in the APK.
        val envProps = Properties().apply {
            val envExample = rootProject.file(".env.example")
            if (envExample.exists()) {
                envExample.inputStream().use { load(it) }
            }
            val envFile = rootProject.file(".env")
            if (envFile.exists()) {
                envFile.inputStream().use { load(it) }
            }
        }

        // These are safe client-side values. They are deliberately kept as a
        // deterministic fallback so a CI/local build cannot produce an APK
        // with empty Supabase configuration when .env files are absent.
        val defaultSupabaseUrl = "https://hrjgblvxhtbopuyzyasu.supabase.co"
        val defaultSupabaseAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhyamdibHZ4aHRib3B1eXp5YXN1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODkzMzMzOTcsImV4cCI6MjEwNDkwOTM5N30.Ms5NsNsJCykVjr7CiX-IwigAYoHfG1nxysTA_oRb9MA"

        val supabaseUrl = (System.getenv("SUPABASE_URL")
            ?: envProps.getProperty("SUPABASE_URL", defaultSupabaseUrl))
            .orEmpty().trimEnd('/').ifBlank { defaultSupabaseUrl }
        val supabasePublishableKey = System.getenv("SUPABASE_PUBLISHABLE_KEY")
            ?: envProps.getProperty("SUPABASE_PUBLISHABLE_KEY", "")
        val supabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
            ?: envProps.getProperty("SUPABASE_ANON_KEY", defaultSupabaseAnonKey)
        val supabaseClientKey = supabasePublishableKey.ifBlank { supabaseAnonKey }.ifBlank { defaultSupabaseAnonKey }

        fun quoteBuildConfig(value: String): String =
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

        buildConfigField("String", "SUPABASE_URL", quoteBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoteBuildConfig(supabaseClientKey))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    testImplementation("junit:junit:4.13.2")
}
