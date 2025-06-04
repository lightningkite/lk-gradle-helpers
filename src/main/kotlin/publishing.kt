package com.lightningkite.deployhelpers

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.encodeToNativeWriter
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.Sign
import java.net.URI

fun Project.publishing() {
    project.repositories {
        mavenLocal()
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }

    project.publishing {
        val version = gitBasedVersion()
        project.version = version
        repositories {
            val lightningKiteMavenAwsAccessKey: String? =
                project.findProperty("lightningKiteMavenAwsAccessKey") as? String
            val lightningKiteMavenAwsSecretAccessKey: String? =
                project.findProperty("lightningKiteMavenAwsSecretAccessKey") as? String
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
    val signingKey: String? = project.findProperty("signingKey") as? String
    if (signingKey != null) {
        project.signing {
            isRequired = false
            val signingPassword: String? = project.findProperty("signingPassword") as? String
            if (signingKey != null) {
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
        }
    } else {
        project.afterEvaluate {
            project.tasks.withType<Sign>().configureEach {
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