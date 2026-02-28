package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sociallistening.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BrandMentionJooqRepository(
    private val dsl: DSLContext,
) : BrandMentionRepository {

    companion object {
        private val TABLE = DSL.table("brand_mentions")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val MENTION_TEXT = DSL.field("mention_text", String::class.java)
        private val AUTHOR_NAME = DSL.field("author_name", String::class.java)
        private val AUTHOR_URL = DSL.field("author_url", String::class.java)
        private val SENTIMENT = DSL.field("sentiment", String::class.java)
        private val REACH = DSL.field("reach", Long::class.java)
        private val SOURCE_URL = DSL.field("source_url", String::class.java)
        private val MENTIONED_AT = DSL.field("mentioned_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): BrandMention? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toBrandMention()

    override fun findByUserId(userId: Long): List<BrandMention> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toBrandMention() }

    override fun findByUserIdAndPeriod(userId: Long, days: Int): List<BrandMention> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(MENTIONED_AT.ge(LocalDateTime.now().minusDays(days.toLong())))
            .fetch().map { it.toBrandMention() }

    override fun save(mention: BrandMention): BrandMention {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, mention.userId)
            .set(PLATFORM, mention.platform)
            .set(MENTION_TEXT, mention.mentionText)
            .set(AUTHOR_NAME, mention.authorName)
            .set(AUTHOR_URL, mention.authorUrl)
            .set(SENTIMENT, mention.sentiment)
            .set(REACH, mention.reach)
            .set(SOURCE_URL, mention.sourceUrl)
            .set(MENTIONED_AT, mention.mentionedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(mention: BrandMention): BrandMention {
        dsl.update(TABLE)
            .set(PLATFORM, mention.platform)
            .set(MENTION_TEXT, mention.mentionText)
            .set(AUTHOR_NAME, mention.authorName)
            .set(AUTHOR_URL, mention.authorUrl)
            .set(SENTIMENT, mention.sentiment)
            .set(REACH, mention.reach)
            .set(SOURCE_URL, mention.sourceUrl)
            .set(MENTIONED_AT, mention.mentionedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(mention.id))
            .execute()
        return findById(mention.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toBrandMention(): BrandMention =
        BrandMention(
            id = get(ID),
            userId = get(USER_ID),
            platform = get(PLATFORM),
            mentionText = get(MENTION_TEXT),
            authorName = get(AUTHOR_NAME),
            authorUrl = get(AUTHOR_URL),
            sentiment = get(SENTIMENT) ?: "NEUTRAL",
            reach = get(REACH) ?: 0,
            sourceUrl = get(SOURCE_URL),
            mentionedAt = localDateTime(MENTIONED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}

@Repository
class KeywordAlertJooqRepository(
    private val dsl: DSLContext,
) : KeywordAlertRepository {

    companion object {
        private val TABLE = DSL.table("keyword_alerts")
        private val KEYWORD = DSL.field("keyword", String::class.java)
        private val PLATFORMS = DSL.field("platforms", String::class.java)
        private val ENABLED = DSL.field("enabled", Boolean::class.java)
        private val NOTIFY_EMAIL = DSL.field("notify_email", Boolean::class.java)
        private val NOTIFY_PUSH = DSL.field("notify_push", Boolean::class.java)
        private val LAST_TRIGGERED_AT = DSL.field("last_triggered_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): KeywordAlert? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toKeywordAlert()

    override fun findByUserId(userId: Long): List<KeywordAlert> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toKeywordAlert() }

    override fun save(alert: KeywordAlert): KeywordAlert {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, alert.userId)
            .set(KEYWORD, alert.keyword)
            .set(PLATFORMS, alert.platforms)
            .set(ENABLED, alert.enabled)
            .set(NOTIFY_EMAIL, alert.notifyEmail)
            .set(NOTIFY_PUSH, alert.notifyPush)
            .set(LAST_TRIGGERED_AT, alert.lastTriggeredAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(alert: KeywordAlert): KeywordAlert {
        dsl.update(TABLE)
            .set(KEYWORD, alert.keyword)
            .set(PLATFORMS, alert.platforms)
            .set(ENABLED, alert.enabled)
            .set(NOTIFY_EMAIL, alert.notifyEmail)
            .set(NOTIFY_PUSH, alert.notifyPush)
            .set(LAST_TRIGGERED_AT, alert.lastTriggeredAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(alert.id))
            .execute()
        return findById(alert.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toKeywordAlert(): KeywordAlert =
        KeywordAlert(
            id = get(ID),
            userId = get(USER_ID),
            keyword = get(KEYWORD),
            platforms = get(PLATFORMS) ?: "[]",
            enabled = get(ENABLED) ?: true,
            notifyEmail = get(NOTIFY_EMAIL) ?: false,
            notifyPush = get(NOTIFY_PUSH) ?: true,
            lastTriggeredAt = localDateTime(LAST_TRIGGERED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}
