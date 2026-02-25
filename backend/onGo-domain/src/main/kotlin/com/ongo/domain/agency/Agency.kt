package com.ongo.domain.agency

import java.time.LocalDateTime

data class AgencyWorkspace(
    val id: Long? = null,
    val ownerUserId: Long,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val createdAt: LocalDateTime? = null,
)

data class AgencyCreator(
    val id: Long? = null,
    val workspaceId: Long,
    val userId: Long,
    val role: String = "CREATOR",
    val joinedAt: LocalDateTime? = null,
)

data class ClientPortal(
    val id: Long? = null,
    val workspaceId: Long,
    val clientName: String? = null,
    val accessToken: String,
    val permissions: String = "{}", // JSONB as String
    val expiresAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
)
