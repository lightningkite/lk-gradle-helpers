import java.net.URI
import java.util.*

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    signing
    `maven-publish`
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

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
}

version = "main-SNAPSHOT"

publishing {
    repositories {
        val lightningKiteMavenAwsAccessKey: String? by project.properties
        val lightningKiteMavenAwsSecretAccessKey: String? by project.properties
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