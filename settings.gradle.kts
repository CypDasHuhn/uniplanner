rootProject.name = "uniplanner"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("handler-api", "core", "cli")
