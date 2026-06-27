package com.lightningkite.deployhelpers

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end proof that git-based versioning is compatible with Gradle's configuration cache:
 * we apply [useGitBasedVersion] in a real throwaway build, run it with the cache enabled and
 * `--configuration-cache-problems=fail` (so any problem fails the build), and confirm the cache is
 * stored on the first run and reused on the second. The unit tests cover the pure version logic;
 * only a real build exercises the [GitVersionValueSource] / config-cache integration.
 */
class GitVersionConfigCacheTest {
    @Test
    fun configCacheIsStoredThenReused() {
        val dir = File("build/tmp/ccVerify").absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()

        // A minimal git repo whose HEAD sits exactly on tag 1.2.0 with a clean tree.
        dir.runCli("git", "init")
        dir.runCli("git", "config", "user.email", "test@example.com")
        dir.runCli("git", "config", "user.name", "Test")
        dir.resolve(".gitignore").writeText(".gradle/\nbuild/\n")
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"ccverify\"\n")
        // The plugin exposes extension functions (no plugin id), so put its classpath on the test
        // build's buildscript classpath directly, using the curated list `java-gradle-plugin`
        // generates for TestKit.
        // A Groovy build script (not .kts) avoids Gradle's kotlin-dsl compile-avoidance limitation
        // with the plugin's public inline functions; it calls the Kotlin extension as a static method.
        val classpathLiterals = pluginUnderTestClasspath().joinToString(",\n        ") {
            "'" + it.replace("\\", "\\\\").replace("'", "\\'") + "'"
        }
        dir.resolve("build.gradle").writeText(
            """
            buildscript {
                dependencies {
                    classpath files(
                        $classpathLiterals
                    )
                }
            }
            com.lightningkite.deployhelpers.GitBasedVersioningKt.useGitBasedVersion(project)
            """.trimIndent()
        )
        dir.runCli("git", "add", ".")
        dir.runCli("git", "commit", "-m", "init")
        dir.runCli("git", "tag", "1.2.0")

        fun run() = GradleRunner.create()
            .withProjectDir(dir)
            .withArguments(
                "help",
                "--configuration-cache",
                "--configuration-cache-problems=fail",
                "--stacktrace",
            )
            .build()

        val first = run().output
        assertTrue("Project version: 1.2.0" in first, "version not computed; got:\n$first")
        assertTrue("Configuration cache entry stored" in first, "cache not stored; got:\n$first")

        val second = run().output
        assertTrue(
            "Configuration cache entry reused" in second || "Reusing configuration cache" in second,
            "cache not reused on second run; got:\n$second",
        )
    }

    /** The plugin-under-test classpath that `java-gradle-plugin` records for TestKit. */
    private fun pluginUnderTestClasspath(): List<String> {
        val resource = javaClass.classLoader.getResource("plugin-under-test-metadata.properties")
            ?: error("plugin-under-test-metadata.properties not found; is `java-gradle-plugin` applied?")
        val props = resource.openStream().use { java.util.Properties().apply { load(it) } }
        return props.getProperty("implementation-classpath").split(File.pathSeparator)
    }
}
