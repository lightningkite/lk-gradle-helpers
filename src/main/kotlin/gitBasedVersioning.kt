package com.lightningkite.deployhelpers

import org.gradle.api.Project
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.file.DirectoryProperty
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.OffsetDateTime
import java.util.WeakHashMap
import javax.inject.Inject

// One computed version per build, shared across subprojects. Keyed on the root Project instance,
// which Gradle recreates each build invocation, so entries never leak across builds (and the weak
// keys let them be collected). Replaces the old start-parameter-identity cache.
private val versionCache = WeakHashMap<Project, String>()

fun Project.useGitBasedVersion() {
    if (!rootProject.rootDir.resolve(".git").exists()) {
        return
    }
    version = gitBasedVersion()
}

/**
 * The git-derived version string for this build.
 *
 * The git invocations and CI-env reads happen inside [GitVersionValueSource] rather than directly
 * here, so that this stays compatible with Gradle's configuration cache: Gradle records the result
 * as a configuration input and only re-runs git to check whether the cached configuration is still
 * valid, instead of flagging the process execution as a problem and dropping the cache.
 */
fun Project.gitBasedVersion(): String = versionCache.getOrPut(rootProject) {
    rootProject.providers.of(GitVersionValueSource::class.java) {
        parameters.rootDir.set(rootProject.layout.projectDirectory)
    }.get()
}

