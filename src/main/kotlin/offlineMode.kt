package com.lightningkite.deployhelpers

import org.gradle.api.Project

val Project.offlineMode: Boolean get() = (
        (findProperty("offlineModeOverride") as? String)
            ?: (findProperty("offlineMode") as? String)
        )
    ?.takeUnless { it.isBlank() }
    ?.let { it.lowercase() == "true" }
    ?: false