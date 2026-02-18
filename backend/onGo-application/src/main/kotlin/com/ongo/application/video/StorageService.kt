package com.ongo.application.video

import java.io.InputStream

interface StorageService {
    fun generateUploadUrl(videoId: Long, filename: String, contentType: String): String
    fun getTusEndpoint(videoId: Long): String
    fun getFileUrl(videoId: Long): String
    fun deleteFile(videoId: Long)
    fun downloadFile(videoId: Long): InputStream
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
}
