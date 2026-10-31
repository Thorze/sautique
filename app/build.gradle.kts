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
        versionCode = 3
        versionName = "1.2"

        // Serveur de scores : variable d'environnement (secret GitHub) ou gradle.properties.
        fun setting(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: (project.findProperty(name) as String?)?.trim()
                ?: ""
        buildConfigField("String", "SCORES_URL", "\"${setting("SCORES_URL")}\"")
        buildConfigField("String", "SCORES_KEY", "\"${setting("SCORES_KEY")}\"")
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
