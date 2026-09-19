pluginManagement {
    repositories {
        // Optional local mirror used by the offline verification harness.
        // GitHub Actions falls through to the standard repositories below.
        maven { url = uri("../tooling/local-maven") }
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("../tooling/local-maven") }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "PULSE-V172"
include(":app")
