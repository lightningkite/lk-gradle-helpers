package com.lightningkite.deployhelpers

import org.junit.Assert.*
import java.io.File
import kotlin.test.Test

class S3Test {
    @Test
    fun uploadTest() {
        File("local/testupload").apply {
            mkdirs()
            resolve("folder").apply {
                mkdirs()
                resolve("test.txt").writeText("Test data")
            }
            resolve("outer.txt").writeText("Test data")
        }.uploadDirectoryToS3("lightningkite-maven", "lk-gradle-helpers-upload-test").also { println(it) }
    }
}