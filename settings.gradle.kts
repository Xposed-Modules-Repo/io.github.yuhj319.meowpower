pluginManagement {
    // AGP 内置的 Kotlin 编译器版本偏旧（2.2.x），而 Miuix 用 2.4.x 编译，
    // metadata 不兼容。这里显式把 Kotlin 插件版本顶上去。
    plugins {
        id("org.jetbrains.kotlin.android") version "2.4.20"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    }
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "ChargeFreedom"

include(":app")
