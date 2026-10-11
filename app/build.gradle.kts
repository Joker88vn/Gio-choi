plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.daopham.giochoi"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.daopham.giochoi"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Khoá ký cố định: các bản build sau cài đè lên bản trước được
    signingConfigs {
        create("giochoi") {
            storeFile = file("giochoi.jks")
            storePassword = "giochoi123"
            keyAlias = "giochoi"
            keyPassword = "giochoi123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("giochoi")
        }
        debug {
            signingConfig = signingConfigs.getByName("giochoi")
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
