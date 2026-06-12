import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") version "4.4.4"
}

// Release imzalama bilgisi (keystore.properties varsa). Bu dosya git'e girmez.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

android {
    namespace = "com.watch.watchtofriend"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.watch.watchtofriend"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keystore.properties varsa release imzasıyla imzala (Play yüklemesi için)
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")      // Remote Config
    implementation("com.google.firebase:firebase-database")    // RTDB — ekran paylaşımı frame relay
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Media3 ExoPlayer — doğrudan video linki (.mp4/.m3u8/HLS) oynatıcı + tam senkron
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // WebRTC — ekran paylaşımı (ücretsiz, peer-to-peer)
    implementation("io.github.webrtc-sdk:android:125.6422.06.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

/** Firebase + assetlinks.json için release SHA-256 al. */
tasks.register("printReleaseFingerprints") {
    group = "signing"
    description = "Release keystore SHA-1 / SHA-256 (Firebase ve davet linki için)"
    doLast {
        if (!keystorePropsFile.exists()) {
            println(
                """
                |
                | keystore.properties yok — SHA-256 şu yollardan alınır:
                |
                | [Play Store APK kullanıyorsan — ÖNEMLİ]
                |   Play Console → Kurulum → Uygulama bütünlüğü → Uygulama imzalama
                |   → "Uygulama imzalama anahtarı sertifikası" → SHA-256
                |   (Upload key değil — mağazadan indiren kullanıcılar bunu kullanır)
                |
                | [El ile imzaladığın .jks varsa]
                |   keytool -list -v -keystore watchtofriend-release.jks -alias watchtofriend
                |
                | [Debug APK test ediyorsan — zaten assetlinks'te var]
                |   ./gradlew signingReport
                |
                """.trimMargin()
            )
            return@doLast
        }
        val storePath = keystoreProps.getProperty("storeFile") ?: error("storeFile eksik")
        val storeFile = file(storePath)
        if (!storeFile.exists()) error("Keystore bulunamadı: $storePath")
        val alias = keystoreProps.getProperty("keyAlias") ?: error("keyAlias eksik")
        val storePass = keystoreProps.getProperty("storePassword") ?: error("storePassword eksik")
        val keytool = File(System.getProperty("java.home"), "bin/keytool.exe")
        val cmd = listOf(
            keytool.absolutePath,
            "-list", "-v",
            "-keystore", storeFile.absolutePath,
            "-alias", alias,
            "-storepass", storePass
        )
        println("=== Release sertifika parmak izleri ===")
        ProcessBuilder(cmd).inheritIO().start().waitFor()
        println("=== Firebase: Project settings → Android → Add fingerprint (SHA-256) ===")
        println("=== assetlinks.json sha256_cert_fingerprints dizisine de ekle ===")
    }
}
