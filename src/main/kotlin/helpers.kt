package com.lightningkite.deployhelpers

import com.lightningkite.deployhelpers.publishing
import com.lightningkite.deployhelpers.signing
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlInline
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.buildTomlTable
import net.peanuuutz.tomlkt.decodeFromNativeReader
import net.peanuuutz.tomlkt.element
import net.peanuuutz.tomlkt.encodeToNativeWriter
import net.peanuuutz.tomlkt.literal
import net.peanuuutz.tomlkt.table
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.Sign
import org.jetbrains.kotlin.gradle.plugin.extraProperties
import org.jetbrains.kotlin.gradle.targets.js.npm.min
import java.io.File
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime
import java.util.WeakHashMap
import kotlin.text.toBoolean

private class MarkerClass

fun MavenPom.github(group: String = "lightningkite", repo: String) {
    url.set("https://github.com/$group/$repo")
    scm {
        connection.set("scm:git:https://github.com/$group/$repo.git")
        developerConnection.set("scm:git:https://github.com/$group/$repo.git")
        url.set("https://github.com/$group/$repo")
    }
}

fun MavenPomLicenseSpec.mit() {
    license {
        name.set("The MIT License (MIT)")
        url.set("https://www.mit.edu/~amini/LICENSE.md")
        distribution.set("repo")
    }
}

fun MavenPomDeveloperSpec.joseph() {
    developer {
        id.set("LightningKiteJoseph")
        name.set("Joseph Ivie")
        email.set("joseph@lightningkite.com")
    }
}

fun MavenPomDeveloperSpec.brady() {
    developer {
        id.set("bjsvedin")
        name.set("Brady Svedin")
        email.set("brady@lightningkite.com")
    }
}

private val loader = MarkerClass::class.java.classLoader
private fun Project.setupGitHubAction(path: String) {
    loader.getResource("githubActions/$path")!!.openStream()
        .copyTo(rootDir.resolve(".github/workflows/${path.substringAfterLast('/')}").apply { parentFile.mkdirs() }
            .outputStream())
}

internal fun File.runCli(vararg args: String): String {
    val process = ProcessBuilder(*args)
        .directory(this)
        .start()
    process.outputStream.close()
    val result = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
    val resultErr = process.errorStream.readAllBytes().toString(Charsets.UTF_8)
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw Exception("Unexpected exit code ${exitCode} from ${args.joinToString(" ")}: $resultErr $result")
    }
    return result
}

internal fun File.getGitCommitTime(): OffsetDateTime =
    OffsetDateTime.parse(runCli("git", "show", "--no-patch", "--format=%ci", "HEAD").trim())

internal fun File.getGitBranch(): String = runCli("git", "rev-parse", "--abbrev-ref", "HEAD").trim()
internal fun File.getGitTag(): Version? = try {
    runCli("git", "describe", "--exact-match", "--tags").trim().let(Version::fromString)
} catch (e: Exception) {
    null
}

internal fun File.getGitHash(): String = runCli("git", "rev-parse", "HEAD").trim()
internal data class GitStatus(
    val raw: String,
    val branch: String,
    val workingTreeClean: Boolean,
    val ahead: Int,
    val behind: Int,
) {
    val fullyPushed get() = workingTreeClean && ahead == 0 && behind == 0
}

internal fun File.getGitStatus(): GitStatus = runCli("git", "status").let {
    GitStatus(
        raw = it,
        branch = it.substringAfter("On branch ", "").substringBefore('\n').trim(),
        workingTreeClean = it.contains("working tree clean", ignoreCase = true),
        ahead = it.substringAfter("Your branch is ahead", "")
            .substringAfter('\'')
            .substringAfter('\'')
            .substringAfter("by ")
            .substringBefore(" commits")
            .toIntOrNull() ?: it.substringBefore(" different commits each, respectively", "")
            .substringAfter("and have ")
            .substringAfter(" and ")
            .toIntOrNull() ?: 0,
        behind = it.substringAfter("Your branch is behind", "")
            .substringAfter('\'')
            .substringAfter('\'')
            .substringAfter("by ")
            .substringBefore(" commits")
            .toIntOrNull() ?: it.substringBefore(" different commits each, respectively", "")
            .substringAfter("and have ")
            .substringBefore(" and ")
            .toIntOrNull() ?: 0,
    )
}

