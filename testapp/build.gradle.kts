plugins {
    id("com.android.application")
}

android {
    namespace = "com.cloneapp.testapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloneapp.testapp"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
