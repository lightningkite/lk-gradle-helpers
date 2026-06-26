package com.lightningkite.deployhelpers

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GitBasedVersioningTest {
    @Test
    fun testBasic() {
        println(File(".").gitBasedVersionUncached())
    }
    @Test fun testVersionToString() {
        println(Version.fromString("1.2.3"))
    }
    @Test
    fun test() {
        // We don't bump the patch ourselves; the commit count keeps the build above tag 0.0.0 while
        // staying below the eventual 0.0.1 release in Gradle's version ordering. A dirty build adds a
        // "-1" marker so it outranks the clean build of the same commit (see GradleVersionOrderingTest).
        assertEquals("0.0.0-1-somehash-1-local", gitBasedVersionLogic(
            branch = "master",
            describedByTag = Version.fromString("0.0.0-1-somehash"),
            isClean = false
        ).toString())
        assertEquals("0.0.0-1-somehash", gitBasedVersionLogic(
            branch = "master",
            describedByTag = Version.fromString("0.0.0-1-somehash"),
            isClean = true
        ).toString())
        assertEquals("0.0.1", gitBasedVersionLogic(
            branch = "master",
            describedByTag = Version.fromString("0.0.1"),
            isClean = true
        ).toString())
        assertEquals("5.0.0-prerelease-8-asdfasdf", gitBasedVersionLogic(
            branch = "version-5",
            describedByTag = Version.fromString("4.4.2-8-asdfasdf"),
            isClean = true
        ).toString())
        assertEquals("5.0.0-prerelease-8-asdfasdf-1-local", gitBasedVersionLogic(
            branch = "version-5",
            describedByTag = Version.fromString("4.4.2-8-asdfasdf"),
            isClean = false
        ).toString())
        assertEquals("5.1.1", gitBasedVersionLogic(
            branch = "version-5",
            describedByTag = Version.fromString("5.1.1"),
            isClean = true
        ).toString())
        assertEquals("5.2.0-prerelease", gitBasedVersionLogic(
            branch = "version-5.2",
            describedByTag = Version.fromString("5.1.1"),
            isClean = true
        ).toString())
    }
}