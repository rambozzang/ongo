package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.commentsummary.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class CommentSummaryResultJooqRepository(
    private val dsl: DSLContext,
) : CommentSummaryResultRepository {

    companion object {
        private val TABLE = DSL.table("comment_summary_results")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val TOTAL_COMMENTS = DSL.field("total_comments", Int::class.java)
        private val POSITIVE_PCT = DSL.field("positive_pct", Int::class.java)
        private val NEGATIVE_PCT = DSL.field("negative_pct", Int::class.java)
        private val NEUTRAL_PCT = DSL.field("neutral_pct", Int::class.java)
        private val TOP_TOPICS = DSL.field("top_topics", String::class.java)
        private val AI_SUMMARY = DSL.field("ai_summary", String::class.java)
        private val ANALYZED_AT = DSL.field("analyzed_at", java.time.LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<CommentSummaryResult> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toResult() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<CommentSummaryResult> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toResult() }

    override fun save(result: CommentSummaryResult): CommentSummaryResult {
        val topTopicsJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(result.topTopics)
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, result.workspaceId)
            .set(VIDEO_TITLE, result.videoTitle)
            .set(PLATFORM, result.platform)
            .set(TOTAL_COMMENTS, result.totalComments)
            .set(POSITIVE_PCT, result.positivePct)
            .set(NEGATIVE_PCT, result.negativePct)
            .set(NEUTRAL_PCT, result.neutralPct)
            .set(TOP_TOPICS, DSL.field("?::jsonb", String::class.java, topTopicsJson))
            .set(AI_SUMMARY, result.aiSummary)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toResult()
    }

    private fun Record.toResult(): CommentSummaryResult {
        val topTopicsRaw = get("top_topics")
        val topTopicsStr = when (topTopicsRaw) {
            is String -> topTopicsRaw
            else -> topTopicsRaw?.toString() ?: "[]"
        }
        val topTopicsList = topTopicsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return CommentSummaryResult(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            videoTitle = get(VIDEO_TITLE),
            platform = get(PLATFORM),
            totalComments = get(TOTAL_COMMENTS) ?: 0,
            positivePct = get(POSITIVE_PCT) ?: 0,
            negativePct = get(NEGATIVE_PCT) ?: 0,
            neutralPct = get(NEUTRAL_PCT) ?: 0,
            topTopics = topTopicsList,
            aiSummary = get(AI_SUMMARY),
            analyzedAt = localDateTime(ANALYZED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}

@Repository
class TopCommentJooqRepository(
    private val dsl: DSLContext,
) : TopCommentRepository {

    companion object {
        private val TABLE = DSL.table("top_comments")
        private val SUMMARY_ID = DSL.field("summary_id", Long::class.java)
        private val AUTHOR = DSL.field("author", String::class.java)
        private val TEXT = DSL.field("text", String::class.java)
        private val LIKES = DSL.field("likes", Int::class.java)
        private val SENTIMENT = DSL.field("sentiment", String::class.java)
        private val IS_HIGHLIGHTED = DSL.field("is_highlighted", Boolean::class.java)
    }

    override fun findBySummaryId(summaryId: Long): List<TopComment> =
        dsl.select().from(TABLE)
            .where(SUMMARY_ID.eq(summaryId))
            .fetch { it.toComment() }

    private fun Record.toComment(): TopComment = TopComment(
        id = get(ID),
        summaryId = get(SUMMARY_ID),
        author = get(AUTHOR),
        text = get(TEXT),
        likes = get(LIKES) ?: 0,
        sentiment = get(SENTIMENT) ?: "NEUTRAL",
        isHighlighted = get(IS_HIGHLIGHTED) ?: false,
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
