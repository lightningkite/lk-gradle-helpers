package com.lightningkite.deployhelpers

import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.encodeToNativeWriter
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.URI
import kotlin.text.set
import kotlin.toString

fun Project.lkPublishing(githubOrg: String, githubRepo: String, pom: MavenPom.()->Unit) {
    project.repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }

    afterEvaluate {
        val lightningKiteMavenAwsAccessKey: String? =
            project.findProperty("lightningKiteMavenAwsAccessKey") as? String
        val lightningKiteMavenAwsSecretAccessKey: String? =
            project.findProperty("lightningKiteMavenAwsSecretAccessKey") as? String
        project.publishing {
            repositories {
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

        lightningKiteMavenAwsAccessKey?.let { ak ->
            dokkaUploadTask(ak, lightningKiteMavenAwsSecretAccessKey!!)
        }

        val signingKey: String? = project.findProperty("signingKey") as? String
        if (signingKey != null) {
            println("Signing key found for ${project.name}")
            project.signing {
                println("Running signing config")
                isRequired = false
                val signingPassword: String? = project.findProperty("signingPassword") as? String
                if (signingKey != null) {
                    println("Signing password found; will sign")
                    useInMemoryPgpKeys(signingKey, signingPassword)
                } else {
                    println("No signing password found.")
                }
            }
        } else {
            project.afterEvaluate {
                println("Signing key NOT found for ${project.name}")
                project.tasks.withType<Sign>().configureEach {
                    println("Disabling signing for ${this.name}")
                    onlyIf("'signingKey' is present") { false }
                }
            }

        }
        project.afterEvaluate {
            project.tasks.findByName("publishToMavenLocal")?.doLast {
                project.rootDir.resolve("local.version.txt").writeText(project.version.toString())
            }
        }

        //Ensure cleanliness precommit hook
        project.rootDir.resolve(".git/hooks/pre-commit").let {
            if ((!it.exists() || it.readText().startsWith("#!/bin/bash")) && it.parentFile!!.exists()) {
                it.writeText(
                    """
                    #!/bin/sh
                    echo "Checking for 'snapshot' in versions..."
                    if grep SNAPSHOT gradle/libs.versions.toml; then
                      echo "There's a 'snapshot' version in your libs.versions.toml!  You need to have clean versions before you commit."
                      exit 1
                    fi
                """.trimIndent()
                )
                it.setExecutable(true)
            }
        }

        // Ensure version file in git ignore
        project.rootDir.resolve(".gitignore").let {
            val withLs = System.lineSeparator() + "local.version.txt" + System.lineSeparator()
            if (it.exists() && !it.readText().contains(withLs)) {
                it.appendText(withLs)
            }
        }
    }
    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        signAllPublications()
        coordinates(group.toString(), name, version.toString())
        configureBasedOnAppliedPlugins(
            sourcesJar = true,
            javadocJar = version.toString().all { it.isDigit() || it == '.' } || localProperties?.getProperty("forceDokka") == "true"
        )
        pom(configure = {
            name.set(this@lkPublishing.name)
            github(githubOrg, githubRepo)
            url.set(dokkaPublicHostingIndex)
            pom()
            licenses { apache() }
            developers {
                joseph()
                brady()
                hunter()
            }
        })
    }
}