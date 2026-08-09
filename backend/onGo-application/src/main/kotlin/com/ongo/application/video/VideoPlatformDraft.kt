package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility

/** A platform-specific metadata snapshot kept with a video draft. */
data class VideoPlatformDraft(
    val platform: Platform,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val visibility: Visibility,
    val customThumbnailUrl: String? = null,
)
