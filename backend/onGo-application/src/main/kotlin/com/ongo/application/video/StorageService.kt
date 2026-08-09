package com.ongo.application.video

import java.io.InputStream

interface StorageService {
    fun generateUploadUrl(videoId: Long, filename: String, contentType: String): String
    fun getTusEndpoint(videoId: Long): String
    /** Refresh a durable URL, using the stored URL to resolve legacy object keys. */
    fun getFileUrl(videoId: Long, storedFileUrl: String? = null): String
    /** Copy a durable video object to the storage prefix of another video row. */
    fun copyVideoFile(sourceVideoId: Long, targetVideoId: Long, storedFileUrl: String? = null): String
    fun deleteFile(videoId: Long)
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
}
