package com.ongo.domain.creatornetwork

import java.time.Instant

data class CollaborationRequest(
    val id: Long = 0,
    val fromCreatorId: Long,
    val toCreatorId: Long,
    val message: String? = null,
    val status: String = "PENDING",
    val proposedType: String? = null,
    val createdAt: Instant = Instant.now(),
)
