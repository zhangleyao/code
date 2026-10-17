pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像靠前
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/central") }
        maven { url = uri("https://jitpack.io") }
        // 兜底放原生源，镜像找不到的时候回头去原生mavenCentral拉取
        google()
        mavenCentral()
    }
}
rootProject.name = "DlnaBrowser"
include(":app")
