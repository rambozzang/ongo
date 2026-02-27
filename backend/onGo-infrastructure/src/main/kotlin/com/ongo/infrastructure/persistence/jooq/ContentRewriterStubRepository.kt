package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrewriter.*
import org.springframework.stereotype.Repository

@Repository
class ContentRewriteResultStubRepository : ContentRewriteResultRepository {
    override fun findById(id: Long): ContentRewriteResult? = null
    override fun findByUserId(userId: Long): List<ContentRewriteResult> = emptyList()
    override fun save(result: ContentRewriteResult): ContentRewriteResult = result.copy(id = 1)
    override fun update(result: ContentRewriteResult): ContentRewriteResult = result
    override fun delete(id: Long) {}
}
