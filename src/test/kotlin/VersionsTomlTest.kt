package com.lightningkite.deployhelpers

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.junit.Test

class VersionsTomlTest {
    @Test fun test() {
        val content = """
            [versions]
            agp = "8.10.0"
            androidDesugaring = "2.1.5"
            angusMail = "2.0.3"
            apacheTika = "3.2.1"
            awsVersion = "2.32.7"
            azureFunctions = "3.1.0"
            azureStorage = "12.31.0"
            bouncyCastle = "1.81"
            bson = "5.5.1"
            clamAv = "2.1.2"
            coroutines = "1.10.2"
            dokka = "2.0.0"
            dynamodb = "2.32.7"
            embedMongo = "4.20.1"
            embeddedPostgres = "2.1.0"
            embeddedRedis = "0.9.1"
            exposed = "0.61.0"
            firebaseAdmin = "9.5.0"
            graalVmNative = "0.11.0"
            guava = "33.4.8-jre"
            hierynomusSshj = "0.40.0"
            javaJwt = "4.5.0"
            kaml = "0.85.0"
            kbson = "0.5.0"
            kotlin = "2.2.0"
            kotlinHtmlJvm = "0.12.0"
            kotlinLogging = "7.0.7"
            kotlinXDatetime = "0.7.1"
            kotlinXIO = "0.8.0"
            kotlinXSerialization = "1.9.0"
            kotlinerCli = "1.0.5"
            kotlinxSerializationCsvDurable = "0.2.11"
            ksp = "2.2.0-2.0.2"
            ktor = "3.2.2"
            lambdaJavaCore = "1.3.0"
            lambdaJavaEvents = "3.16.1"
            lambdaJavaLog4j2 = "1.6.0"
            lettuce = "6.7.1.RELEASE"
            lkGradleHelpers = "4.0.3"
            logBackClassic = "1.5.18"
            mcp = "0.6.0"
            memcached = "2.4.8"
            metadataExtractor = "2.19.0"
            mongoDriver = "5.5.1"
            okio = "3.15.0"
            oneTimePass = "2.4.1"
            orgCrac = "0.1.3"
            postgresql = "42.7.7"
            proguard = "7.7.0"
            scrimage = "4.3.3"
            sentry = "8.17.0"
            serializationLibs = "1.9.0"
            shadow = "8.1.1"
            vanniktechMavenPublish = "0.34.0"
            webauthn4jCore = "0.29.4.RELEASE"
            xmlUtilJvm = "0.91.1"
            versionCatalogUpdate = "1.0.0"

            [libraries]
            androidDesugaring = { module = "com.android.tools:desugar_jdk_libs", version.ref = "androidDesugaring" }
            angusMail = { module = "org.eclipse.angus:angus-mail", version.ref = "angusMail" }
            apacheTika = { module = "org.apache.tika:tika-parsers", version.ref = "apacheTika" }
            awsApiGateway = { module = "software.amazon.awssdk:apigatewaymanagementapi", version.ref = "awsVersion" }
            awsCloudWatch = { module = "software.amazon.awssdk:cloudwatch", version.ref = "awsVersion" }
            awsCrtClient = { module = "software.amazon.awssdk:aws-crt-client", version.ref = "awsVersion" }
            awsLambda = { module = "software.amazon.awssdk:lambda", version.ref = "awsVersion" }
            awsRds = { module = "software.amazon.awssdk:rds", version.ref = "awsVersion" }
            awsS3 = { module = "software.amazon.awssdk:s3", version.ref = "awsVersion" }
            awsSes = { module = "software.amazon.awssdk:ses", version.ref = "awsVersion" }
            azureFunctions = { module = "com.microsoft.azure.functions:azure-functions-java-library", version.ref = "azureFunctions" }
            azureStorage = { module = "com.azure:azure-storage-blob", version.ref = "azureStorage" }
            bouncyCastleBcpkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bouncyCastle" }
            bouncyCastleBcprov = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncyCastle" }
            clamAv = { module = "xyz.capybara:clamav-client", version.ref = "clamAv" }
            comLightningkiteKotlinxSerializationCsvDurable = { module = "com.lightningkite:kotlinx-serialization-csv-durable", version.ref = "kotlinxSerializationCsvDurable" }
            coroutinesCore = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
            coroutinesJdk = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8", version.ref = "coroutines" }
            coroutinesReactive = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-reactive", version.ref = "coroutines" }
            coroutinesTesting = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
            deployHelpers = "com.lightningkite:deploy-helpers:0.0.7"
            dynamodb = { module = "software.amazon.awssdk:dynamodb", version.ref = "dynamodb" }
            embedMongo = { module = "de.flapdoodle.embed:de.flapdoodle.embed.mongo", version.ref = "embedMongo" }
            embeddedPostgres = { module = "io.zonky.test:embedded-postgres", version.ref = "embeddedPostgres" }
            embeddedRedis = { module = "org.signal:embedded-redis", version.ref = "embeddedRedis" }
            exposedCore = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
            exposedJavaTime = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
            exposedJdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
        """.trimIndent()
        Toml.decodeFromString(
            VersionsToml.serializer(),
            content
        )
    }
}