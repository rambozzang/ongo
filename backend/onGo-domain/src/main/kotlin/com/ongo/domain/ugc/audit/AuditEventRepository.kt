package com.ongo.domain.ugc.audit

interface AuditEventRepository {
    fun save(event: AuditEvent): AuditEvent

    fun findByCampaignId(campaignId: Long, offset: Int, limit: Int): List<AuditEvent>

    fun countByCampaignId(campaignId: Long): Long
}
