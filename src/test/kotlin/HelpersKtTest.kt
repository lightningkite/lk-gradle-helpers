package com.lightningkite.deployhelpers

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import org.junit.Test
import java.io.File
import java.io.StringReader
import kotlin.test.assertEquals

class HelpersKtTest {
    @Test
    fun test() {
        println(File(".").getGitBranch())
        println(File(".").getGitHash())
        println(File(".").gitLatestTag(0, 0))
    }

    @Test
    fun tag() {
        File(".").runCli("git", "describe", "--tags", "--match", "0.0.*")
            .let(::println)
    }

    @Test
    fun tag2() {
        val repo = kotlin.io.path.createTempDirectory("gitLatestTag").toFile()
        try {
            repo.runCli("git", "init")
            repo.runCli(
                "git",
                "-c",
                "user.name=test",
                "-c",
                "user.email=test@test",
                "commit",
                "--allow-empty",
                "-m",
                "init"
            )
            listOf("0.1.5", "0.2.2", "0.2.10", "0.2.9", "0.3.0").forEach { repo.runCli("git", "tag", it) }
            assertEquals(Version(0, 2, 10), repo.gitLatestTag(0, 2))
            assertEquals(null, repo.gitLatestTag(0, 4))
        } finally {
            repo.deleteRecursively()
        }
    }

    @Test
    fun parseVersions() {
        """
            <metadata>
            <groupId>com.lightningkite</groupId>
            <artifactId>kotlinx-serialization-csv-durable</artifactId>
            <versioning>
            <latest>0.2.1</latest>
            <release>0.2.1</release>
            <versions>
            <version>1.0-SNAPSHOT</version>
            <version>0.1.0</version>
            <version>0.2.1</version>
            </versions>
            <lastUpdated>20250203222004</lastUpdated>
            </versioning>
            </metadata>
        """.trimIndent()
            .let { XmlParser().parse(StringReader(it)) }
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
                Version.fromString((it as Node).text().also { println(it) })
            }
    }

    @Test
    fun caseFix() {
        println("org.jetbrains.kotlin.plugin.serialization".camelCase())
    }
}