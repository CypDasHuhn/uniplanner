plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":handler-api"))
    testImplementation(project(":core"))
    testImplementation(kotlin("test"))
}
