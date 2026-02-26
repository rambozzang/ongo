package com.ongo.domain.livestream

import java.time.LocalDateTime

data class StreamChat(
    val id: Long? = null,
    val streamId: Long,
    val username: String,
    val message: String,
    val timestamp: LocalDateTime? = null,
    val isHighlighted: Boolean = false,
    val isModerator: Boolean = false,
)
