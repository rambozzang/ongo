package com.ongo.domain.video

import com.ongo.common.enums.Platform

/** A durable publication target. Provider alone is not unique for multi-account creators. */
data class VideoUploadTarget(
    val platform: Platform,
    val channelId: Long?,
)