data class Version(val major: Int, val minor: Int, val patch: Int, val postdash: String? = null) : Comparable<Version> {
    override fun compareTo(other: Version): Int = comparator.compare(this, other)

    companion object {
        private val comparator = compareBy(Version::major, Version::minor, Version::patch)
        fun fromString(string: String): Version {
            val parts = string.substringBefore('-').split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: -1
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: -1
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: -1
            return Version(major, minor, patch, string.substringAfter('-', "").takeUnless { it.isBlank() })
        }
    }

    override fun toString(): String = "$major.$minor.$patch" + (postdash?.let { "-$it" } ?: "")
    fun incrementPatch() = copy(patch = patch + 1)
}

internal fun File.gitLatestTag(major: Int, minor: Int): Version? {
    return runCli("git", "tag", "-l", "--sort=-version:refname", "$major.$minor.*")
        .lines()
        .also { println("All matching tags: ${it.joinToString(", ")}") }
        .firstOrNull()
        ?.takeUnless { it.isBlank() }
        ?.trim()
        ?.let(Version::fromString)
}

internal fun File.gitTagHash(tag: String): String = runCli("git", "rev-list", "-n", "1", tag).trim()
internal fun File.gitBasedVersion(versionMajor: Int, versionMinor: Int, canCreateTag: Boolean = true): Version? {
    runCli("git", "fetch", "--tags", "--force") // ensure we're up to date
    val status = getGitStatus()
    if (!status.fullyPushed) {
        println("Not fully pushed, using snapshot version.  Raw status of Git: ${status.raw}")
        return null
    }
    val current = getGitTag()
    println("Current tag: $current")
    return if (current != null) {
        // OK, we've already made the tag!
        println("Tag is up to date.")
        current
    } else if(canCreateTag) {
        val latest = gitLatestTag(versionMajor, versionMinor)
        println("Creating new tag...")
        val newTag = (latest ?: Version(versionMajor, versionMinor, -1)).incrementPatch()
        runCli("git", "tag", newTag.toString())
        runCli("git", "push", "origin", "tag", newTag.toString())
        println("New tag created.")
        newTag
    } else null
}

fun latestFromRemote(group: String, artifact: String, major: Int, minor: Int? = null): String {
    val versions = URL(
        "https://lightningkite-maven.s3.us-west-2.amazonaws.com/${
            group.replace(
                '.',
                '/'
            )
        }/$artifact/maven-metadata.xml"
    )
        .let { XmlParser().parse(it.openStream()) }
        .get("versioning")
        .let { it as NodeList }
        .first()
        .let { it as Node }
        .get("versions")
        .let { it as NodeList }
        .first()
        .let { it as Node }
        .children()
        .map {
            Version.fromString((it as Node).text())
        }
    return versions
        .also { println("Versions: $it") }
        .filter { it.major == major && (minor == null || it.minor == minor) }
        .also { println("Versions matching: $it") }
        .max()
        .toString()
}

fun latestFromRemote(id: String, major: Int, minor: Int? = null): String {
    val versions = URL(
        "https://lightningkite-maven.s3.us-west-2.amazonaws.com/$id/$id.gradle.plugin/maven-metadata.xml"
    )
        .let { XmlParser().parse(it.openStream()) }
        .get("versioning")
        .let { it as NodeList }
        .first()
        .let { it as Node }
        .get("versions")
        .let { it as NodeList }
        .first()
        .let { it as Node }
        .children()
        .map {
            Version.fromString((it as Node).text())
        }
    return versions
        .also { println("Versions: $it") }
        .filter { it.major == major && (minor == null || it.minor == minor) }
        .also { println("Versions matching: $it") }
        .max()
        .toString()
}

fun Project.lk(setup: LkGradleHelpers.() -> Unit) = LkGradleHelpers(this).also(setup)

@Serializable
data class VersionsToml(
    val versions: HashMap<String, TomlElement> = HashMap(),
    val libraries: HashMap<String, VersionsTomlLibrary> = HashMap(),
    val plugins: HashMap<String, VersionsTomlPlugin> = HashMap(),
)

