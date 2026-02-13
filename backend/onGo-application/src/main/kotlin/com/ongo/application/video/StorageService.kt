package com.ongo.application.video

import com.ongo.common.enums.Platform
import java.io.InputStream

interface StorageService {
    fun generateUploadUrl(videoId: Long, filename: String, contentType: String): String
    fun getTusEndpoint(videoId: Long): String
    fun getFileUrl(videoId: Long): String
    fun deleteFile(videoId: Long)
    fun downloadFile(videoId: Long): InputStream
    fun uploadVariant(videoId: Long, platform: Platform, inputStream: InputStream, size: Long): String
}
