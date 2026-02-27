package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sociallistening.*
import org.springframework.stereotype.Repository

@Repository
class BrandMentionStubRepository : BrandMentionRepository {
    override fun findById(id: Long): BrandMention? = null
    override fun findByUserId(userId: Long): List<BrandMention> = emptyList()
    override fun findByUserIdAndPeriod(userId: Long, days: Int): List<BrandMention> = emptyList()
    override fun save(mention: BrandMention): BrandMention = mention.copy(id = 1)
    override fun update(mention: BrandMention): BrandMention = mention
    override fun delete(id: Long) {}
}

@Repository
class KeywordAlertStubRepository : KeywordAlertRepository {
    override fun findById(id: Long): KeywordAlert? = null
    override fun findByUserId(userId: Long): List<KeywordAlert> = emptyList()
    override fun save(alert: KeywordAlert): KeywordAlert = alert.copy(id = 1)
    override fun update(alert: KeywordAlert): KeywordAlert = alert
    override fun delete(id: Long) {}
}
