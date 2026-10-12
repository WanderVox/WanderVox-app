pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://alphacephei.com/maven/") }
    }
}

rootProject.name = "WanderVox"
include(":app")
// Capa Core
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:ui")

// Capa Features
include(":features:auth")
include(":features:translator")
include(":features:log")
include(":features:map")
include(":features:settings")
