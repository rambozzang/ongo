package com.ongo.infrastructure.external.storage

import java.io.InputStream

interface StorageClient {
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun getFileUrl(key: String): String
    fun deleteFile(key: String)
    fun generatePresignedUploadUrl(key: String, contentType: String, expirationMinutes: Int): String
}
