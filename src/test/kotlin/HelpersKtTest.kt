package com.lightningkite.deployhelpers

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlInline
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.buildTomlTable
import net.peanuuutz.tomlkt.element
import net.peanuuutz.tomlkt.encodeToTomlElement
import net.peanuuutz.tomlkt.literal
import net.peanuuutz.tomlkt.table
import org.junit.Test
import java.io.File
import java.io.StringReader
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
    @Test fun tag2() {
        File("/Users/jivie/Projects/kotlinx-serialization-csv-durable").gitLatestTag(0, 2).let(::println)
    }

    @Test fun parseVersions() {
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
    @Test fun caseFix() {
        println("org.jetbrains.kotlin.plugin.serialization".camelCase())
    }
    @Test fun toml() {

        Toml.decodeFromString(VersionsToml.serializer(), """
            [versions]
            kotlinXSerialization="1.7.3"
            kotlin="2.0.21"

            [libraries]
            kotlinXJson={module="org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref="kotlinXSerialization"}

            [plugins]
            serialization={id="org.jetbrains.kotlin.plugin.serialization", version.ref="kotlin"}

        """.trimIndent()).also { println(it) }.also {
            it.prettyTable().let { Toml.encodeToString(it) }.let { println(it) }
        }
    }
}