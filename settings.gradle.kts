pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        flatDir {
            dirs("libs")
        }
    }

}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        flatDir {
            dirs("libs")
        }
        mavenCentral()
        maven(url = "https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/") {
            mavenContent { snapshotsOnly() }
        }
    }
}

rootProject.name = "tv"
include(":app")
