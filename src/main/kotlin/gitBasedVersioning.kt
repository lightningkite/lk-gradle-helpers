package com.lightningkite.deployhelpers

import org.gradle.api.Project
import java.io.File
import java.time.OffsetDateTime
import java.util.WeakHashMap

val versionCache = WeakHashMap<Project, Pair<String, String>>()
fun Project.useGitBasedVersion() {
    if(!project.rootDir.resolve(".git").exists()) { return }
    version = gitBasedVersion()
}

fun Project.gitBasedVersion(): String {
    val (runId, existingVersion) = versionCache[project.rootProject] ?: ("0" to "0.0.0")
    val myRunId = System.identityHashCode(project.gradle.startParameter).toString()
    if (myRunId == runId) return existingVersion

    val result = project.rootDir.gitBasedVersionUncached().toString()
    versionCache[project.rootProject] = myRunId to result
    return result
}
internal val isCi: Boolean get() =
    System.getenv("GITHUB_ACTIONS") == "true" ||
    System.getenv("TRAVIS") == "true" ||
    System.getenv("CIRCLECI") == "true" ||
    System.getenv("GITLAB_CI") == "true"
internal fun File.gitBasedVersionUncached(): Version {
    val branch = this.getGitBranch()
    val isClean = this.getGitStatus().workingTreeClean || isCi
    val describedByTag = this.getGitClosestTag()!!
    return gitBasedVersionLogic(branch, describedByTag, isClean)
}

internal fun gitBasedVersionLogic(
    branch: String,
    describedByTag: Version,
    isClean: Boolean
): Version {
    val isStandardBranchName = when(branch) {
        "dev", "development", "main", "master" -> true
        else -> branch.removePrefix("version").removePrefix("-").all { it.isDigit() || it == '.' }
    }
    val intendedVersionByBranchName = run {
        val cutOff = branch.removePrefix("version").removePrefix("v").removePrefix("-")
        if (cutOff.all { it.isDigit() || it == '.' || it == '-' }) {
            Version.fromString(cutOff.replace('-', '.'))
        } else null
    }
    val major = intendedVersionByBranchName?.major ?: describedByTag.major
    val minor = intendedVersionByBranchName?.minor?.takeIf { describedByTag.major != intendedVersionByBranchName.major } ?: describedByTag.minor
    val patch = intendedVersionByBranchName?.patch?.takeIf { describedByTag.major != intendedVersionByBranchName.major || describedByTag.minor != intendedVersionByBranchName.minor } ?: describedByTag.patch
    val isPreReleaseFromBranch = major > describedByTag.major || (major == describedByTag.major && minor > describedByTag.minor) || (major == describedByTag.major && minor == describedByTag.minor && patch > describedByTag.patch)

    return Version(
        major = major,
        minor = minor,
        patch = if(isPreReleaseFromBranch || (isClean && describedByTag.prerelease == null)) patch else patch + 1,
        prerelease = StringBuilder().apply {
            if (!isStandardBranchName) {
                append(branch.filter { it.isLetterOrDigit() })
                append('-')
            } else if(isPreReleaseFromBranch) {
                append("prerelease-")
            }
            describedByTag.prerelease?.let { append(it) }
            if (!isClean) {
                append("-local")
            }
        }.toString().takeUnless { it.isBlank() },
        buildMetadata = describedByTag.buildMetadata
    )
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
internal fun File.getGitClosestTag(): Version? = try {
    runCli("git", "describe", "--tags").trim().substringBeforeLast('-').let(Version::fromString)
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

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
    val buildMetadata: String? = null
) : Comparable<Version> {
    constructor(
        major: Int,
        minor: Int,
        patch: Int,
        prereleaseName: String? = null,
        prereleaseBuildNumber: Int? = null,
        buildMetadata: String? = null,
    ):this(
        major = major,
        minor = minor,
        patch = patch,
        prerelease = listOfNotNull(
            prereleaseName,
            prereleaseBuildNumber?.toString()?.padStart(4, '0')
        ).joinToString("-"),
        buildMetadata = buildMetadata?.takeUnless { it.isBlank() }
    )
    override fun compareTo(other: Version): Int = comparator.compare(this, other)

    companion object {
        private val comparator = compareBy(Version::major, Version::minor, Version::patch)
        fun fromString(string: String): Version {
            val parts = string.substringBefore('-').substringBefore('+').split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val postDash = string.substringAfter('-', "").substringBefore('+').takeUnless { it.isBlank() }
            val buildMetadata = string.substringAfter('+', "").substringBefore('-').takeUnless { it.isBlank() }
            return Version(major, minor, patch, postDash, buildMetadata)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (prerelease?.let { "-$it" } ?: "") + (buildMetadata?.let { "+$it" } ?: "")

    fun incrementPatch() = copy(patch = patch + 1)

    val prereleaseBuildNumber: Int get() = prerelease?.filter { it.isDigit() }?.toIntOrNull() ?: -1
    val prereleaseName: String? get() = prerelease?.filter { it.isLetter() }?.takeUnless { it.isBlank() }
    fun incrementPrereleaseBuildNumber() = copy(buildMetadata = buildMetadata)
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
