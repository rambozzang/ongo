package com.ongo.domain.contentrewriter

interface ContentRewriteResultRepository {
    fun findById(id: Long): ContentRewriteResult?
    fun findByUserId(userId: Long): List<ContentRewriteResult>
    fun save(result: ContentRewriteResult): ContentRewriteResult
    fun update(result: ContentRewriteResult): ContentRewriteResult
    fun delete(id: Long)
}
