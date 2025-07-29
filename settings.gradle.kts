pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")

        mavenCentral()
    }
}

rootProject.name = "tv"
include(":app")
