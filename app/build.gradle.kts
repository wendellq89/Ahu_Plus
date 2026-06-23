plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 2026-06-22 AboutLibraries: 离线仓库未提供 plugin marker,改为手动生成 aboutlibraries.json
    // (运行时库 aboutlibraries-compose 仍可用)
}

android {
    namespace = "com.yourname.ahu_plus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yourname.ahu_plus"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 2026-06-22 体积优化：ABI Split（见下方 splits.abi 块）。
        // 注意：不要在这里加 ndk.abiFilters，会同时影响 universal APK,
        // 导致兜底包也只剩 arm64 libs。splits.abi.include 才是 per-variant 控制。
    }

    // 临时 release 签名:复用 Android SDK 自带 debug.keystore,仅用于本地自测,不能上 Play Store
    signingConfigs {
        create("release") {
            val debugKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storeFile = debugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            // 内测阶段：debug 构建启用云端备份等测试功能
            buildConfigField("boolean", "ENABLE_CLOUD_BACKUP", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 发布阶段：云端备份由 ProGuard 混淆隐藏，功能仍可用但不可被逆向发现
            buildConfigField("boolean", "ENABLE_CLOUD_BACKUP", "true")
        }
    }

    // 2026-06-22 体积优化：ABI Split
    // 输出 app-arm64-v8a-debug.apk + app-universal-debug.apk(兜底)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.jsoup)
    implementation(libs.zxing.core)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.conscrypt.android)
    // AboutLibraries 依赖已临时移除 (Aliyun 镜像未缓存 11.6.1),改用 OpenSourceLicensesScreen 手写列表
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
