package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class AuditEventResponse(
    val id: Long,
    val actorId: Long,
    val action: String,
    val resourceType: String?,
    val resourceId: Long?,
    val detail: String?,
    val createdAt: LocalDateTime?,
)

data class AuditEventListResponse(
    val items: List<AuditEventResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