@Serializable
data class VersionsTomlLibrary(
    val module: String = "",
    @TomlInline val version: TomlElement = TomlLiteral("0.0.0")
)

@Serializable
data class VersionsTomlPlugin(
    val id: String = "",
    @TomlInline val version: TomlElement = TomlLiteral("0.0.0")
)

fun versionsTomlVersionStrictly(version: String) = TomlTable(mapOf("strictly" to TomlLiteral(version)))
fun versionsTomlVersionRef(refName: String) = TomlTable(mapOf("ref" to TomlLiteral(refName)))
fun versionsTomlVersionDirect(version: String) = TomlLiteral(version)
fun versionsTomlVersionStrictlyIfSnapshot(version: String) = if(version.contains("snapshot", true)) versionsTomlVersionStrictly(version) else versionsTomlVersionDirect(version)

fun VersionsToml.prettyTable(): TomlTable = buildTomlTable {
    table("versions") {
        for (entry in versions.entries.sortedBy { it.key }) {
            element(entry.key, entry.value)
        }
    }
    table("libraries") {
        for (entry in libraries.entries.sortedBy { it.key }) {
            val e = Toml.encodeToTomlElement(VersionsTomlLibrary.serializer(), entry.value)
            element(entry.key, e, TomlInline())
        }
    }
    table("plugins") {
        for (entry in plugins.entries.sortedBy { it.key }) {
            val e = Toml.encodeToTomlElement(VersionsTomlPlugin.serializer(), entry.value)
            element(entry.key, e, TomlInline())
        }
    }
}

val permitCheckout = false

@Deprecated("Remove minor, now set with gradle property 'versionMinor'")
fun Project.lk(minor: Int, setup: LkGradleHelpers.() -> Unit = {}) = LkGradleHelpers(this).also(setup)
class LkGradleHelpers(val project: Project) {
    val branch = project.rootDir.getGitBranch()
    val versionMajor = branch.let {
        if (it.startsWith("version-")) it.removePrefix("version-").substringBefore('-').toIntOrNull() ?: 0
        else 0
    }
    val versionBranch = branch.removePrefix("version-").substringAfter('-', "").takeUnless { it.isBlank() }
    val versionMinor: Int = (project.findProperty("versionMinor") as? String)?.toIntOrNull() ?: 0
    fun gitBasedVersion(): String {
        val (runId, existingVersion) = (project.rootProject.extraProperties.let {
            if (it.has("gitBasedVersion")) it.get(
                "gitBasedVersion"
            ) as? String else null
        } ?: "\n")
            .split('\n')
        val myRunId = System.identityHashCode(project.gradle.startParameter).toString()
        if (myRunId == runId) return existingVersion
        val result = project.rootDir.gitBasedVersion(versionMajor, versionMinor, canCreateTag = versionBranch == null)?.toString()
            ?: project.rootDir.getGitBranch().plus("-SNAPSHOT")
        project.rootProject.extraProperties.set("gitBasedVersion", "$myRunId\n$result")
        return result
    }

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
    val branchModeProjectsFolder: String? = (
            (project.findProperty("branchModeProjectsFolderOverride") as? String)
                ?: (project.findProperty("branchModeProjectsFolder") as? String)
            )
        ?.takeUnless { it.isBlank() || it == "off" || it == "false" }
        .also { if (it != null) println("Branch mode active ($it)") }
    val upgradeLockToLatest: Boolean = (
            (project.findProperty("upgradeLockToLatestOverride") as? String)
                ?: (project.findProperty("upgradeLockToLatest") as? String)
            )
        ?.takeUnless { it.isBlank() || it == "off" || it == "false" }
        ?.toBoolean()
        .let { it ?: false }
        .also { if (it) println("Upgrade lock to latest enabled") }

