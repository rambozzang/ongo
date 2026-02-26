package com.ongo.domain.smartreply

interface SmartReplyConfigRepository {
    fun findByUserId(userId: Long): SmartReplyConfig?
    fun save(config: SmartReplyConfig): SmartReplyConfig
    fun update(config: SmartReplyConfig): SmartReplyConfig
}
