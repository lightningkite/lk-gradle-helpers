package com.lightningkite.deployhelpers

import org.gradle.api.Project
import java.io.File
import java.time.OffsetDateTime
import java.util.WeakHashMap

val versionCache = WeakHashMap<Project, Pair<String, String>>()
fun Project.useGitBasedVersion() { version = gitBasedVersion() }
fun Project.gitBasedVersion(): String {
    val (runId, existingVersion) = versionCache[project.rootProject] ?: ("0" to "0.0.0")
    val myRunId = System.identityHashCode(project.gradle.startParameter).toString()
    if (myRunId == runId) return existingVersion

    val branch = project.rootDir.getGitBranch()
    val versionMajor = branch.let {
        if (it.startsWith("version-")) it.removePrefix("version-").substringBefore('-').toIntOrNull() ?: 0
        else 0
    }
    val versionBranch = branch.removePrefix("version-").substringAfter('-', "").takeUnless { it.isBlank() }
    val versionMinor: Int = (project.findProperty("versionMinor") as? String)?.toIntOrNull() ?: 0

    val result = project.rootDir.gitBasedVersion(
        versionMajor,
        versionMinor,
        canCreateTag = versionBranch == null,
        offlineMode = offlineMode
    )?.toString()
        ?: project.rootDir.getGitBranch().plus("-SNAPSHOT")
    println("Determined version of ${project.rootProject.name} to be $result")
    versionCache[project.rootProject] = myRunId to result
    return result
}

internal fun File.runCli(vararg args: String): String {
    assert(this.exists())
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
internal fun File.runCliWithOutput(vararg args: String) {
    assert(this.exists())
    val process = ProcessBuilder(*args)
        .directory(this)
        .start()
    process.outputStream.close()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw Exception("Unexpected exit code ${exitCode} from ${args.joinToString(" ")}")
    }
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
        .firstOrNull()
        ?.takeUnless { it.isBlank() }
        ?.trim()
        ?.let(Version::fromString)
}

internal fun File.gitTagHash(tag: String): String = runCli("git", "rev-list", "-n", "1", tag).trim()
internal fun File.gitBasedVersion(versionMajor: Int, versionMinor: Int, canCreateTag: Boolean = true, offlineMode: Boolean): Version? {
    val status = getGitStatus()
    if (!status.fullyPushed) {
        println("Not fully pushed, using snapshot version.  Raw status of Git: ${status.raw}")
        return null
    }
    var current = getGitTag()
    if (current == null && !offlineMode) {
        runCli("git", "fetch", "--tags", "--force") // ensure we're up to date
        current = getGitTag()
    }
    return if (current != null) {
        // OK, we've already made the tag!
        println("Tag $current is up to date.")
        current
    } else if (canCreateTag && !offlineMode) {
        val latest = gitLatestTag(versionMajor, versionMinor)
        val newTag = (latest ?: Version(versionMajor, versionMinor, -1)).incrementPatch()
        runCli("git", "tag", newTag.toString())
        runCli("git", "push", "origin", "tag", newTag.toString())
        println("New tag $newTag created.")
        newTag
    } else null
}