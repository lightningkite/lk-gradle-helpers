package com.lightningkite.deployhelpers

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.dokka.gradle.engine.plugins.DokkaPluginParametersBaseSpec
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi
import org.jetbrains.dokka.gradle.tasks.DokkaGeneratePublicationTask
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import java.net.URI
import java.net.URL
import javax.inject.Inject


val Project.dokkaPublicHostingRootPath: String
    get() = group.toString().replace('.', '/') + "/" + name + "/docs"
val Project.dokkaPublicHostingPath: String
    get() = group.toString().replace('.', '/') + "/" + name + "/" + version + "/docs"
val Project.dokkaPublicHostingIndex: String get() = "https://lightningkite-maven.s3.amazonaws.com/$dokkaPublicHostingPath/index.html"

fun Project.dokkaUploadTask(accessKey: String, secret: String) {
    (project.tasks.findByName("dokkaGeneratePublicationHtml") ?: project.tasks.findByName("dokkaHtml"))?.let { dokkaHtml ->
        val publishDokka = project.tasks.create("publishDokkaToS3") {
            dependsOn(dokkaHtml)
            group = "publishing"
            val dir = dokkaHtml.outputs.files.single { !it.toString().contains("tmp") }
            inputs.files(dir)
            doFirst {
                dir.uploadDirectoryToS3(
                    bucket = "lightningkite-maven",
                    keyPrefix = project.dokkaPublicHostingPath,
                    credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secret)),
                ).also {
                    println("Published docs to $it")
                }
                uploadFilesToS3(
                    bucket = "lightningkite-maven",
                    keyPrefix = project.dokkaPublicHostingRootPath,
                    keys = mapOf(
                        "index.html" to """
                            <!DOCTYPE HTML>
                            <html lang="en-US">
                            <head>
                                <meta charset="UTF-8">
                                <meta http-equiv="refresh" content="0; url=$dokkaPublicHostingIndex">
                                <script type="text/javascript">
                                    window.location.href = "$dokkaPublicHostingIndex"
                                </script>
                                <title>Page Redirection</title>
                            </head>
                            <body>
                            <!-- Note: don't tell people to `click` the link, just tell them that it is a link. -->
                            If you are not redirected automatically, follow this <a href='$dokkaPublicHostingIndex'>link</a>.
                            </body>
                            </html>
                        """.trimIndent()
                    ),
                    credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secret)),
                ).also {
                    println("Published additional cross-version content to $it")
                }
            }
        }
        project.tasks.getByName("publish").dependsOn(publishDokka)
        project.tasks.create("publishPublic") {
            group = "publishing"
            dependsOn(project.tasks.getByName("publishAllPublicationsToMavenCentralRepository"))
            dependsOn(project.tasks.getByName("publishDokkaToS3"))
        }
    }
}

fun Project.setupDokka(group: String = "lightningkite", repo: String) {
    dependencies {
        add("dokkaPlugin", "com.lightningkite:dokka-plugin-hide-optin:0.0.1--local")
    }
    configure<DokkaExtension> {
        dokkaSourceSets.configureEach {
            // used as project name in the header
//                moduleName.set("Dokka Gradle Example")

            // contains descriptions for the module and the packages
            (
                    this.sourceRoots
                        .mapNotNull { it.resolve("module.md") }
                        .firstOrNull { it.exists() }
                    )
                ?.let {
                    includes.from(it)
                }

            // adds source links that lead to this repository, allowing readers
            // to easily find source code for inspected declarations
            sourceLink {
                localDirectory.set(file("src/${name}/kotlin"))
                remoteUrl.set(URI("https://github.com/$group/$repo/tree/master/"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

