pluginManagement {
  repositories {
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
      mavenContent { includeGroupAndSubgroups("io.github.goooler.golang") }
    }
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }
}

plugins {
  id("com.gradle.develocity") version "4.5.1"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/help/legal-terms-of-use"
    termsOfUseAgree = "yes"
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}

rootProject.name = "Tabby"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
  ":androidApp",
  ":core",
  ":core:model",
  ":service",
  ":core:database",
  ":service:remote",
  ":common",
  ":glue",
  ":ui",
  ":ui:crash",
  ":ui:home",
  ":ui:log",
  ":ui:proxy",
  ":ui:profile",
  ":ui:settings",
)
