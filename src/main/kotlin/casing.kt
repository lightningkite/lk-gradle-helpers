@file:Suppress("Deprecation")
package com.lightningkite.deployhelpers


val `casing separator regex` = Regex("([-_.\\s]+([A-Z]*[a-z0-9]+))|([.-_\\s]*[A-Z]+)")
inline fun String.caseAlter(crossinline update: (after: String) -> String): String =
    `casing separator regex`.replace(this) {
        if (it.range.start == 0) it.value
        else update(it.value.filter { !(it == '-' || it == '_' || it.isWhitespace() || it == '.') })
    }


fun String.titleCase() = caseAlter { " " + it.capitalize() }.capitalize()
fun String.spaceCase() = caseAlter { " " + it }.decapitalize()
fun String.kabobCase() = caseAlter { "-$it" }.lowercase()
fun String.snakeCase() = caseAlter { "_$it" }.lowercase()
fun String.screamingSnakeCase() = caseAlter { "_$it" }.uppercase()
fun String.camelCase() = caseAlter { it.capitalize() }.decapitalize()
fun String.pascalCase() = caseAlter { it.capitalize() }.capitalize()