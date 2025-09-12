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
        maven(url = "https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")

        mavenCentral()
    }
}

rootProject.name = "tv"
include(":app")
