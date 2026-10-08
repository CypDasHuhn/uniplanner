plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.cypdashuhn.uniplanner.cli.MainKt"
}

dependencies {
    implementation(project(":core"))
}
