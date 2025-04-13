plugins {
    val kotlinVersion = "2.0.0"
    application
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

group = "cash.atto"

application {
    mainClass.set("cash.atto.BenchmarkKt")
    applicationDefaultJvmArgs = listOf("-Xms1g", "-Xmx2g")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    val commonsVersion = "3.3.1"
    val testcontainersVersion = "1.19.5"
    val ktorVersion = "3.1.2"

    implementation("cash.atto:commons-core:$commonsVersion")
    implementation("cash.atto:commons-worker:$commonsVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jetty:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-util-io:2.1.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("org.testcontainers:testcontainers:$testcontainersVersion")
    implementation("org.testcontainers:mysql:$testcontainersVersion")
    implementation("io.micrometer:micrometer-core:1.12.3")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.3")

    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    implementation("org.awaitility:awaitility:4.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}