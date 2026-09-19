plugins {
    id("com.android.application")
}

android {
    namespace = "com.cloneapp.ca"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloneapp.ca"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha01"
    }
}

dependencies {
    implementation(project(":ca-core"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
