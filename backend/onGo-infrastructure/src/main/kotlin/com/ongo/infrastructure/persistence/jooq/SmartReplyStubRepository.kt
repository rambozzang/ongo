package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.smartreply.*
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
class SmartReplyRuleJooqRepository(
    private val dsl: DSLContext,
) : SmartReplyRuleRepository {

    companion object {
        private val TABLE = DSL.table("smart_reply_rules")
        private val NAME = DSL.field("name", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val CONTEXT = DSL.field("context", String::class.java)
        private val TRIGGER_KEYWORDS = DSL.field("trigger_keywords", String::class.java)
        private val SENTIMENT = DSL.field("sentiment", String::class.java)
        private val TONE = DSL.field("tone", String::class.java)
        private val TEMPLATE_TEXT = DSL.field("template_text", String::class.java)
        private val USE_AI = DSL.field("use_ai", Boolean::class.java)
        private val AUTO_SEND = DSL.field("auto_send", Boolean::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val REPLY_COUNT = DSL.field("reply_count", Int::class.java)
        private val LAST_USED = DSL.field("last_used", LocalDateTime::class.java)
    }

    override fun findById(id: Long): SmartReplyRule? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toRule()

    override fun findByUserId(userId: Long): List<SmartReplyRule> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toRule() }

    override fun save(rule: SmartReplyRule): SmartReplyRule {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, rule.userId)
            .set(NAME, rule.name)
            .set(IS_ACTIVE, rule.isActive)
            .set(CONTEXT, rule.context)
            .set(TRIGGER_KEYWORDS, rule.triggerKeywords)
            .set(SENTIMENT, rule.sentiment)
            .set(TONE, rule.tone)
            .set(TEMPLATE_TEXT, rule.templateText)
            .set(USE_AI, rule.useAi)
            .set(AUTO_SEND, rule.autoSend)
            .set(PLATFORM, rule.platform)
            .set(REPLY_COUNT, rule.replyCount)
            .set(LAST_USED, rule.lastUsed)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun update(rule: SmartReplyRule): SmartReplyRule {
        dsl.update(TABLE)
            .set(NAME, rule.name)
            .set(IS_ACTIVE, rule.isActive)
            .set(CONTEXT, rule.context)
            .set(TRIGGER_KEYWORDS, rule.triggerKeywords)
            .set(SENTIMENT, rule.sentiment)
            .set(TONE, rule.tone)
            .set(TEMPLATE_TEXT, rule.templateText)
            .set(USE_AI, rule.useAi)
            .set(AUTO_SEND, rule.autoSend)
            .set(PLATFORM, rule.platform)
            .set(REPLY_COUNT, rule.replyCount)
            .set(LAST_USED, rule.lastUsed)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(rule.id))
            .execute()
        return findById(rule.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toRule(): SmartReplyRule = SmartReplyRule(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        isActive = get(IS_ACTIVE) ?: true,
        context = get(CONTEXT) ?: "COMMENT",
        triggerKeywords = get(TRIGGER_KEYWORDS) ?: "[]",
        sentiment = get(SENTIMENT),
        tone = get(TONE) ?: "FRIENDLY",
        templateText = get(TEMPLATE_TEXT),
        useAi = get(USE_AI) ?: false,
        autoSend = get(AUTO_SEND) ?: false,
        platform = get(PLATFORM),
        replyCount = get(REPLY_COUNT) ?: 0,
        lastUsed = localDateTime(LAST_USED),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}

@Repository
class SmartReplySuggestionJooqRepository(
    private val dsl: DSLContext,
) : SmartReplySuggestionRepository {

    companion object {
        private val TABLE = DSL.table("smart_reply_suggestions")
        private val STR_ID = DSL.field("id", String::class.java)
        private val ORIGINAL_TEXT = DSL.field("original_text", String::class.java)
        private val ORIGINAL_AUTHOR = DSL.field("original_author", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val CONTEXT = DSL.field("context", String::class.java)
        private val SENTIMENT = DSL.field("sentiment", String::class.java)
        private val SUGGESTIONS = DSL.field("suggestions", String::class.java)
        private val VIDEO_ID_STR = DSL.field("video_id", String::class.java)
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
    }

    override fun findById(id: String): SmartReplySuggestion? =
        dsl.select().from(TABLE).where(STR_ID.eq(id)).fetchOne()?.toSuggestion()

    override fun findByUserId(userId: Long): List<SmartReplySuggestion> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch { it.toSuggestion() }

    override fun save(suggestion: SmartReplySuggestion): SmartReplySuggestion {
        dsl.insertInto(TABLE)
            .set(STR_ID, suggestion.id)
            .set(USER_ID, suggestion.userId)
            .set(ORIGINAL_TEXT, suggestion.originalText)
            .set(ORIGINAL_AUTHOR, suggestion.originalAuthor)
            .set(PLATFORM, suggestion.platform)
            .set(CONTEXT, suggestion.context)
            .set(SENTIMENT, suggestion.sentiment)
            .set(SUGGESTIONS, DSL.field("?::jsonb", String::class.java, suggestion.suggestions))
            .set(VIDEO_ID_STR, suggestion.videoId)
            .set(VIDEO_TITLE, suggestion.videoTitle)
            .execute()
        return findById(suggestion.id)!!
    }

    override fun delete(id: String) {
        dsl.deleteFrom(TABLE).where(STR_ID.eq(id)).execute()
    }

    private fun Record.toSuggestion(): SmartReplySuggestion {
        val suggestionsRaw = get("suggestions")
        return SmartReplySuggestion(
            id = get(STR_ID),
            userId = get(USER_ID),
            originalText = get(ORIGINAL_TEXT),
            originalAuthor = get(ORIGINAL_AUTHOR),
            platform = get(PLATFORM),
            context = get(CONTEXT) ?: "COMMENT",
            sentiment = get(SENTIMENT) ?: "NEUTRAL",
            suggestions = when (suggestionsRaw) {
                is String -> suggestionsRaw
                else -> suggestionsRaw?.toString() ?: "[]"
            },
            videoId = get(VIDEO_ID_STR),
            videoTitle = get(VIDEO_TITLE),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}

@Repository
class SmartReplyConfigJooqRepository(
    private val dsl: DSLContext,
) : SmartReplyConfigRepository {

    companion object {
        private val TABLE = DSL.table("smart_reply_configs")
        private val DEFAULT_TONE = DSL.field("default_tone", String::class.java)
        private val ENABLE_AUTO_REPLY = DSL.field("enable_auto_reply", Boolean::class.java)
        private val MAX_AUTO_REPLIES_PER_DAY = DSL.field("max_auto_replies_per_day", Int::class.java)
        private val EXCLUDE_KEYWORDS = DSL.field("exclude_keywords", String::class.java)
        private val REPLY_DELAY = DSL.field("reply_delay", Int::class.java)
        private val PLATFORMS = DSL.field("platforms", String::class.java)
    }

    override fun findByUserId(userId: Long): SmartReplyConfig? =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetchOne()?.toConfig()

    override fun save(config: SmartReplyConfig): SmartReplyConfig {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, config.userId)
            .set(DEFAULT_TONE, config.defaultTone)
            .set(ENABLE_AUTO_REPLY, config.enableAutoReply)
            .set(MAX_AUTO_REPLIES_PER_DAY, config.maxAutoRepliesPerDay)
            .set(EXCLUDE_KEYWORDS, config.excludeKeywords)
            .set(REPLY_DELAY, config.replyDelay)
            .set(PLATFORMS, config.platforms)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toConfig()
    }

    override fun update(config: SmartReplyConfig): SmartReplyConfig {
        dsl.update(TABLE)
            .set(DEFAULT_TONE, config.defaultTone)
            .set(ENABLE_AUTO_REPLY, config.enableAutoReply)
            .set(MAX_AUTO_REPLIES_PER_DAY, config.maxAutoRepliesPerDay)
            .set(EXCLUDE_KEYWORDS, config.excludeKeywords)
            .set(REPLY_DELAY, config.replyDelay)
            .set(PLATFORMS, config.platforms)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(config.id))
            .execute()
        return dsl.select().from(TABLE).where(ID.eq(config.id)).fetchOne()!!.toConfig()
    }

    private fun Record.toConfig(): SmartReplyConfig = SmartReplyConfig(
        id = get(ID),
        userId = get(USER_ID),
        defaultTone = get(DEFAULT_TONE) ?: "FRIENDLY",
        enableAutoReply = get(ENABLE_AUTO_REPLY) ?: false,
        maxAutoRepliesPerDay = get(MAX_AUTO_REPLIES_PER_DAY) ?: 50,
        excludeKeywords = get(EXCLUDE_KEYWORDS) ?: "[]",
        replyDelay = get(REPLY_DELAY) ?: 0,
        platforms = get(PLATFORMS) ?: "[]",
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
