package com.lightningkite.deployhelpers

import org.junit.Test
import java.io.File
import java.net.URL
import kotlin.test.assertEquals

class HelpersKtTest {
    @Test
    fun test() {
        println(File(".").getGitBranch())
        println(File(".").getGitHash())
        println(File(".").gitLatestTag(0, 0))
    }

    @Test fun tag() {
        File(".").runCli("git", "describe", "--tags", "--match", "0.0.*")
            .let(::println)
    }
}