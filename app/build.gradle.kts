import java.util.Properties
plugins { id("com.android.application") }
val signing = Properties().apply { rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) } }
android {
    namespace = "com.zfj.imageclipboard"
    compileSdk = 37
    defaultConfig { applicationId = "com.zfj.imageclipboard"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { buildConfig = true; resValues = true }
    buildTypes.getByName("debug") { applicationIdSuffix = ".debug"; resValue("string", "app_name", "图片自动复制·测试") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    if (!signing.getProperty("storePassword").isNullOrBlank()) {
        signingConfigs.create("personal") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("personal")
    }
    buildTypes.getByName("release") { isMinifyEnabled = false }
    lint { disable += "MissingTranslation" }
}
