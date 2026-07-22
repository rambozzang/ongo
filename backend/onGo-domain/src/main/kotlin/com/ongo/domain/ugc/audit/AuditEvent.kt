package com.ongo.domain.ugc.audit

import java.time.LocalDateTime

/**
 * UGC 감사 이벤트. 주요 상태·금액 변경을 행위자·시각·대상과 함께 기록한다.
 */
data class AuditEvent(
    val id: Long? = null,
    val workspaceId: Long,
    val campaignId: Long? = null,
    val actorId: Long,
    val action: String,
    val resourceType: String? = null,
    val resourceId: Long? = null,
    val detail: String? = null,
    val createdAt: LocalDateTime? = null,
)
