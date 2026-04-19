package com.ongo.domain.channelaudit

interface ChannelAuditRepository {
    fun findByUserId(userId: Long, page: Int, size: Int): List<ChannelAuditReport>
    fun countByUserId(userId: Long): Int
    fun save(report: ChannelAuditReport): ChannelAuditReport
    fun findById(id: Long): ChannelAuditReport?
}
