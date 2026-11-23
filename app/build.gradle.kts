plugins {
    id("com.android.application")
}

android {
    namespace = "com.dsh.dayin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dsh.dayin"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
