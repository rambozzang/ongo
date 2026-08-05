package com.ongo.infrastructure.storage

import com.ongo.application.common.FileStoragePort
import com.ongo.infrastructure.external.storage.StorageClient
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class FileStoragePortAdapter(
    private val storageClient: StorageClient,
) : FileStoragePort {

    override fun uploadByKey(key: String, inputStream: InputStream, contentType: String, size: Long): String {
        return storageClient.uploadFile(key, inputStream, contentType, size)
    }

    override fun deleteByKey(key: String) {
        storageClient.deleteFile(key)
    }
}
