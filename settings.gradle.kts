pluginManagement {
    repositories {
        google {  // 官方 Google Maven 仓库 - 优先
            content { includeGroupByRegex("com\\.android.*") }
            content { includeGroupByRegex("com\\.google.*") }
            content { includeGroupByRegex("androidx.*") }
        }
        mavenCentral()  // 官方 Maven Central - 优先
        gradlePluginPortal()  // 官方 Gradle Plugin Portal
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()  // 官方 Google Maven 仓库 - 优先
        mavenCentral()  // 官方 Maven Central - 优先
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "澪 Mio"
include(":app")
