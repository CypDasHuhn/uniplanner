plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.cypdashuhn.uniplanner.server.ServerKt"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":handlers"))
    implementation(project(":server-auth"))
    implementation("io.ktor:ktor-server-core:3.3.3")
    implementation("io.ktor:ktor-server-netty:3.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("ch.qos.logback:logback-classic:1.5.38")

    testImplementation("io.ktor:ktor-server-test-host:3.3.3")
    testImplementation(kotlin("test"))
}
