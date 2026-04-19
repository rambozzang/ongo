package com.ongo.domain.contentsource

interface ContentSourceRepository {
    fun findById(id: Long): ContentSource?
    fun findByUserAndType(userId: Long, type: ContentSourceType): ContentSource?
    fun findAllByUser(userId: Long): List<ContentSource>
    fun save(source: ContentSource): ContentSource
    fun updateStatus(id: Long, status: ContentSourceStatus, lastError: String?)
    fun updateTokens(
        id: Long,
        accessTokenEncrypted: String,
        refreshTokenEncrypted: String?,
        expiresAt: java.time.Instant?,
    )
    fun markUsed(id: Long)
    fun delete(id: Long)
}
