package com.ongo.infrastructure.external.storage

import com.ongo.application.common.StorageService
import java.io.InputStream

data class ObjectMetadata(val contentLength: Long, val contentType: String?, val eTag: String?)

interface StorageClient : StorageService {
    override fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun getFileUrl(key: String): String
    fun copyObject(sourceKey: String, targetKey: String)
    fun deleteFile(key: String)
    fun generatePresignedUploadUrl(key: String, contentType: String, expirationMinutes: Int): String
    fun listObjects(prefix: String): List<String>
    fun generatePresignedDownloadUrl(key: String, expirationMinutes: Int): String
    fun objectExists(key: String): Boolean
    fun getObjectMetadata(key: String): ObjectMetadata?
}
