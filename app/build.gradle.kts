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
        // Fournis par le workflow de release à partir du tag (v1.4 -> 1.4 / 10400)
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 4
        versionName = System.getenv("VERSION_NAME") ?: "1.3-dev"

        // Serveur de scores : variable d'environnement (secret GitHub) ou gradle.properties.
        fun setting(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: (project.findProperty(name) as String?)?.trim()
                ?: ""
        buildConfigField("String", "SCORES_URL", "\"${setting("SCORES_URL")}\"")
        buildConfigField("String", "SCORES_KEY", "\"${setting("SCORES_KEY")}\"")
        // Dépôt GitHub dont les releases servent aux mises à jour automatiques
        buildConfigField("String", "UPDATE_REPO", "\"${setting("UPDATE_REPO").ifBlank { "Thorze/sautique" }}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Clé de signature fixe (secrets GitHub) : indispensable pour que les mises à jour s'installent
    // par-dessus la version existante. Sans elle, on retombe sur la clé de debug.
    val keystorePath = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("sautique") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("sautique") ?: signingConfigs.getByName("debug")
        release {
            // R8 : code et ressources inutilisés retirés pour un APK plus léger
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
        debug {
            signingConfig = signing
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Sons : les fichiers du dossier sons/ (à la racine du dépôt) sont copiés dans les ressources
// de l'appli, avec des noms valides pour Android (minuscules, « - » et espaces remplacés par « _ »).
val generatedSounds = layout.buildDirectory.dir("generated/sons").get().asFile
val copySounds by tasks.registering(Sync::class) {
    from(rootProject.file("sons")) {
        include("**/*.mp3", "**/*.ogg", "**/*.wav", "**/*.MP3", "**/*.OGG", "**/*.WAV")
    }
    into(File(generatedSounds, "raw"))
    includeEmptyDirs = false
    eachFile {
        val ext = name.substringAfterLast('.').lowercase()
        var base = name.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_]"), "_")
        if (base.firstOrNull()?.isLetter() != true) base = "s_$base"
        path = "$base.$ext"
    }
}
android.sourceSets["main"].res.srcDir(generatedSounds)
tasks.named("preBuild") { dependsOn(copySounds) }
