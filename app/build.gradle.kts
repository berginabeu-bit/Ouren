import java.util.Properties
import java.io.FileInputStream
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.focusedmind.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.focusedmind.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 20
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("signing/release.properties")
            val props = Properties().apply { if (propsFile.isFile) propsFile.inputStream().use(::load) }
            val path = providers.environmentVariable("KEYSTORE_PATH").orNull ?: props.getProperty("storeFile")
            val storePass = providers.environmentVariable("STORE_PASSWORD").orNull ?: props.getProperty("storePassword")
            val alias = providers.environmentVariable("KEY_ALIAS").orNull ?: props.getProperty("keyAlias")
            val keyPass = providers.environmentVariable("KEY_PASSWORD").orNull ?: props.getProperty("keyPassword")
            if (!path.isNullOrBlank() && !storePass.isNullOrBlank() && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = rootProject.file(path); storePassword = storePass; keyAlias = alias; keyPassword = keyPass
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    val localProps = Properties().apply { val f = rootProject.file("local.properties"); if (f.isFile) f.inputStream().use(::load) }
    val iapKey = localProps.getProperty("focusedMindHuaweiIapPublicKey", "")
    defaultConfig {
        buildConfigField("String", "HUAWEI_IAP_PUBLIC_KEY", "\"${iapKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("com.huawei.hms:iap:6.13.0.300")
    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        // val cfg = signingConfigs.getByName("debug")
        // check(cfg.storeFile != null && !cfg.storePassword.isNullOrBlank() && !cfg.keyAlias.isNullOrBlank() && !cfg.keyPassword.isNullOrBlank()) {
            "Release signing is not configured. Set KEYSTORE_PATH/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD or signing/release.properties."
        }
    }
