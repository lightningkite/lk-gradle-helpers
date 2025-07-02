package com.lightningkite.deployhelpers

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.decodeFromNativeReader
import net.peanuuutz.tomlkt.encodeToNativeWriter
import org.gradle.api.Project
import java.io.File

fun Project.useLocalDependencies() {
    val projectsLocation =
        (project.findProperty("branchModeProjectsFolder") as? String)?.let { File(it) } ?: project.rootDir.parentFile!!
    val versioningTomlFile = project.rootDir.resolve("gradle/libs.versions.toml")
    val versioningToml = versioningTomlFile.takeIf { it.exists() }?.let {
        Toml.decodeFromNativeReader(
            VersionsToml.serializer(),
            it.reader()
        )
    } ?: VersionsToml()
    val versioningTomlStart = versioningTomlFile.takeIf { it.exists() }?.let {
        Toml.decodeFromNativeReader(
            VersionsToml.serializer(),
            it.reader()
        )
    } ?: VersionsToml()
    val actuallyUseLocal = project.localProperties?.getProperty("useLocalDependencies", "false")?.toBoolean() ?: false
    fun local(gitUrl: String, vararg proposedArtifacts: String) {
        val matchingArtifacts = proposedArtifacts
            .mapNotNull {
                val matches = versioningToml.libraries.entries.find { e -> e.value.module == it }?.key
                    ?: versioningToml.plugins.entries.find { e -> e.value.id == it }?.key
                    ?: return@mapNotNull null
                matches to it
            }
            .associate { it }
        if(matchingArtifacts.isEmpty()) return
        if (!actuallyUseLocal) return
        val projectName = gitUrl.removeSuffix(".git").substringAfterLast('/')
        val camelCased = projectName.camelCase()
        println("Updating versions for ${matchingArtifacts.keys}")
        val folder = projectsLocation.resolve(projectName)

        if (!folder.exists()) {
            println("Cloning $gitUrl into $folder...")
            folder.parentFile.runCli("git", "clone", gitUrl)
            println("Building $gitUrl...")
            folder.runCliWithOutput("./gradlew", "publishToMavenLocal")
            println("Build finished.")
        }

        val version = folder.resolve("local.version.txt").takeIf { it.exists() }?.readText()
            ?: run {
                throw IllegalStateException(
                    "No findable local version in ${folder.resolve("local.version.txt")}; exists? ${
                        folder.resolve(
                            "local.version.txt"
                        ).exists()
                    }"
                )
            }

        versioningToml.versions[camelCased] = versionsTomlVersionStrictlyIfSnapshot(version)
        for ((name, artifact) in matchingArtifacts) {
            if(artifact.contains(':')) {
                // normal dependency
                versioningToml.libraries[name] =
                    VersionsTomlLibrary(artifact, versionsTomlVersionRef(camelCased))
            } else {
                // plugin
                versioningToml.plugins[name] =
                    VersionsTomlPlugin(artifact, versionsTomlVersionRef(camelCased))
            }
        }
    }

    // Insert known LK artifacts here
    local(
        "git@github.com:lightningkite/readable.git",
        "com.lightningkite:readable",
    )
    local(
        "git@github.com:lightningkite/kiteui.git",
        "com.lightningkite.kiteui:library",
        "com.lightningkite.kiteui"
    )
    local(
        "git@github.com:lightningkite/lightning-server-kiteui.git",
        "com.lightningkite.lightningserver:client",
    )
    local(
        "git@github.com:lightningkite/js-optimized-constructs.git",
        "com.lightningkite:js-optimized-constructs",
    )
    local(
        "git@github.com:lightningkite/kotlinx-serialization-csv-durable.git",
        "com.lightningkite:kotlinx-serialization-csv-durable",
    )

    local(
        "git@github.com:lightningkite/lightning-server.git",
        "com.lightningkite.lightningserver:processor",
        "com.lightningkite.lightningserver:shared",
        "com.lightningkite.lightningserver:server-aws",
        "com.lightningkite.lightningserver:server-azure",
        "com.lightningkite.lightningserver:server-clamav",
        "com.lightningkite.lightningserver:server-core",
        "com.lightningkite.lightningserver:server-testing",
        "com.lightningkite.lightningserver:server-dynamodb",
        "com.lightningkite.lightningserver:server-firebase",
        "com.lightningkite.lightningserver:server-ktor",
        "com.lightningkite.lightningserver:server-mcp",
        "com.lightningkite.lightningserver:server-media",
        "com.lightningkite.lightningserver:server-memcached",
        "com.lightningkite.lightningserver:server-mongo",
        "com.lightningkite.lightningserver:server-postgresql",
        "com.lightningkite.lightningserver:server-redis",
        "com.lightningkite.lightningserver:server-scim",
        "com.lightningkite.lightningserver:server-sentry",
        "com.lightningkite.lightningserver:server-sentry9",
        "com.lightningkite.lightningserver:server-sftp",
    )
    local(
        "git@github.com:lightningkite/kotlin-test-manual.git",
        "com.lightningkite.testing.manual",
        "com.lightningkite.testing:kotlin-test-manual-runtime",
    )

    if (versioningToml != versioningTomlStart) {
        println("Updating versions toml...")
        Toml.encodeToNativeWriter(
            TomlTable.serializer(),
            versioningToml.prettyTable(),
            versioningTomlFile.writer()
        )
        println("Done.")
    } else {
        println("Versions are up to date.")
    }
}
