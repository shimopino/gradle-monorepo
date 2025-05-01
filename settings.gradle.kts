pluginManagement {
    repositories { 
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
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