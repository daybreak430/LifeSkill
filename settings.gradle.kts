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
        mavenCentral()
        exclusiveContent {
            forRepository { maven("https://maven-central.storage-download.googleapis.com/maven2") }
            filter {
                includeGroup("io.github.kyant0")
                includeGroup("com.tom-roush")
                includeGroup("org.bouncycastle")
            }
        }
    }
}

rootProject.name = "LifeSkill"
include(":app")
