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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MeshNet"

// App
include(":app")

// Core modules
include(":core:crypto")
include(":core:mesh")
include(":core:routing")
include(":core:storage")
include(":core:protocol")
include(":core:maps")
include(":core:sync")
include(":core:security")
include(":core:trust")
include(":core:metrics")
include(":core:events")
include(":core:flags")
include(":simulator")

// Feature modules
include(":feature:chat")
include(":feature:groups")
include(":feature:maps")
include(":feature:reports")
include(":feature:settings")
include(":feature:onboarding")

// Domain + Data
include(":domain")
include(":data")

// Benchmark
include(":benchmark")
