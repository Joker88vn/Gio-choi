plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.daopham.codmaster"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.daopham.codmaster"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.4"
    }

    // Khoá ký cố định: các bản build sau cài đè lên bản trước được
    signingConfigs {
        create("codmaster") {
            storeFile = file("codmaster.jks")
            storePassword = "codmaster123"
            keyAlias = "codmaster"
            keyPassword = "codmaster123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("codmaster")
        }
        debug {
            signingConfig = signingConfigs.getByName("codmaster")
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
