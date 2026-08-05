package com.ongo.application.common

import java.io.InputStream

interface FileStoragePort {
    fun uploadByKey(key: String, inputStream: InputStream, contentType: String, size: Long): String
    fun deleteByKey(key: String)
}
