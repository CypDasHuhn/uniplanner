rootProject.name = "uniplanner"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("handler-api", "core", "handlers", "cli", "js-api", "web-demo", "server-auth", "server")
