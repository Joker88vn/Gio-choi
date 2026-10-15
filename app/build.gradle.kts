plugins {
    id("com.android.application")
}

android {
    namespace = "vn.daopham.amlich"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.daopham.amlich"
        minSdk = 26
        targetSdk = 34
        versionCode = 30
        versionName = "3.0"
    }

    // Khoá ký cố định: các bản sau cài đè lên bản trước được
    signingConfigs {
        create("amlich") {
            storeFile = file("amlich.jks")
            storePassword = "amlich2026"
            keyAlias = "amlich"
            keyPassword = "amlich2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("amlich")
        }
        debug {
            signingConfig = signingConfigs.getByName("amlich")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
