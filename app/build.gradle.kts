plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sautique.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sautique.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        // Classement mondial (Supabase) : propriétés Gradle ou variables d'environnement.
        fun secret(name: String) = (project.findProperty(name) as String?) ?: System.getenv(name) ?: ""
        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signé avec la clé de debug pour pouvoir l'installer directement.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
