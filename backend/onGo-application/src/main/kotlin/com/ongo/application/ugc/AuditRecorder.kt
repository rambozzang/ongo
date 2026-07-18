package com.ongo.application.ugc

import com.ongo.domain.ugc.audit.AuditEvent
import com.ongo.domain.ugc.audit.AuditEventRepository
import org.springframework.stereotype.Service

/**
 * UGC 감사 이벤트 기록기. 주요 상태·금액 변경 유스케이스에서 호출한다.
 */
@Service
class AuditRecorder(
    private val auditEventRepository: AuditEventRepository,
) {
    fun record(
        workspaceId: Long,
        campaignId: Long?,
        actorId: Long,
        action: String,
        resourceType: String? = null,
        resourceId: Long? = null,
        detail: String? = null,
    ) {
        auditEventRepository.save(
            AuditEvent(
                workspaceId = workspaceId,
                campaignId = campaignId,
                actorId = actorId,
                action = action,
                resourceType = resourceType,
                resourceId = resourceId,
                detail = detail,
            ),
        )
    }
}
