import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
  keystoreProperties.load(keystorePropertiesFile.inputStream())
}


android {
  namespace = "tv.firetvwebwrapper.app"
  compileSdk = 34

  val releaseSigningConfig = if (keystoreProperties.isNotEmpty()) {
    val storeFilePath = keystoreProperties.getProperty("storeFile")
      ?: error("keystore.properties is missing storeFile")
    val storePassword = keystoreProperties.getProperty("storePassword")
      ?: error("keystore.properties is missing storePassword")
    val keyAlias = keystoreProperties.getProperty("keyAlias")
      ?: error("keystore.properties is missing keyAlias")
    val keyPassword = keystoreProperties.getProperty("keyPassword")
      ?: error("keystore.properties is missing keyPassword")

    signingConfigs.create("release") {
      storeFile = file(storeFilePath)
      this.storePassword = storePassword
      this.keyAlias = keyAlias
      this.keyPassword = keyPassword
    }
  } else {
    null
  }

  defaultConfig {
    applicationId = "tv.firetvwebwrapper.app"
    minSdk = 30
    targetSdk = 34
    versionCode = 4
    versionName = "1.1.2"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )

      if (releaseSigningConfig != null) {
        signingConfig = releaseSigningConfig
      } else {
        logger.warn("Release build is currently unsigned. Provide keystore.properties to enable signing.")
      }
    }
  }

  buildFeatures {
    viewBinding = true
    buildConfig = true
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
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.preference:preference-ktx:1.2.1")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation("com.google.zxing:core:3.5.2")
}
