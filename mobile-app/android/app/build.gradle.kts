import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// ---- Firebase (FlutterFire) ----
// The Google Services plugin turns google-services.json into Android resources
// (Firebase app id, default_web_client_id for Google sign-in). The JSON is
// gitignored, so apply the plugin only when it exists: CI and fresh checkouts
// still build, just without push / Google sign-in (the app hides them).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// ---- Release signing ----
// The upload keystore + its passwords live OUTSIDE git (android/key.properties and *.jks are
// gitignored). Think of it like a bank's HSM signing key: whoever holds it can publish
// "official" updates of the app, so it goes in a password manager, never in the repo.
// android/key.properties format:
//   storePassword=...
//   keyPassword=...
//   keyAlias=upload
//   storeFile=C:/path/outside/repo/nepalgo-upload.jks
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.nepalgo.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Permanent store identity: can never change after the first Play upload.
        applicationId = "com.nepalgo.app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // Real upload key when key.properties exists; otherwise fall back to the debug key so
            // `flutter run --release` still works locally. A debug-signed bundle is rejected by Play,
            // so a forgotten key.properties can't accidentally ship.
            signingConfig = if (keystorePropertiesFile.exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
