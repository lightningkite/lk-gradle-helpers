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
    upgradeVersions(versioningToml, actuallyUseLocal, projectsLocation)

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

internal fun upgradeVersions(
    versioningToml: VersionsToml,
    actuallyUseLocal: Boolean,
    projectsLocation: File
) {
    fun local(gitUrl: String, match: (String) -> Boolean) {
        val matchingKeys = listOf(
            versioningToml.libraries.entries.filter { match(it.value.module) }.map { it.key },
            versioningToml.plugins.entries.filter { match(it.value.id) }.map { it.key }
        ).flatten().toSet()
//        val matchingArtifacts = proposedArtifacts
//            .mapNotNull {
//                val matches = versioningToml.libraries.entries.find { e -> e.value.module == it }?.key
//                    ?: versioningToml.plugins.entries.find { e -> e.value.id == it }?.key
//                    ?: return@mapNotNull null
//                matches to it
//            }
//            .associate { it }
        if (matchingKeys.isEmpty()) return
        if (!actuallyUseLocal) return
        val projectName = gitUrl.removeSuffix(".git").substringAfterLast('/')
        val camelCased = projectName.camelCase()
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

        println("Updating versions for ${matchingKeys} to ${version}")
        versioningToml.versions[camelCased] = versionsTomlVersionStrictlyIfSnapshot(version)
        for (key in matchingKeys) {
            versioningToml.libraries[key]?.let { it ->
                versioningToml.libraries[key] = it.copy(version = versionsTomlVersionRef(camelCased))
            } ?: versioningToml.plugins[key]?.let { it ->
                versioningToml.plugins[key] = it.copy(version = versionsTomlVersionRef(camelCased))
            }
        }
    }

    fun local(gitUrl: String, startsWith: String) {
        local(gitUrl) { it.startsWith(startsWith) }
    }

    fun local(gitUrl: String, artifacts: Collection<String>) {
        local(gitUrl) { it in artifacts }
    }

    // Insert known LK artifacts here
    local(
        "git@github.com:lightningkite/readable.git",
        "com.lightningkite:readable",
    )
    local(
        "git@github.com:lightningkite/reactive.git",
        "com.lightningkite:reactive",
    )
    local(
        "git@github.com:lightningkite/kiteui.git",
        "com.lightningkite.kiteui"
    )
    local(
        "git@github.com:lightningkite/lightning-server-kiteui.git",
        listOf(
            "com.lightningkite.lightningserver:client",
            "com.lightningkite.lightningserver:server-client-utils",
        )
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
        "git@github.com:lightningkite/service-abstractions.git",
        "com.lightningkite.services",
    )

    local(
        "git@github.com:lightningkite/lightning-server.git",
    ) {
        it.startsWith("com.lightningkite.lightningserver") && !it.startsWith("com.lightningkite.lightningserver:client") && !it.startsWith("com.lightningkite.lightningserver:server-client-utils")
    }
    local(
        "git@github.com:lightningkite/kotlin-test-manual.git",
        listOf(
            "com.lightningkite.testing.manual",
            "com.lightningkite.testing:kotlin-test-manual-runtime",
        )
    )
}
