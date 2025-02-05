package com.lightningkite.deployhelpers

import org.gradle.api.Action
import org.gradle.api.publish.PublishingExtension
import org.gradle.plugins.signing.SigningExtension

/**
 * Retrieves the [sourceSets][org.gradle.api.tasks.SourceSetContainer] extension.
 */
internal val org.gradle.api.Project.`sourceSets`: org.gradle.api.tasks.SourceSetContainer
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

/**
 * Configures the [publishing][org.gradle.api.publish.PublishingExtension] extension.
 */
internal fun org.gradle.api.Project.`publishing`(configure: PublishingExtension.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.findByName("publishing")?.let { configure(it as PublishingExtension) }
}

/**
 * Configures the [signing][org.gradle.plugins.signing.SigningExtension] extension.
 */
internal fun org.gradle.api.Project.`signing`(configure: SigningExtension.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.findByName("signing")?.let { configure(it as SigningExtension) }
}

/**
 * Retrieves the [publishing][org.gradle.api.publish.PublishingExtension] extension.
 */
internal val org.gradle.api.Project.`publishing`: org.gradle.api.publish.PublishingExtension?
    get() =
        (this as org.gradle.api.plugins.ExtensionAware).extensions.findByName("publishing") as? org.gradle.api.publish.PublishingExtension