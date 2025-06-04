package com.lightningkite.deployhelpers

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import java.net.URL


fun latestFromRemote(group: String, artifact: String, major: Int? = null, minor: Int? = null): String {
    val versions = URL(
        "https://lightningkite-maven.s3.us-west-2.amazonaws.com/${
            group.replace(
                '.',
                '/'
            )
        }/$artifact/maven-metadata.xml"
    )
        .let { XmlParser().parse(it.openStream()) }
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
            Version.fromString((it as Node).text())
        }
    return versions
        .also { println("Versions: $it") }
        .filter { (major == null || it.major == major) && (minor == null || it.minor == minor) }
        .also { println("Versions matching: $it") }
        .max()
        .toString()
}

fun latestFromRemote(id: String, major: Int? = null, minor: Int? = null): String {
    val versions = URL(
        "https://lightningkite-maven.s3.us-west-2.amazonaws.com/$id/$id.gradle.plugin/maven-metadata.xml"
    )
        .let { XmlParser().parse(it.openStream()) }
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
            Version.fromString((it as Node).text())
        }
    return versions
        .also { println("Versions: $it") }
        .filter { (major == null || it.major == major) && (minor == null || it.minor == minor) }
        .also { println("Versions matching: $it") }
        .max()
        .toString()
}