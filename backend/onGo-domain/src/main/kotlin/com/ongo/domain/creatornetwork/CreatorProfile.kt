package com.ongo.domain.creatornetwork

import java.time.Instant

data class CreatorProfile(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val avatarUrl: String? = null,
    val platform: String,
    val subscribers: Long = 0,
    val category: String? = null,
    val matchScore: Int = 0,
    val isConnected: Boolean = false,
    val bio: String? = null,
    val joinedAt: Instant = Instant.now(),
)
