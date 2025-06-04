package com.lightningkite.deployhelpers

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlInline
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.buildTomlTable
import net.peanuuutz.tomlkt.element
import net.peanuuutz.tomlkt.table


@Serializable
data class VersionsToml(
    val versions: HashMap<String, TomlElement> = HashMap(),
    val libraries: HashMap<String, VersionsTomlLibrary> = HashMap(),
    val plugins: HashMap<String, VersionsTomlPlugin> = HashMap(),
)

@Serializable
data class VersionsTomlLibrary(
    val module: String = "",
    @TomlInline val version: TomlElement = TomlLiteral("0.0.0"),
)

@Serializable
data class VersionsTomlPlugin(
    val id: String = "",
    @TomlInline val version: TomlElement = TomlLiteral("0.0.0"),
)

fun versionsTomlVersionStrictly(version: String) = TomlTable(mapOf("strictly" to TomlLiteral(version)))
fun versionsTomlVersionRef(refName: String) = TomlTable(mapOf("ref" to TomlLiteral(refName)))
fun versionsTomlVersionDirect(version: String) = TomlLiteral(version)
fun versionsTomlVersionStrictlyIfSnapshot(version: String) =
    if (version.contains("snapshot", true)) versionsTomlVersionStrictly(version) else versionsTomlVersionDirect(version)

fun VersionsToml.prettyTable(): TomlTable = buildTomlTable {
    table("versions") {
        for (entry in versions.entries.sortedBy { it.key }) {
            element(entry.key, entry.value, TomlInline())
        }
    }
    table("libraries") {
        for (entry in libraries.entries.sortedBy { it.key }) {
            val e = Toml.encodeToTomlElement(VersionsTomlLibrary.serializer(), entry.value)
            element(entry.key, e, TomlInline())
        }
    }
    table("plugins") {
        for (entry in plugins.entries.sortedBy { it.key }) {
            val e = Toml.encodeToTomlElement(VersionsTomlPlugin.serializer(), entry.value)
            element(entry.key, e, TomlInline())
        }
    }
}