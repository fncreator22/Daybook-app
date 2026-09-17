import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties in the project root.
// That file is gitignored and never committed. If it is absent the release
// build still assembles, it just comes out unsigned, so a fresh clone works.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { stream -> keystoreProperties.load(stream) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.sr2ma.daybook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sr2ma.daybook"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs side by side with a release build from the Play Store.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Android ships org.json but stubs it for local unit tests, so the real
    // implementation has to be on the test classpath for BackupCodec tests.
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)

    // ML Kit — bundled (offline). DO NOT use play-services-mlkit-* variants.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.mlkit)

    // WorkManager (nightly agent, sync workers)
    implementation(libs.workmanager.ktx)

    // Phase 3 — On-device AI (opt-in, lazy-loaded — never active until user enables AI)
    // LiteRT-LM: Gemma 270M INT4 QAT inference. Model downloaded post-install, not bundled.
    implementation(libs.litert.lm)
    // ONNX Runtime Mobile: MiniLM-L6-v2 INT8 embeddings. Model bundled in assets (~22 MB).
    implementation(libs.onnxruntime.mobile)
    // sqlite-vec: KNN vector search. Pre-compiled ARM64 .so in jniLibs/arm64-v8a/.
    // Download from: https://github.com/asg017/sqlite-vec/releases — place libsqlitevec.so there.

    // Phase 4 — Google Calendar + Drive sync (opt-in, user-controlled)
    // Jetpack Security: EncryptedSharedPreferences backed by Android Keystore.
    implementation(libs.security.crypto)
    // Android Credential Manager: Google Sign-In (ID token flow).
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    // Google ID: GoogleIdTokenCredential, GetGoogleIdOption.
    implementation(libs.googleid)
}
