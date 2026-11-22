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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Tradingin Aja"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":domain")
include(":data")
include(":feature:market")
include(":feature:trade")
include(":feature:portfolio")
include(":feature:learn")
include(":feature:profile")
