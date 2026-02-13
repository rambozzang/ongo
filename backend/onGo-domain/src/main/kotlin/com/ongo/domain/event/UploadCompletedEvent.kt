package com.ongo.domain.event

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus

data class UploadCompletedEvent(
    val videoId: Long,
    val platform: Platform,
    val status: UploadStatus,
    val platformVideoId: String? = null,
    val errorMessage: String? = null,
)
