package com.lightningkite.deployhelpers

import com.lightningkite.deployhelpers.publishing
import com.lightningkite.deployhelpers.signing
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import org.gradle.api.Project
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.internal.extensions.core.extra
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.targets.js.npm.min
import java.io.File
import java.net.URI
import java.net.URL
import java.time.OffsetDateTime
import java.util.WeakHashMap

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
    return process.inputStream.readAllBytes().toString(Charsets.UTF_8)
}

internal fun File.getGitCommitTime(): OffsetDateTime =
    OffsetDateTime.parse(runCli("git", "show", "--no-patch", "--format=%ci", "HEAD").trim())

internal fun File.getGitBranch(): String = runCli("git", "rev-parse", "--abbrev-ref", "HEAD").trim()
internal fun File.getGitHash(): String = runCli("git", "rev-parse", "HEAD").trim()
internal data class GitStatus(
    val branch: String,
    val workingTreeClean: Boolean,
    val ahead: Int,
    val behind: Int,
) {
    val fullyPushed get() = workingTreeClean && ahead == 0 && behind == 0
}

internal fun File.getGitStatus(): GitStatus = runCli("git", "status").let {
    GitStatus(
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

internal fun File.gitLatestTag(major: Int, minor: Int): Version? =
    runCli("git", "describe", "--tags", "--match", "$major.$minor.*").takeUnless { it.contains("fatal: ", true) }
        ?.trim()
        ?.substringBefore('-')
        ?.takeUnless { it.isBlank() }
        ?.let(Version::fromString)

internal fun File.gitTagHash(tag: String): String = runCli("git", "rev-list", "-n", "1", tag).trim()
internal fun File.gitBasedVersion(versionMajor: Int, versionMinor: Int): Version? {
    if (!getGitStatus().fullyPushed) {
        println("Not fully pushed, using snapshot version")
        return null
    }
    val hash = getGitHash()
    val latest = gitLatestTag(versionMajor, versionMinor) ?: Version(versionMajor, versionMinor, -1)
    println("Current hash: $hash")
    println("Latest tag: $latest")
    if (gitTagHash(latest.toString()) == hash) {
        // OK, we've already made the tag!
        println("Tag is up to date.")
        return latest
    } else {
        println("Creating new tag...")
        val newTag = latest.incrementPatch()
        runCli("git", "tag", newTag.toString())
        runCli("git", "push", "origin", "tag", newTag.toString())
        println("New tag created.")
        return newTag
    }
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

fun Project.lk(setup: LkGradleHelpers.() -> Unit) = LkGradleHelpers(this).also(setup)

@Deprecated("Remove minor, now set with gradle property 'versionMinor'")
fun Project.lk(minor: Int, setup: LkGradleHelpers.() -> Unit = {}) = LkGradleHelpers(this).also(setup)
class LkGradleHelpers(val project: Project) {
    val versionMajor = project.rootDir.getGitBranch().let {
        if (it.startsWith("version-")) it.removePrefix("version-").toIntOrNull() ?: 0
        else 0
    }
    val versionMinor: Int = (project.findProperty("versionMinor") as? String)?.toIntOrNull() ?: 0
    fun gitBasedVersion(): String {
        return project.rootDir.gitBasedVersion(versionMajor, versionMinor)?.toString()
            ?: project.rootDir.getGitBranch().plus("-SNAPSHOT")
    }

    val lockFile = project.projectDir.resolve("mavenOrLocal.lock")
    var lockFileContents: Map<String, String> = lockFile
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.associate { it.substringBefore(' ') to it.substringAfter(' ') }
        ?: mapOf()
        set(value) {
            field = value
            lockFile.writeText(value.entries.joinToString("\n") { it.key + " " + it.value })
        }
    val branchModeProjectsFolder: String? by project
    val upgradeLockToLatest: String? by project
    private fun requireProject(gitUrl: String): File {
        val projectName = gitUrl.removeSuffix(".git").substringAfterLast('/')
        val folder = File(branchModeProjectsFolder!!).resolve(projectName)
        if (!folder.exists()) folder.parentFile.runCli("git", "clone", gitUrl)
        return folder
    }

    fun mavenOrLocal(gitUrl: String, group: String, artifact: String, major: Int, minor: Int? = null): Any {
        return branchModeProjectsFolder?.let(::File)?.let {
            // get current remote version
            val folder = requireProject(gitUrl)
            if (major != 0) {
                if (folder.getGitBranch() != "version-$major") {
                    if (folder.getGitStatus().let { it.workingTreeClean && it.ahead == 0 }) {
                        folder.runCli("git", "checkout", "version-$major")
                    } else {
                        throw IllegalStateException("$folder: Need to get to a clean state before you can switch branches")
                    }
                }
            }
            println("Building subproject at ${folder}...")
            folder.runCli("./gradlew", "publishToMavenLocal")
            println("Built subproject at ${folder}.")
            val version = folder.runCli("./gradlew", "properties")
                .lines()
                .find { it.startsWith("version: ") }
                ?.substringAfter(": ")
                ?: run {
                    println("WARNING: Could not get version from $folder!")
                    lockFileContents["$group:$artifact"]
                        ?: throw IllegalStateException("No findable local version of $group:$artifact from $folder")
                }
            lockFileContents = lockFileContents + ("$group:$artifact" to version)
            "$group:$artifact:$version"
        } ?: run {
            val lockVersion =
                if (upgradeLockToLatest?.toBoolean() == true) latestFromRemote(group, artifact, major, minor).also {
                    lockFileContents += "$group:$artifact" to it
                }
                else lockFileContents["$group:$artifact"] ?: latestFromRemote(group, artifact, major, minor).also {
                    lockFileContents += "$group:$artifact" to it
                }
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
        project.signing {
            val signingKey: String? = project.findProperty("signingKey") as? String
            val signingPassword: String? = project.findProperty("signingPassword") as? String
            if(signingKey != null) {
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
        }
    }
}

fun LkGradleHelpers.kiteUi(major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/kiteui.git",
    group = "com.lightningkite.kiteui",
    artifact = "library",
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

fun LkGradleHelpers.lightningServer(artifact: String, major: Int, minor: Int? = null) = mavenOrLocal(
    gitUrl = "git@github.com:lightningkite/lightning-server.git",
    group = "com.lightningkite.lightningserver",
    artifact = artifact,
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
