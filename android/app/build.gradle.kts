import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 读取本地 local.properties（不提交到 Git）以便本地开发也可注入密钥
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun resolveConfigProp(envKey: String, propKey: String = envKey): String {
    return (System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(propKey)?.takeIf { it.isNotBlank() }
        ?: "").trim()
}

fun escapeBuildConfigString(raw: String): String {
    return raw.replace("\\", "\\\\").replace("\"", "\\\"")
}

val appHmacSecret = resolveConfigProp("APP_HMAC_SECRET")
val adminSyncToken = resolveConfigProp("ADMIN_SYNC_TOKEN")

val releaseKeystorePath = resolveConfigProp("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = resolveConfigProp("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = resolveConfigProp("ANDROID_KEY_ALIAS")
val releaseKeyPassword = resolveConfigProp("ANDROID_KEY_PASSWORD")
val hasReleaseKeystore = releaseKeystorePath.isNotBlank() &&
    File(releaseKeystorePath).exists() &&
    releaseKeystorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

android {
    namespace = "com.zeng.lottery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zeng.lottery"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "APP_HMAC_SECRET", "\"${escapeBuildConfigString(appHmacSecret)}\"")
        buildConfigField("String", "ADMIN_SYNC_TOKEN", "\"${escapeBuildConfigString(adminSyncToken)}\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = File(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("test") {
            resources.srcDir("src/main/assets")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // 网络与 SSE 流式解析
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // 6tail 农历天文历法引擎（与后端 lunar_python 同源算法）
    implementation("cn.6tail:lunar:1.7.4")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