/** Obtains the git version through Gradle's value-source mechanism (configuration-cache safe). */
internal abstract class GitVersionValueSource : ValueSource<String, GitVersionValueSource.Params> {
    interface Params : ValueSourceParameters {
        val rootDir: DirectoryProperty
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String {
        val dir = parameters.rootDir.get().asFile
        val cli = GitCli { args ->
            val out = ByteArrayOutputStream()
            exec.exec {
                workingDir = dir
                commandLine(listOf("git", *args))
                standardOutput = out
            }
            out.toString(Charsets.UTF_8)
        }
        val result = cli.computeVersion().toString()
        println("Project version: $result")
        return result
    }
}

/**
 * Runs `git` with the given args and returns its stdout. Implemented two ways: a plain
 * ProcessBuilder backend for tests and non-Gradle callers (see [File.gitCli]), and an
 * [ExecOperations] backend for the configuration-cache-safe [GitVersionValueSource]. The version
 * logic below is written against this interface so both backends behave identically.
 */
internal fun interface GitCli {
    fun git(vararg args: String): String
}

internal fun GitCli.gitBranch(): String = git("rev-parse", "--abbrev-ref", "HEAD").trim()

// `--porcelain` prints nothing when the working tree (including untracked files) is clean, which is
// equivalent to the old "working tree clean" status text but far less fragile to parse.
internal fun GitCli.gitWorkingTreeClean(): Boolean = git("status", "--porcelain").isBlank()

internal fun GitCli.gitClosestTagVersion(): Version {
    val tag = git("describe", "--tags", "--abbrev=0").trim()
    val base = Version.fromString(tag)
    val commitsAhead = git("rev-list", "$tag..HEAD", "--count").trim().toIntOrNull() ?: 0
    if (commitsAhead == 0) return base
    val shortHash = git("rev-parse", "--short", "HEAD").trim()
    return base.copy(commitsAheadOfPrevious = commitsAhead, buildHash = shortHash)
}

internal fun GitCli.computeVersion(): Version {
    val branch = gitBranch()
    val isClean = gitWorkingTreeClean() || isCi
    val describedByTag =
        try {
            gitClosestTagVersion()
        } catch (e: Exception) {
            e.printStackTrace()
            println("FAILED to get version.  Using 0.0.0")
            return Version(0, 0, 0)
        }
    println("describedByTag: $describedByTag")
    return gitBasedVersionLogic(branch, describedByTag, isClean)
}

/** ProcessBuilder-backed [GitCli] rooted at this directory (used by tests and non-Gradle callers). */
internal fun File.gitCli(): GitCli = GitCli { args -> runCli("git", *args) }

internal val isCi: Boolean
    get() =
        System.getenv("GITHUB_ACTIONS") == "true" ||
                System.getenv("TRAVIS") == "true" ||
                System.getenv("CIRCLECI") == "true" ||
                System.getenv("GITLAB_CI") == "true"

internal fun File.gitBasedVersionUncached(): Version = gitCli().computeVersion()

internal fun gitBasedVersionLogic(
    branch: String,
    describedByTag: Version,
    isClean: Boolean
): Version {
    val isStandardBranchName = when (branch.lowercase()) {
        "dev", "development", "main", "master", "head" -> true
        else -> branch.removePrefix("version").removePrefix("-").all { it.isDigit() || it == '.' }
    }
    val intendedVersionByBranchName = run {
        val cutOff = branch.removePrefix("version").removePrefix("v").removePrefix("-")
        if (cutOff.all { it.isDigit() || it == '.' || it == '-' }) {
            Version.fromString(cutOff.replace('-', '.'))
        } else null
    }
    val isPreReleaseFromBranch = intendedVersionByBranchName != null && intendedVersionByBranchName > describedByTag

    val label = when {
        isPreReleaseFromBranch -> "prerelease"
        !isStandardBranchName -> branch.filter { it.isLetterOrDigit() }
        else -> null
    }

    // A version branch declares the next release number; otherwise we sit on the previous tag and
    // let the commit count carry us forward. We never bump the patch ourselves: the trailing commit
    // count keeps a build above its base tag while staying below the next real release in Gradle's
    // version ordering (where an extra numeric part raises a version above the bare `x.y.z`).
    val base = if (isPreReleaseFromBranch) intendedVersionByBranchName!! else describedByTag

    return Version(
        major = base.major,
        minor = base.minor,
        patch = base.patch,
        commitsAheadOfPrevious = describedByTag.commitsAheadOfPrevious,
        branch = label.takeUnless { isClean && describedByTag.buildHash == null },
        buildHash = describedByTag.buildHash,
        local = !isClean,
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

internal fun File.getGitBranch(): String = gitCli().gitBranch()
internal fun File.getGitTag(): Version? = try {
    runCli("git", "describe", "--exact-match", "--tags").trim().let(Version::fromString)
} catch (e: Exception) {
    null
}

internal fun File.getGitClosestTag(): Version = gitCli().gitClosestTagVersion()

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

/**
 * A semantic version as Lightning Kite produces them from git history.
 *
 * String form: `major.minor.patch[-branch][-commitsAheadOfPrevious][-buildHash][-local]`, e.g.
 * `5.0.0-prerelease-8-a1b2c3d` for a pre-release build 8 commits past the previous tag, or
 * `1.2.3-3-a1b2c3d-1-local` for a dirty local build 3 commits past tag `1.2.3` (the trailing `-1`
 * is a marker that sorts a dirty build just above the clean build of the same commit; see below).
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** Number of commits since the previous tag, or null when built exactly on a tag. */
    val commitsAheadOfPrevious: Int? = null,
    /** Pre-release label: "prerelease" for a version branch, or a sanitized feature-branch name. */
    val branch: String? = null,
    /** Short git commit hash, present for builds that are ahead of the previous tag. */
    val buildHash: String? = null,
    /** True for builds made against a dirty working tree. */
    val local: Boolean = false,
) : Comparable<Version> {

    override fun compareTo(other: Version): Int = comparator.compare(this, other)

    companion object {
        private val comparator = compareBy(Version::major, Version::minor, Version::patch, Version::commitsAheadOfPrevious)

        /**
         * Parses the leading `major.minor.patch` and an optional trailing `-commits-hash` pair, as
         * produced by tags and `git describe`. Branch labels and the `local` marker are not parsed
         * back; they only ever exist on freshly computed versions, never on tags we read.
         */
        fun fromString(string: String): Version {
            val core = string.substringBefore('-').split('.')
            val rest = string.substringAfter('-', "")
            return Version(
                major = core.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = core.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = core.getOrNull(2)?.toIntOrNull() ?: 0,
                commitsAheadOfPrevious = rest.substringBefore('-', "").toIntOrNull(),
                buildHash = rest.substringAfter('-', "").takeUnless { it.isBlank() },
            )
        }
    }

    override fun toString(): String = buildString {
        append(major)
        append('.')
        append(minor)
        append('.')
        append(patch)
        branch?.let { append('-'); append(it) }
        commitsAheadOfPrevious?.let { append('-'); append(it) }
        buildHash?.let { append('-'); append(it) }
        if (local) {
            // A dirty build must sort *above* the clean build of the same commit yet *below* the
            // next commit, in Gradle's version ordering. Gradle ranks a version with an extra
            // numeric part above one without it, so the trailing "-1" raises the dirty build just
            // above the clean one; the next commit still wins on its higher commit count. When built
            // on an exact tag there is no commit count yet, so we add a "0" to occupy that slot and
            // remain below commit #1 (whose count is 1).
            if (commitsAheadOfPrevious == null) append("-0")
            append("-1-local")
        }
    }
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
