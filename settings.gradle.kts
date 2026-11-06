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
        // Mozilla's Maven repo hosts GeckoView (stable channel = org.mozilla.geckoview:geckoview).
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content { includeGroupByRegex("org\\.mozilla.*") }
        }
    }
}

rootProject.name = "Airlock"
include(":app")
