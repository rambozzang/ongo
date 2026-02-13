package com.ongo.infrastructure.upload

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class TusUploadConfig(
    @Value("\${ongo.upload.tus.storage-path:/tmp/ongo-tus}")
    val storagePath: String,

    @Value("\${ongo.upload.tus.max-file-size:2147483648}")
    val maxFileSize: Long,

    @Value("\${ongo.upload.tus.expiration-hours:24}")
    val expirationHours: Int,
)
