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
        versionCode = 5
        versionName = "1.0-beta.5"

        // Client-side Supabase configuration.
        // Only the publishable/anon key belongs in an Android client.
        // Never put a service-role/secret key in the APK.
        val envProps = Properties().apply {
            val envExample = rootProject.file(".env.example")
            if (envExample.exists()) envExample.inputStream().use { load(it) }
            val envFile = rootProject.file(".env")
            if (envFile.exists()) envFile.inputStream().use { load(it) }
        }

        // This is the active Uzzap Supabase project. Local/CI configuration
        // can override it with -PSUPABASE_URL or SUPABASE_URL.
        val defaultSupabaseUrl = "https://fnnvajxveklmgjnxstrq.supabase.co"
        val defaultSupabaseAnonKey = ""

        val projectSupabaseUrl = providers.gradleProperty("SUPABASE_URL").orNull
        val projectSupabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY").orNull
        val projectSupabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY").orNull
        val envSupabaseUrl = System.getenv("SUPABASE_URL")
        val envSupabasePublishableKey = System.getenv("SUPABASE_PUBLISHABLE_KEY")
        val envSupabaseAnonKey = System.getenv("SUPABASE_ANON_KEY")
        val fileSupabaseUrl = envProps.getProperty("SUPABASE_URL")
        val fileSupabasePublishableKey = envProps.getProperty("SUPABASE_PUBLISHABLE_KEY")
        val fileSupabaseAnonKey = envProps.getProperty("SUPABASE_ANON_KEY")

        val supabaseUrl = (projectSupabaseUrl ?: envSupabaseUrl ?: fileSupabaseUrl ?: defaultSupabaseUrl)
            .trim().trimEnd('/').ifBlank { defaultSupabaseUrl }
        val supabaseKey = (projectSupabasePublishableKey
            ?: envSupabasePublishableKey
            ?: fileSupabasePublishableKey
            ?: projectSupabaseAnonKey
            ?: envSupabaseAnonKey
            ?: fileSupabaseAnonKey
            ?: defaultSupabaseAnonKey)
            .trim()

        fun quoteBuildConfig(value: String): String =
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

        buildConfigField("String", "SUPABASE_URL", quoteBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoteBuildConfig(supabaseKey))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quoteBuildConfig(supabaseKey))
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

    if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (!releaseKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
