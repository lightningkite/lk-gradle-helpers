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
        assertEquals("0.0.1-1-somehash-local", gitBasedVersionLogic(
            branch = "master",
            describedByTag = Version.fromString("0.0.0-1-somehash"),
            isClean = false
        ).toString())
        assertEquals("0.0.1-1-somehash", gitBasedVersionLogic(
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
        assertEquals("5.0.0-prerelease-8-asdfasdf-local", gitBasedVersionLogic(
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