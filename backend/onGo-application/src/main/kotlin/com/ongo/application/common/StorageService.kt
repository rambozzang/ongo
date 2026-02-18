package com.ongo.application.common

import java.io.InputStream

interface StorageService {
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
}
