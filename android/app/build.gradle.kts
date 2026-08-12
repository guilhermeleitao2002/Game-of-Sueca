import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

/**
 * Release signing details, from `android/keystore.properties` or the environment.
 *
 * Neither the keystore nor the passwords are ever committed, and when they are missing the
 * release build still runs — it just comes out unsigned, which is enough for CI and for anyone
 * who has cloned the repository and only wants to check that it compiles.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, environmentVariable: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(environmentVariable)

val releaseKeystore: File? = signingValue("storeFile", "SUECA_KEYSTORE")
    ?.let { path -> File(path).takeIf { it.isAbsolute } ?: rootProject.file(path) }
    ?.takeIf { it.exists() }

android {
    namespace = "pt.up.fe.asma.sueca"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.up.fe.asma.sueca"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = releaseKeystore
                storePassword = signingValue("storePassword", "SUECA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SUECA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SUECA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Camera + on device text recognition for the card scanner.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
