package com.ongo.application.agency.dto

import java.time.LocalDateTime

data class AgencyWorkspaceResponse(
    val id: Long,
    val ownerUserId: Long,
    val name: String,
    val description: String?,
    val logoUrl: String?,
    val creators: List<AgencyCreatorResponse>,
    val createdAt: LocalDateTime?,
)

data class CreateWorkspaceRequest(
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
)

data class UpdateWorkspaceRequest(
    val name: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
)

data class AgencyCreatorResponse(
    val id: Long,
    val userId: Long,
    val role: String,
    val joinedAt: LocalDateTime?,
)

data class AddCreatorRequest(
    val userId: Long,
    val role: String = "CREATOR",
)

data class ClientPortalResponse(
    val id: Long,
    val workspaceId: Long,
    val clientName: String?,
    val accessToken: String,
    val permissions: String,
    val expiresAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CreatePortalRequest(
    val clientName: String? = null,
    val permissions: String = "{}",
    val expiresAt: LocalDateTime? = null,
)
