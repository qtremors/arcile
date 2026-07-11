pluginManagement {
    includeBuild("build-logic")
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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Arcile"
include(":app")
include(":core:runtime")
include(":core:operation:api")
include(":core:operation:android")
include(":core:plugin:android")
include(":core:navigation:api")
include(":core:presentation")
include(":core:storage:domain")
include(":core:storage:data")
include(":core:ui")
include(":core:testing")
include(":core:ui:testing")
include(":feature:browser")
include(":feature:trash")
include(":feature:archive")
include(":feature:recentfiles")
include(":feature:imagegallery")
include(":feature:onboarding")
include(":feature:quickaccess")
include(":feature:storagecleaner")
include(":feature:storageusage")
include(":feature:home")
include(":feature:settings")
include(":feature:activitylog")
include(":feature:plugins")
include(":feature:import")
include(":plugin-api")
include(":plugin-ui")
include(":plugin-glb")
 
