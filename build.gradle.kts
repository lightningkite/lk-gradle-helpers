import nmcp.CentralPortalOptions
import java.net.URI
import java.util.*

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    signing
    `maven-publish`
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("com.gradleup.nmcp.aggregation").version("1.0.2")
}
val kotlinVersion = "1.9.0"

group = "com.lightningkite"

repositories {
    mavenCentral()
    google()
    maven(url = "https://plugins.gradle.org/m2/")
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(localGroovy())
    api(gradleApi())

    api(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version = kotlinVersion)
    api(group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin-api", version = kotlinVersion)
    implementation("net.peanuuutz.tomlkt:tomlkt:0.3.7")
    implementation("software.amazon.awssdk:s3:2.32.7")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.0.0")
    implementation("org.jetbrains:markdown:0.7.3")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.34.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
}

afterEvaluate {
    project.tasks.findByName("dokkaHtml")?.let { dokkaHtml ->
        val path = project.group.toString().replace('.', '/') + "/" + project.name + "/" + project.version + "/docs"
        println("Would send dokka files from ${dokkaHtml.outputs.files.singleFile} to $path")
    }
}

version = "4.0.2"

publishing {
    repositories {
        val lightningKiteMavenAwsAccessKey: String? = project.findProperty("lightningKiteMavenAwsAccessKey") as? String
        val lightningKiteMavenAwsSecretAccessKey: String? = project.findProperty("lightningKiteMavenAwsSecretAccessKey") as? String
        lightningKiteMavenAwsAccessKey?.let { ak ->
            maven {
                name = "LightningKite"
                url = URI.create("s3://lightningkite-maven")
                credentials(AwsCredentials::class) {
                    accessKey = ak
                    secretKey = lightningKiteMavenAwsSecretAccessKey!!
                }
            }
        }
    }
}
