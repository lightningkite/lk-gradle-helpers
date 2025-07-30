package com.lightningkite.deployhelpers

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

fun Project.lkLibrary(githubOrg: String, githubRepo: String, pom: MavenPom.()->Unit) {
    useGitBasedVersion()
    useLocalDependencies()
    setupDokka(githubOrg, githubRepo)
    lkPublishing(githubOrg, githubRepo, pom)
}