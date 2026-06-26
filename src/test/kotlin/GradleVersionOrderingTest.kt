package com.lightningkite.deployhelpers

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the version strings we generate are ordered the way we intend by Gradle's own
 * version comparator — the one Gradle uses for conflict resolution and dynamic versions — rather
 * than only by our [Version.compareTo]. The two use different rules, so this guards the behaviour
 * that actually matters when these versions are resolved as dependencies.
 */
class GradleVersionOrderingTest {
    private val parser = VersionParser()
    private val comparator = DefaultVersionComparator().asVersionComparator()

    /** True when Gradle considers [a] strictly lower (older) than [b]. */
    private fun lower(a: String, b: String): Boolean =
        comparator.compare(parser.transform(a), parser.transform(b)) < 0

    private fun assertAscending(vararg versions: String) {
        for (i in 0 until versions.size - 1) {
            assertTrue(
                lower(versions[i], versions[i + 1]),
                "Expected Gradle to order ${versions[i]} < ${versions[i + 1]}",
            )
        }
    }

    /** Runs the real version logic so we test the strings we actually publish. */
    private fun version(branch: String, tag: String, clean: Boolean): String =
        gitBasedVersionLogic(branch, Version.fromString(tag), clean).toString()

    @Test
    fun everyCommitMovesForward() {
        assertAscending(
            version("main", "5.1.1", clean = true),        // built on the tag
            version("main", "5.1.1-1-h1", clean = true),   // one commit later
            version("main", "5.1.1-2-h2", clean = true),   // two commits later
            version("main", "5.1.2", clean = true),        // then we tag 5.1.2
            version("main", "5.1.2-1-h3", clean = true),   // one commit past the new tag
        )
    }

    @Test
    fun localBuildSortsAboveItsOwnCommitButBelowTheNext() {
        val clean = version("main", "5.1.1-2-h2", clean = true)        // clean build of commit C
        val local = version("main", "5.1.1-2-h2", clean = false)       // dirty build of commit C
        val nextCommit = version("main", "5.1.1-3-h3", clean = true)   // clean build of commit C+1
        // Dirty edits outrank the clean build of the same commit, but committing them (the next
        // commit) still moves strictly forward — no hash-dependent tie.
        assertAscending(clean, local, nextCommit)
    }

    @Test
    fun dirtyBuildExactlyOnATagIsStillStrictlyOrdered() {
        val release = version("main", "5.1.1", clean = true)           // 5.1.1
        val dirtyOnTag = version("main", "5.1.1", clean = false)       // 5.1.1-0-1-local
        val firstCommit = version("main", "5.1.1-1-h1", clean = true)  // first commit after the tag
        assertAscending(release, dirtyOnTag, firstCommit)
    }

    @Test
    fun localBuildStaysBelowTheNextRelease() {
        val local = version("main", "5.1.1-2-h2", clean = false)
        assertTrue(lower(local, "5.1.2"), "Expected $local to sort below the 5.1.2 release")
    }

    @Test
    fun branchPrereleaseSortsBetweenTheOldAndNewRelease() {
        val prerelease = version("version-5", "4.4.2-8-asdf", clean = true) // 5.0.0-prerelease-8-asdf
        assertAscending("4.4.2", prerelease, "5.0.0")
    }
}
