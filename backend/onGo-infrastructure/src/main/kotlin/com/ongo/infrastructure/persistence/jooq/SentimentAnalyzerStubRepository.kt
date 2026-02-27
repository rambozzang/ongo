package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.sentimentanalyzer.CommentSentiment
import com.ongo.domain.sentimentanalyzer.CommentSentimentRepository
import com.ongo.domain.sentimentanalyzer.SentimentResult
import com.ongo.domain.sentimentanalyzer.SentimentResultRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ANALYZED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_SENTIMENT_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENT_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT_TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.KEYWORDS
import com.ongo.infrastructure.persistence.jooq.Fields.NEGATIVE_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.NEUTRAL_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.POSITIVE_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.POSITIVE_RATE
import com.ongo.infrastructure.persistence.jooq.Fields.RESULT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.SENTIMENT
import com.ongo.infrastructure.persistence.jooq.Fields.TOP_NEGATIVE_KEYWORDS
import com.ongo.infrastructure.persistence.jooq.Fields.TOP_POSITIVE_KEYWORDS
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_COMMENTS
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.COMMENT_SENTIMENTS
import com.ongo.infrastructure.persistence.jooq.Tables.SENTIMENT_RESULTS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import kotlin.math.roundToInt

@Repository
class SentimentResultJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : SentimentResultRepository {

    override fun findByWorkspaceId(workspaceId: Long): List<SentimentResult> =
        dsl.select()
            .from(SENTIMENT_RESULTS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toSentimentResult() }

    override fun findById(id: Long): SentimentResult? =
        dsl.select()
            .from(SENTIMENT_RESULTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toSentimentResult()

    override fun save(result: SentimentResult): SentimentResult {
        val positiveKwJsonb = JSONB.jsonb(objectMapper.writeValueAsString(result.topPositiveKeywords))
        val negativeKwJsonb = JSONB.jsonb(objectMapper.writeValueAsString(result.topNegativeKeywords))

        val id = dsl.insertInto(SENTIMENT_RESULTS)
            .set(WORKSPACE_ID, result.workspaceId)
            .set(CONTENT_TITLE, result.contentTitle)
            .set(PLATFORM, result.platform)
            .set(TOTAL_COMMENTS, result.totalComments)
            .set(POSITIVE_COUNT, result.positiveCount)
            .set(NEUTRAL_COUNT, result.neutralCount)
            .set(NEGATIVE_COUNT, result.negativeCount)
            .set(POSITIVE_RATE, result.positiveRate)
            .set(AVG_SENTIMENT_SCORE, result.avgSentimentScore)
            .set(TOP_POSITIVE_KEYWORDS, positiveKwJsonb.data())
            .set(TOP_NEGATIVE_KEYWORDS, negativeKwJsonb.data())
            .set(ANALYZED_AT, result.analyzedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    private fun Record.toSentimentResult(): SentimentResult {
        val positiveKeywords = parseJsonStringList(get(TOP_POSITIVE_KEYWORDS))
        val negativeKeywords = parseJsonStringList(get(TOP_NEGATIVE_KEYWORDS))

        return SentimentResult(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            contentTitle = get(CONTENT_TITLE),
            platform = get(PLATFORM),
            totalComments = get(TOTAL_COMMENTS) ?: 0,
            positiveCount = get(POSITIVE_COUNT) ?: 0,
            neutralCount = get(NEUTRAL_COUNT) ?: 0,
            negativeCount = get(NEGATIVE_COUNT) ?: 0,
            positiveRate = get(POSITIVE_RATE) ?: BigDecimal.ZERO,
            avgSentimentScore = get(AVG_SENTIMENT_SCORE) ?: 0,
            topPositiveKeywords = positiveKeywords,
            topNegativeKeywords = negativeKeywords,
            analyzedAt = localDateTime(ANALYZED_AT) ?: java.time.LocalDateTime.now(),
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }

    private fun parseJsonStringList(raw: Any?): List<String> {
        if (raw == null) return emptyList()
        val str = when (raw) {
            is JSONB -> raw.data()
            else -> raw.toString()
        }
        return try {
            objectMapper.readValue<List<String>>(str)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Repository
class CommentSentimentJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : CommentSentimentRepository {

    override fun findByResultId(resultId: Long): List<CommentSentiment> =
        dsl.select()
            .from(COMMENT_SENTIMENTS)
            .where(RESULT_ID.eq(resultId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCommentSentiment() }

    override fun findByResultIdAndSentiment(resultId: Long, sentiment: String): List<CommentSentiment> =
        dsl.select()
            .from(COMMENT_SENTIMENTS)
            .where(RESULT_ID.eq(resultId))
            .and(SENTIMENT.eq(sentiment))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCommentSentiment() }

    override fun save(comment: CommentSentiment): CommentSentiment {
        val keywordsJsonb = JSONB.jsonb(objectMapper.writeValueAsString(comment.keywords))

        val id = dsl.insertInto(COMMENT_SENTIMENTS)
            .set(RESULT_ID, comment.resultId)
            .set(COMMENT_TEXT, comment.commentText)
            .set(AUTHOR_NAME, comment.authorName)
            .set(SENTIMENT, comment.sentiment)
            .set(SCORE, comment.score.toDouble())
            .set(KEYWORDS, keywordsJsonb.data())
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(COMMENT_SENTIMENTS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toCommentSentiment()
    }

    override fun saveBatch(comments: List<CommentSentiment>): List<CommentSentiment> =
        comments.map { save(it) }

    private fun Record.toCommentSentiment(): CommentSentiment {
        val keywordsRaw = get(KEYWORDS)
        val keywords: List<String> = if (keywordsRaw != null) {
            val str = when (keywordsRaw) {
                is JSONB -> keywordsRaw.data()
                else -> keywordsRaw.toString()
            }
            try {
                objectMapper.readValue<List<String>>(str)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        return CommentSentiment(
            id = get(ID),
            resultId = get(RESULT_ID),
            commentText = get(COMMENT_TEXT),
            authorName = get(AUTHOR_NAME),
            sentiment = get(SENTIMENT) ?: "NEUTRAL",
            score = get(SCORE)?.roundToInt() ?: 50,
            keywords = keywords,
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}
