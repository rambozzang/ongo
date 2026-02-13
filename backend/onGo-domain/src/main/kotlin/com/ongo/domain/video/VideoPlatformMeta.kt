package com.ongo.domain.video

import com.ongo.common.enums.Visibility

data class VideoPlatformMeta(
    val id: Long? = null,
    val videoUploadId: Long,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val visibility: Visibility = Visibility.PUBLIC,
    val customThumbnailUrl: String? = null,
)
