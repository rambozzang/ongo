package com.ongo.infrastructure.external.storage

import com.ongo.application.common.StorageService
import java.io.InputStream

interface StorageClient : StorageService {
    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun getFileUrl(key: String): String
    fun deleteFile(key: String)
    fun generatePresignedUploadUrl(key: String, contentType: String, expirationMinutes: Int): String
    fun listObjects(prefix: String): List<String>
    fun downloadFile(key: String): InputStream
    fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String
}
