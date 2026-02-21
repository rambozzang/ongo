package com.ongo.application.workspace.dto

import java.time.LocalDateTime

data class WorkspaceResponse(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val logoUrl: String?,
    val memberCount: Int,
    val createdAt: LocalDateTime?,
)

data class CreateWorkspaceRequest(
    val name: String,
    val slug: String,
    val description: String? = null,
)

data class UpdateWorkspaceRequest(
    val name: String? = null,
    val slug: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
)
