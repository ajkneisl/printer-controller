import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.6.21"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
}

group = "dev.ajkneisl"
version = "1.0-SNAPSHOT"
val ktor_version = "2.0.0"

repositories {
    mavenCentral()

    maven {
        url = uri("https://maven.pkg.github.com/ajkneisl/printer-lib")

        credentials {
            username = "ajkneisl"
            password = System.getenv("GHP_GITHUB")
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")

    implementation("com.github.anastaciocintra:escpos-coffee:4.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.litote.kmongo:kmongo:4.7.2")
    implementation("org.litote.kmongo:kmongo-serialization:4.7.2")
    implementation("org.litote.kmongo:kmongo-coroutine:4.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5")

    implementation("org.apache.logging.log4j:log4j-core:2.19.0")
    implementation("ch.qos.logback:logback-classic:1.4.4")

    implementation("dev.ajkneisl:printerlib:1.2.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("dev.ajkneisl.printercontroller.Controller")
}