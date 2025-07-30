package com.lightningkite.deployhelpers

import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicenseSpec


fun MavenPom.github(group: String = "lightningkite", repo: String) {
    url.set("https://github.com/$group/$repo")
    scm {
        connection.set("scm:git:https://github.com/$group/$repo.git")
        developerConnection.set("scm:git:https://github.com/$group/$repo.git")
        url.set("https://github.com/$group/$repo")
    }
}

fun MavenPomLicenseSpec.mit() {
    license {
        name.set("The MIT License (MIT)")
        url.set("https://www.mit.edu/~amini/LICENSE.md")
        distribution.set("repo")
    }
}

fun MavenPomLicenseSpec.apache() {
    license {
        name.set("Apache-2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("repo")
        comments.set("A business-friendly OSS license")
    }
}

fun MavenPomDeveloperSpec.joseph() {
    developer {
        id.set("LightningKiteJoseph")
        name.set("Joseph Ivie")
        email.set("joseph@lightningkite.com")
    }
}

fun MavenPomDeveloperSpec.brady() {
    developer {
        id.set("bjsvedin")
        name.set("Brady Svedin")
        email.set("brady@lightningkite.com")
    }
}
fun MavenPomDeveloperSpec.hunter() {
    developer {
        id.set("iHoonter")
        name.set("Hunter")
        email.set("Hunter@lightningkite.com")
    }
}
//fun MavenPomDeveloperSpec.albin() {
//    developer {
//        id.set("bjsvedin")
//        name.set("Albin")
//        email.set("brady@lightningkite.com")
//    }
//}