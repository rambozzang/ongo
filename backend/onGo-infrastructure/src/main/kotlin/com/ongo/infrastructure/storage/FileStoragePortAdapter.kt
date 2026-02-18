package com.ongo.infrastructure.storage

import com.ongo.application.common.FileStoragePort
import com.ongo.infrastructure.external.storage.StorageClient
import org.springframework.stereotype.Component

@Component
class FileStoragePortAdapter(
    private val storageClient: StorageClient,
) : FileStoragePort {

    override fun deleteByKey(key: String) {
        storageClient.deleteFile(key)
    }
}
