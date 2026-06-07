pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
    }
}

rootProject.name = "scope-benchmarks"

includeBuild("..") {
    dependencySubstitution {
        substitute(module("be.theking90000:scope")).using(project(":scope"))
    }
}
