package com.lightningkite.deployhelpers

import net.peanuuutz.tomlkt.Toml
import java.io.File
import kotlin.test.Test

class LocalDependenciesTest {
    @Test fun test() {
        upgradeVersions(Toml.decodeFromString(
            VersionsToml.serializer(),
            sampleToml
        ), true, File(".."))
    }

    private val sampleToml = """
        
        [versions]
        agp = "0.0.0"
        androidDesugaring = "0.0.0"
        dokka = "0.0.0"
        graalVmNative = "0.0.0"
        kiteui = "0.0.0"
        kotlin = "0.0.0"
        kotlinerCli = "0.0.0"
        kotlinxCoroutinesTest = "0.0.0"
        kotlinxSerializationCsvDurable = "0.0.0"
        ksp = "0.0.0"
        lightningServer = "0.0.0"
        lkGradleHelpers = "0.0.0"
        log4jToSlf4j = "0.0.0"
        proguard = "0.0.0"
        serviceAbstractions = "0.0.0"
        shadow = "0.0.0"
        vanniktechPublishing = "0.0.0"
        vite = "DEV"

        [libraries]
        androidDesugaring = { module = "com.android.tools:desugar_jdk_libs", version = { ref = "androidDesugaring" } }
        comLightningKiteServices-aws-client = { module = "com.lightningkite.services:aws-client", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-basis = { module = "com.lightningkite.services:basis", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-cache = { module = "com.lightningkite.services:cache", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-cache-dynamodb = { module = "com.lightningkite.services:cache-dynamodb", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-cache-memcached = { module = "com.lightningkite.services:cache-memcached", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-cache-redis = { module = "com.lightningkite.services:cache-redis", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-cache-test = { module = "com.lightningkite.services:cache-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-data = { module = "com.lightningkite.services:data", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database = { module = "com.lightningkite.services:database", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-jsonfile = { module = "com.lightningkite.services:database-jsonfile", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-mongodb = { module = "com.lightningkite.services:database-mongodb", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-postgres = { module = "com.lightningkite.services:database-postgres", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-processor = { module = "com.lightningkite.services:database-processor", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-shared = { module = "com.lightningkite.services:database-shared", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-database-test = { module = "com.lightningkite.services:database-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-email = { module = "com.lightningkite.services:email", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-email-javasmtp = { module = "com.lightningkite.services:email-javasmtp", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-email-mailgun = { module = "com.lightningkite.services:email-mailgun", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-email-test = { module = "com.lightningkite.services:email-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-exceptions-sentry = { module = "com.lightningkite.services:exceptions-sentry", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files = { module = "com.lightningkite.services:files", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-azbs = { module = "com.lightningkite.services:files-azbs", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-clamav = { module = "com.lightningkite.services:files-clamav", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-client = { module = "com.lightningkite.services:files-client", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-javalocal = { module = "com.lightningkite.services:files-javalocal", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-s3 = { module = "com.lightningkite.services:files-s3", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-files-test = { module = "com.lightningkite.services:files-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-http-client = { module = "com.lightningkite.services:http-client", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-metrics-cloudwatch = { module = "com.lightningkite.services:metrics-cloudwatch", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-notifications = { module = "com.lightningkite.services:notifications", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-notifications-fcm = { module = "com.lightningkite.services:notifications-fcm", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-notifications-test = { module = "com.lightningkite.services:notifications-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-pubsub = { module = "com.lightningkite.services:pubsub", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-pubsub-redis = { module = "com.lightningkite.services:pubsub-redis", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-pubsub-test = { module = "com.lightningkite.services:pubsub-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-should-be-standard-library = { module = "com.lightningkite.services:should-be-standard-library", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-sms = { module = "com.lightningkite.services:sms", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-sms-test = { module = "com.lightningkite.services:sms-test", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-sms-twilio = { module = "com.lightningkite.services:sms-twilio", version = { ref = "serviceAbstractions" } }
        comLightningKiteServices-test = { module = "com.lightningkite.services:test", version = { ref = "serviceAbstractions" } }
        comLightningkiteKiteuiLibrary = { module = "com.lightningkite.kiteui:library", version = { ref = "kiteui" } }
        comLightningkiteKotlinxSerializationCsvDurable = { module = "com.lightningkite:kotlinx-serialization-csv-durable", version = { ref = "kotlinxSerializationCsvDurable" } }
        comLightningkiteLightningserver-auth = { module = "com.lightningkite.lightningserver:auth", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-aws = { module = "com.lightningkite.lightningserver:aws", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-aws-serverless = { module = "com.lightningkite.lightningserver:aws-serverless", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-core = { module = "com.lightningkite.lightningserver:core", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-core-shared = { module = "com.lightningkite.lightningserver:core-shared", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-demo = { module = "com.lightningkite.lightningserver:demo", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-engine-local = { module = "com.lightningkite.lightningserver:engine-local", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-files = { module = "com.lightningkite.lightningserver:files", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-files-shared = { module = "com.lightningkite.lightningserver:files-shared", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-ktor = { module = "com.lightningkite.lightningserver:ktor", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-meta = { module = "com.lightningkite.lightningserver:meta", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions = { module = "com.lightningkite.lightningserver:sessions", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions-email = { module = "com.lightningkite.lightningserver:sessions-email", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions-oauth = { module = "com.lightningkite.lightningserver:sessions-oauth", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions-oauth-shared = { module = "com.lightningkite.lightningserver:sessions-oauth-shared", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions-shared = { module = "com.lightningkite.lightningserver:sessions-shared", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-sessions-sms = { module = "com.lightningkite.lightningserver:sessions-sms", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-typed = { module = "com.lightningkite.lightningserver:typed", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-typed-shared = { module = "com.lightningkite.lightningserver:typed-shared", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-upload-files = { module = "com.lightningkite.lightningserver:upload-files", version = { ref = "lightningServer" } }
        comLightningkiteLightningserver-vertx = { module = "com.lightningkite.lightningserver:vertx", version = { ref = "lightningServer" } }
        kotlin-test-junit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version = { ref = "kotlin" } }
        kotlinerCli = { module = "com.lightningkite:kotliner-cli", version = { ref = "kotlinerCli" } }
        kotlinxCoroutinesTest = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version = { ref = "kotlinxCoroutinesTest" } }
        lkGradleHelpers = { module = "com.lightningkite:lk-gradle-helpers", version = { ref = "lkGradleHelpers" } }
        log4j-to-slf4j = { module = "org.apache.logging.log4j:log4j-to-slf4j", version = { ref = "log4jToSlf4j" } }
        proguard = { module = "com.guardsquare:proguard-gradle", version = { ref = "proguard" } }

        [plugins]
        androidApp = { id = "com.android.application", version = { ref = "agp" } }
        androidLibrary = { id = "com.android.library", version = { ref = "agp" } }
        comLightningkiteKiteui = { id = "com.lightningkite.kiteui", version = { ref = "kiteui" } }
        dokka = { id = "org.jetbrains.dokka", version = { ref = "dokka" } }
        graalVmNative = { id = "org.graalvm.buildtools.native", version = { ref = "graalVmNative" } }
        kotlinCocoapods = { id = "org.jetbrains.kotlin.native.cocoapods", version = { ref = "kotlin" } }
        kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version = { ref = "kotlin" } }
        kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version = { ref = "kotlin" } }
        ksp = { id = "com.google.devtools.ksp", version = { ref = "ksp" } }
        serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version = { ref = "kotlin" } }
        shadow = { id = "com.github.johnrengelman.shadow", version = { ref = "shadow" } }
        vanniktechPublishing = { id = "com.vanniktech.maven.publish", version = { ref = "vanniktechPublishing" } }
        vite = { id = "dev.opensavvy.vite.kotlin", version = { ref = "vite" } }
    """.trimIndent()
}
