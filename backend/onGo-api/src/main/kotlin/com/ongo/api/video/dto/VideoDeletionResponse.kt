package com.ongo.api.video.dto

import com.ongo.common.enums.Platform

data class VideoDeletionResponse(
    val videoId: Long,
    val externalFailures: List<ExternalDeletionFailureResponse> = emptyList(),
    val storageDeletionFailed: Boolean = false,
)

data class ExternalDeletionFailureResponse(
    val platform: Platform,
    val reason: String,
)
