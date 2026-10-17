plugins { id("com.android.application") }
android {
    namespace = "com.zfj.imageclipboard.probe"
    compileSdk = 37
    defaultConfig { applicationId = "com.zfj.imageclipboard.probe"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "test" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
