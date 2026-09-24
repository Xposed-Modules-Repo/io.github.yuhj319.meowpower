import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 签名信息放在根目录的 keystore.properties，不写死在构建脚本里。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

kotlin {
    compilerOptions {
        // AGP 9.4 自带的 Kotlin 编译器是 2.2.x，而 Miuix 用 2.4.x 编译，
        // 其 metadata 版本偏高。跳过版本校验让 2.2 编译器仍能读取。
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

android {
    namespace = "io.github.yuhj319.meowpower"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.github.yuhj319.meowpower"
        minSdk = 29
        targetSdk = 37
        versionCode = 700340
        versionName = "6.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v2 覆盖 Android 7.0+，v3 额外支持密钥轮换
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            // 有正式签名就用正式签名；keystore.properties 缺失时退回 debug 签名，保证仍可构建
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs["debug"]
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            // 只排除签名文件。不要 excludes += "**"：
            // 那会丢掉依赖库的 java 资源（含 ServiceLoader 等），
            // 而 src/main/resources 下的 META-INF/xposed/* AGP 默认就会打进 APK，无需 merges。
            excludes += "META-INF/*.SF"
            excludes += "META-INF/*.DSA"
            excludes += "META-INF/*.RSA"
            excludes += "META-INF/*.versions"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    compileOnly(libs.androidx.annotation)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
}

// AGP 9.4 的 produceXxxComposeMapping 任务需要 org.jetbrains.kotlin:compose-group-mapping，
// 但 AGP 内置的 Kotlin 2.2.10 没有对应的发布版本，会导致 release 构建失败。
// 该任务只影响 Compose 的 R8 group 映射优化，关掉不影响功能。
tasks.matching { it.name.contains("ComposeMapping") }.configureEach {
    enabled = false
}
