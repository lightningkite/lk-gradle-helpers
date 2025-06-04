package com.lightningkite.deployhelpers

import org.gradle.api.Project
import java.util.Properties
import java.util.WeakHashMap

private val cache = WeakHashMap<Project, Properties?>()
val Project.localProperties get() = cache.getOrPut(this) {
    rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()?.use { stream ->
        Properties().apply { load(stream) }
    }
}