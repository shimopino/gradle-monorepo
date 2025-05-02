pluginManagement {
    repositories { 
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
    // https://docs.gradle.org/current/userguide/version_catalogs.html#sec:importing-catalog-from-file
    // Gradleは名前から自動読み取りを行うためファイル指定は不要
    versionCatalogs {
        create("libs")
    }
}

rootProject.name = "gradle-playground"

include("app1")
project(":app1").projectDir = file("packages/app1")

include("common")
project(":common").projectDir = file("packages/common")

include("lambda")
project(":lambda").projectDir = file("packages/lambda")

include("lambda-test")
project(":lambda-test").projectDir = file("packages/lambda-test")