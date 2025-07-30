package com.lightningkite.deployhelpers

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File

/**
 * Uploads a directory and all its contents to an S3 bucket.
 *
 * @param bucket The name of the S3 bucket to upload to
 * @param keyPrefix The prefix to use for the S3 keys (can include path separators)
 * @return The S3 URL of the folder's contents.  You probably want to append an 'index.html' for access later.
 */
fun File.uploadDirectoryToS3(
    bucket: String,
    keyPrefix: String,
    region: Region = Region.US_WEST_2,
    credentials: AwsCredentialsProvider = DefaultCredentialsProvider.builder().profileName("lk").build()
): String {
    // Validate that this is a directory
    if (!this.isDirectory) {
        throw IllegalArgumentException("File must be a directory: ${this.absolutePath}")
    }

    // Create S3 client with default credentials provider
    val s3Client = S3AsyncClient.builder()
        .region(region) // Default region, can be made configurable if needed
        .credentialsProvider(credentials)
        .build()

    try {
        // Get the base path for creating relative paths
        val basePath = this.absolutePath

        // Walk through all files in the directory
        this.walkTopDown().filter { it.isFile }.map { file ->
            // Create the S3 key by combining the prefix with the relative path
            // Handle path separators consistently (replace Windows backslashes with forward slashes)
            val relativePath = file.absolutePath.removePrefix(basePath)
                .removePrefix(File.separator)
                .replace(File.separatorChar, '/')
            val key = if (keyPrefix.isEmpty()) relativePath else "$keyPrefix/$relativePath"

            // Create the put request
            val putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()

            // Upload the file
            s3Client.putObject(putRequest, AsyncRequestBody.fromFile(file))
        }.toList().forEach {
            it.get()
        }
    } finally {
        // Close the S3 client to release resources
        s3Client.close()
    }
    return "https://$bucket.s3.amazonaws.com/$keyPrefix"
}

/**
 * Uploads a directory and all its contents to an S3 bucket.
 *
 * @param bucket The name of the S3 bucket to upload to
 * @param keyPrefix The prefix to use for the S3 keys (can include path separators)
 * @return The S3 URL of the folder's contents.  You probably want to append an 'index.html' for access later.
 */
fun uploadFilesToS3(
    bucket: String,
    keyPrefix: String,
    keys: Map<String, String>,
    region: Region = Region.US_WEST_2,
    credentials: AwsCredentialsProvider = DefaultCredentialsProvider.builder().profileName("lk").build()
): String {

    // Create S3 client with default credentials provider
    val s3Client = S3AsyncClient.builder()
        .region(region) // Default region, can be made configurable if needed
        .credentialsProvider(credentials)
        .build()
    try {
        keys.entries.map { (key, content) ->
            // Create the put request
            val putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key("$keyPrefix/$key".trim('/'))
                .build()

            // Upload the file
            s3Client.putObject(putRequest, ByteBuffersAsyncRequestBody.from("text/html", content.toByteArray()))
        }.forEach { it.get() }
    } finally {
        // Close the S3 client to release resources
        s3Client.close()
    }
    return "https://$bucket.s3.amazonaws.com/$keyPrefix"
}