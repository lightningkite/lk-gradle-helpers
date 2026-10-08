import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import java.net.URI

buildscript {
    dependencies {
        classpath("software.amazon.awssdk:auth:2.55.5")
        classpath("software.amazon.awssdk:signin:2.55.5")
    }
}

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    signing
    `maven-publish`
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.dokka") version "2.2.0"
    id("com.gradleup.nmcp.aggregation").version("1.6.2")
}
val kotlinVersion = "2.4.20"

group = "com.lightningkite"

repositories {
    mavenCentral()
    google()
    maven(url = "https://plugins.gradle.org/m2/")
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class).all {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(localGroovy())
    api(gradleApi())

    api("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    api("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$kotlinVersion")
    implementation("net.peanuuutz.tomlkt:tomlkt:0.5.0")
    implementation("software.amazon.awssdk:s3:2.55.5")
    implementation("software.amazon.awssdk:signin:2.55.5")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.2.0")
    implementation("org.jetbrains:markdown:0.7.16")
    implementation("com.vanniktech.maven.publish:com.vanniktech.maven.publish.gradle.plugin:0.37.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation(gradleTestKit())
}

afterEvaluate {
    project.tasks.findByName("dokkaHtml")?.let { dokkaHtml ->
        val path = project.group.toString().replace('.', '/') + "/" + project.name + "/" + project.version + "/docs"
        println("Would send dokka files from ${dokkaHtml.outputs.files.singleFile} to $path")
    }
}

version = "4.1.2"

publishing {
    repositories {
        val lightningKiteMavenAwsAccessKey: String? = project.findProperty("lightningKiteMavenAwsAccessKey") as? String
        val lightningKiteMavenAwsSecretAccessKey: String? = project.findProperty("lightningKiteMavenAwsSecretAccessKey") as? String
        val lightningKiteMavenCredentials =
            if (lightningKiteMavenAwsAccessKey != null && lightningKiteMavenAwsSecretAccessKey != null)
                AwsBasicCredentials.create(lightningKiteMavenAwsAccessKey, lightningKiteMavenAwsSecretAccessKey)
            else
                runCatching { ProfileCredentialsProvider.create("lk").resolveCredentials() }.getOrNull()
        lightningKiteMavenCredentials?.let { creds ->
            maven {
                name = "LightningKite"
                url = URI.create("s3://lightningkite-maven")
                credentials(AwsCredentials::class) {
                    accessKey = creds.accessKeyId()
                    secretKey = creds.secretAccessKey()
                    sessionToken = (creds as? AwsSessionCredentials)?.sessionToken()
                }
            }
        }
    }
}