    private fun requireProjectAndGetVersion(gitUrl: String, major: Int): String {
        val projectName = gitUrl.removeSuffix(".git").substringAfterLast('/')
        val folder = File(branchModeProjectsFolder!!).resolve(projectName)
        var needsBuild = !folder.resolve("local.version.txt").exists()
        if (!folder.exists()) {
            println("Cloning $gitUrl into $folder...")
            folder.parentFile.runCli("git", "clone", gitUrl)
            needsBuild = true
        }
        if (major != 0) {
            if (!folder.getGitBranch().startsWith("version-$major")) {
                if (folder.getGitStatus().let { it.workingTreeClean && it.ahead == 0 } && permitCheckout) {
                    println("Checking out version-$major for ${folder}...")
                    folder.runCli("git", "checkout", "version-$major")
                    needsBuild = true
                } else {
                    throw IllegalStateException("$folder: Need to get to a clean state before you can switch branches to 'version-$major'.  It's currently unclean on '${folder.getGitStatus()}'")
                }
            }
        }
        if (needsBuild) {
            println("Building subproject at ${folder}...")
            folder.runCli("./gradlew", "publishToMavenLocal")
            println("Built subproject at ${folder}.")
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
        return version
    }

    fun mavenOrLocalPlugin(gitUrl: String, id: String, major: Int, minor: Int? = null): String {
        val camelCased = id.camelCase()
        return branchModeProjectsFolder?.let(::File)?.let {
            val version = requireProjectAndGetVersion(gitUrl, major)
            versioningToml.plugins[camelCased] =
                VersionsTomlPlugin(id = id, version = versionsTomlVersionStrictlyIfSnapshot(version))
            "$id:$version"
        } ?: run {
            fun useLatest() = latestFromRemote(id, major, minor).also { version ->
                versioningToml.plugins[camelCased] =
                    VersionsTomlPlugin(id = id, version = versionsTomlVersionDirect(version))
            }

            val lockVersion =
                if (upgradeLockToLatest) useLatest()
                else versioningToml.plugins[camelCased]?.version?.let { it as? TomlLiteral }?.content ?: useLatest()
            "$id:$lockVersion"
        }
    }

    fun mavenOrLocal(gitUrl: String, group: String, artifact: String, major: Int, minor: Int? = null): String {
        val camelCased = "$group $artifact".camelCase()
        return branchModeProjectsFolder?.let(::File)?.let {
            val version = requireProjectAndGetVersion(gitUrl, major)
            versioningToml.libraries[camelCased] =
                VersionsTomlLibrary(module = "$group:$artifact", version = versionsTomlVersionStrictlyIfSnapshot(version))
            "$group:$artifact:$version"
        } ?: run {
            fun useLatest() = latestFromRemote(group, artifact, major, minor).also { version ->
                versioningToml.libraries[camelCased] =
                    VersionsTomlLibrary(module = "$group:$artifact", version = versionsTomlVersionDirect(version))
            }

            val lockVersion =
                if (upgradeLockToLatest) useLatest()
                else versioningToml.libraries[camelCased]?.version?.let { it as? TomlLiteral }?.content ?: useLatest()
            "$group:$artifact:$lockVersion"
        }
    }

    init {
        project.repositories {
            if (branchModeProjectsFolder != null) mavenLocal()
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
            if (versioningToml != versioningTomlStart) {
                println("Updating versions toml...")
                Toml.encodeToNativeWriter(
                    TomlTable.serializer(),
                    versioningToml.prettyTable(),
                    versioningTomlFile.writer()
                )
                println("Done.")
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
}

fun LkGradleHelpers.readable(major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/readable.git",
    group = "com.lightningkite",
    artifact = "readable",
    major = major,
    minor = minor
)

fun LkGradleHelpers.kiteUi(major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/kiteui.git",
    group = "com.lightningkite.kiteui",
    artifact = "library",
    major = major,
    minor = minor
)

fun LkGradleHelpers.kiteUiPlugin(major: Int, minor: Int? = null) = mavenOrLocalPlugin(
    gitUrl = "git@github.com:lightningkite/kiteui.git",
    id = "com.lightningkite.kiteui",
    major = major,
    minor = minor
)

fun LkGradleHelpers.lightningServerKiteUiClient(major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/lightning-server-kiteui.git",
    group = "com.lightningkite.lightningserver",
    artifact = "client",
    major = major,
    minor = minor
)

fun LkGradleHelpers.jsOptimizedConstructs(major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/js-optimized-constructs.git",
    group = "com.lightningkite",
    artifact = "js-optimized-constructs",
    major = major,
    minor = minor
)

fun LkGradleHelpers.lightningServer(artifact: String, major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/lightning-server.git",
    group = "com.lightningkite.lightningserver",
    artifact = artifact,
    major = major,
    minor = minor
)

fun LkGradleHelpers.kotlinTestManualPlugin(major: Int = 0, minor: Int? = null) = mavenOrLocalPlugin(
    gitUrl = "git@github.com:lightningkite/kotlin-test-manual.git",
    id = "com.lightningkite.testing.manual",
    major = major,
    minor = minor
)

fun LkGradleHelpers.kotlinTestManualRuntime(major: Int = 0, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/kotlin-test-manual.git",
    group = "com.lightningkite.testing",
    artifact = "kotlin-test-manual-runtime",
    major = major,
    minor = minor
)

class LightningServerPackageSelector(val lkGradleHelpers: LkGradleHelpers, val major: Int, val minor: Int?)

fun LkGradleHelpers.lightningServer(major: Int, minor: Int? = null) = LightningServerPackageSelector(this, major, minor)
val LightningServerPackageSelector.processor get() = lkGradleHelpers.lightningServer("processor", major, minor)
val LightningServerPackageSelector.shared get() = lkGradleHelpers.lightningServer("shared", major, minor)
val LightningServerPackageSelector.aws get() = lkGradleHelpers.lightningServer("server-aws", major, minor)
val LightningServerPackageSelector.azure get() = lkGradleHelpers.lightningServer("server-azure", major, minor)
val LightningServerPackageSelector.clamav get() = lkGradleHelpers.lightningServer("server-clamav", major, minor)
val LightningServerPackageSelector.core get() = lkGradleHelpers.lightningServer("server-core", major, minor)
val LightningServerPackageSelector.testing get() = lkGradleHelpers.lightningServer("server-testing", major, minor)
val LightningServerPackageSelector.dynamodb get() = lkGradleHelpers.lightningServer("server-dynamodb", major, minor)
val LightningServerPackageSelector.firebase get() = lkGradleHelpers.lightningServer("server-firebase", major, minor)
val LightningServerPackageSelector.ktor get() = lkGradleHelpers.lightningServer("server-ktor", major, minor)
val LightningServerPackageSelector.memcached get() = lkGradleHelpers.lightningServer("server-memcached", major, minor)
val LightningServerPackageSelector.mongo get() = lkGradleHelpers.lightningServer("server-mongo", major, minor)
val LightningServerPackageSelector.postgresql get() = lkGradleHelpers.lightningServer("server-postgresql", major, minor)
val LightningServerPackageSelector.redis get() = lkGradleHelpers.lightningServer("server-redis", major, minor)
val LightningServerPackageSelector.scim get() = lkGradleHelpers.lightningServer("server-scim", major, minor)
val LightningServerPackageSelector.sentry get() = lkGradleHelpers.lightningServer("server-sentry", major, minor)
val LightningServerPackageSelector.sentry9 get() = lkGradleHelpers.lightningServer("server-sentry9", major, minor)
val LightningServerPackageSelector.sftp get() = lkGradleHelpers.lightningServer("server-sftp", major, minor)


val `casing separator regex` = Regex("([-_.\\s]+([A-Z]*[a-z0-9]+))|([.-_\\s]*[A-Z]+)")
inline fun String.caseAlter(crossinline update: (after: String) -> String): String =
    `casing separator regex`.replace(this) {
        if (it.range.start == 0) it.value
        else update(it.value.filter { !(it == '-' || it == '_' || it.isWhitespace() || it == '.') })
    }


fun String.titleCase() = caseAlter { " " + it.capitalize() }.capitalize()
fun String.spaceCase() = caseAlter { " " + it }.decapitalize()
fun String.kabobCase() = caseAlter { "-$it" }.toLowerCase()
fun String.snakeCase() = caseAlter { "_$it" }.toLowerCase()
fun String.screamingSnakeCase() = caseAlter { "_$it" }.toUpperCase()
fun String.camelCase() = caseAlter { it.capitalize() }.decapitalize()
fun String.pascalCase() = caseAlter { it.capitalize() }.capitalize()