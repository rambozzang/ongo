package com.ongo.domain.event

import com.ongo.common.enums.Platform

data class VideoPublishEvent(
    val videoId: Long,
    val userId: Long,
    val targetPlatforms: List<Platform>,
    val immediate: Boolean = true,
)
