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
include(":core:operation")
include(":core:storage:domain")
include(":core:storage:data")
include(":core:ui")
include(":feature:browser")
include(":feature:trash")
include(":feature:archive")
include(":feature:recentfiles")
 
