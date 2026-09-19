plugins {
    id("com.android.library")
}

android {
    namespace = "com.cloneapp.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
