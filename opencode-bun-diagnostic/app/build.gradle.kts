plugins {
    id("com.android.application")
}

android {
    namespace = "com.f1n0.nexus.opencodediagnostic"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.f1n0.nexus.opencodediagnostic"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-diagnostic"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
