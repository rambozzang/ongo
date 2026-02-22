package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_AVATAR_URL
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_CHANNEL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_HIDDEN
import com.ongo.infrastructure.persistence.jooq.Fields.IS_PINNED
import com.ongo.infrastructure.persistence.jooq.Fields.IS_REPLIED
import com.ongo.infrastructure.persistence.jooq.Fields.LIKE_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.PARENT_COMMENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_COMMENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_LIKE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_REPLY_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REPLIED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REPLY_CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.REPLY_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.SENTIMENT
import com.ongo.infrastructure.persistence.jooq.Fields.SYNCED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.COMMENTS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class CommentJooqRepository(
    private val dsl: DSLContext,
) : CommentRepository {

    override fun findById(id: Long): Comment? =
        dsl.select()
            .from(COMMENTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toComment()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<Comment> =
        dsl.select()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toComment() }

    override fun findByUserIdAndVideoId(userId: Long, videoId: Long, page: Int, size: Int): List<Comment> =
        dsl.select()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .and(VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toComment() }

    override fun findByUserIdAndPlatform(userId: Long, platform: String, page: Int, size: Int): List<Comment> =
        dsl.select()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .and(PLATFORM.eq(platform))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toComment() }

    override fun findByUserIdAndSentiment(userId: Long, sentiment: String, page: Int, size: Int): List<Comment> =
        dsl.select()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .and(SENTIMENT.eq(sentiment))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toComment() }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    override fun save(comment: Comment): Comment {
        val id = dsl.insertInto(COMMENTS)
            .set(USER_ID, comment.userId)
            .set(VIDEO_ID, comment.videoId)
            .set(PLATFORM, comment.platform)
            .set(PLATFORM_COMMENT_ID, comment.platformCommentId)
            .set(AUTHOR_NAME, comment.authorName)
            .set(AUTHOR_AVATAR_URL, comment.authorAvatarUrl)
            .set(AUTHOR_CHANNEL_URL, comment.authorChannelUrl)
            .set(CONTENT, comment.content)
            .set(SENTIMENT, comment.sentiment)
            .set(LIKE_COUNT, comment.likeCount)
            .set(REPLY_COUNT, comment.replyCount)
            .set(PARENT_COMMENT_ID, comment.parentCommentId)
            .set(PLATFORM_VIDEO_ID, comment.platformVideoId)
            .set(PLATFORM_REPLY_ID, comment.platformReplyId)
            .set(PLATFORM_LIKE_ID, comment.platformLikeId)
            .set(IS_REPLIED, comment.isReplied)
            .set(IS_HIDDEN, comment.isHidden)
            .set(IS_PINNED, comment.isPinned)
            .set(REPLY_CONTENT, comment.replyContent)
            .set(REPLIED_AT, comment.repliedAt)
            .set(PUBLISHED_AT, comment.publishedAt)
            .set(SYNCED_AT, comment.syncedAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(comment: Comment): Comment {
        dsl.update(COMMENTS)
            .set(IS_REPLIED, comment.isReplied)
            .set(REPLY_CONTENT, comment.replyContent)
            .set(REPLIED_AT, comment.repliedAt)
            .set(SENTIMENT, comment.sentiment)
            .set(LIKE_COUNT, comment.likeCount)
            .set(REPLY_COUNT, comment.replyCount)
            .set(IS_HIDDEN, comment.isHidden)
            .set(IS_PINNED, comment.isPinned)
            .set(PLATFORM_REPLY_ID, comment.platformReplyId)
            .set(PLATFORM_LIKE_ID, comment.platformLikeId)
            .set(SYNCED_AT, comment.syncedAt)
            .where(ID.eq(comment.id))
            .execute()

        return findById(comment.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(COMMENTS)
            .where(ID.eq(id))
            .execute()
    }

    override fun findByPlatformAndPlatformCommentId(platform: String, platformCommentId: String): Comment? =
        dsl.select()
            .from(COMMENTS)
            .where(PLATFORM.eq(platform))
            .and(PLATFORM_COMMENT_ID.eq(platformCommentId))
            .fetchOne()
            ?.toComment()

    override fun upsertBatch(comments: List<Comment>): Int {
        if (comments.isEmpty()) return 0

        var upserted = 0
        for (comment in comments) {
            val result = dsl.insertInto(COMMENTS)
                .set(USER_ID, comment.userId)
                .set(VIDEO_ID, comment.videoId)
                .set(PLATFORM, comment.platform)
                .set(PLATFORM_COMMENT_ID, comment.platformCommentId)
                .set(AUTHOR_NAME, comment.authorName)
                .set(AUTHOR_AVATAR_URL, comment.authorAvatarUrl)
                .set(AUTHOR_CHANNEL_URL, comment.authorChannelUrl)
                .set(CONTENT, comment.content)
                .set(SENTIMENT, comment.sentiment)
                .set(LIKE_COUNT, comment.likeCount)
                .set(REPLY_COUNT, comment.replyCount)
                .set(PARENT_COMMENT_ID, comment.parentCommentId)
                .set(PLATFORM_VIDEO_ID, comment.platformVideoId)
                .set(IS_REPLIED, comment.isReplied)
                .set(IS_HIDDEN, comment.isHidden)
                .set(IS_PINNED, comment.isPinned)
                .set(PUBLISHED_AT, comment.publishedAt)
                .set(SYNCED_AT, comment.syncedAt)
                .onConflict(PLATFORM, PLATFORM_COMMENT_ID)
                .doUpdate()
                .set(CONTENT, comment.content)
                .set(AUTHOR_NAME, comment.authorName)
                .set(AUTHOR_AVATAR_URL, comment.authorAvatarUrl)
                .set(AUTHOR_CHANNEL_URL, comment.authorChannelUrl)
                .set(LIKE_COUNT, comment.likeCount)
                .set(REPLY_COUNT, comment.replyCount)
                .set(SYNCED_AT, comment.syncedAt)
                .execute()
            upserted += result
        }
        return upserted
    }

    override fun findByUserIdFiltered(
        userId: Long,
        videoId: Long?,
        platform: String?,
        sentiment: String?,
        searchText: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        page: Int,
        size: Int,
    ): List<Comment> {
        val conditions = buildFilterConditions(userId, videoId, platform, sentiment, searchText, startDate, endDate)

        return dsl.select()
            .from(COMMENTS)
            .where(conditions)
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toComment() }
    }

    override fun countByUserIdFiltered(
        userId: Long,
        videoId: Long?,
        platform: String?,
        sentiment: String?,
        searchText: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Int {
        val conditions = buildFilterConditions(userId, videoId, platform, sentiment, searchText, startDate, endDate)

        return dsl.selectCount()
            .from(COMMENTS)
            .where(conditions)
            .fetchOne(0, Int::class.java) ?: 0
    }

    override fun countByUserIdGroupedBySentiment(userId: Long): Map<String, Int> =
        dsl.select(SENTIMENT, DSL.count())
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .groupBy(SENTIMENT)
            .fetch()
            .associate { (it.get(SENTIMENT) ?: "NEUTRAL") to (it.get(1, Int::class.java) ?: 0) }

    override fun findSentimentGroupedByDate(userId: Long, days: Int): Map<LocalDate, Map<String, Int>> {
        val dateField = DSL.field("DATE({0})", LocalDate::class.java, CREATED_AT)
        val cutoff = LocalDateTime.now().minusDays(days.toLong())

        return dsl.select(dateField, SENTIMENT, DSL.count())
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .and(CREATED_AT.greaterOrEqual(cutoff))
            .groupBy(dateField, SENTIMENT)
            .orderBy(dateField.asc())
            .fetch()
            .groupBy(
                { it.get(dateField)!! },
                { (it.get(SENTIMENT) ?: "NEUTRAL") to (it.get(2, Int::class.java) ?: 0) },
            )
            .mapValues { (_, pairs) -> pairs.toMap() }
    }

    override fun findRecentNegativeComments(userId: Long, limit: Int): List<Comment> =
        dsl.select()
            .from(COMMENTS)
            .where(USER_ID.eq(userId))
            .and(SENTIMENT.eq("NEGATIVE"))
            .orderBy(CREATED_AT.desc())
            .limit(limit)
            .fetch()
            .map { it.toComment() }

    override fun findByIds(ids: List<Long>): List<Comment> {
        if (ids.isEmpty()) return emptyList()
        return dsl.select()
            .from(COMMENTS)
            .where(ID.`in`(ids))
            .fetch()
            .map { it.toComment() }
    }

    private fun buildFilterConditions(
        userId: Long,
        videoId: Long?,
        platform: String?,
        sentiment: String?,
        searchText: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): List<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(USER_ID.eq(userId))

        videoId?.let { conditions.add(VIDEO_ID.eq(it)) }
        platform?.let { conditions.add(PLATFORM.eq(it)) }
        sentiment?.let { conditions.add(SENTIMENT.eq(it)) }
        searchText?.takeIf { it.isNotBlank() }?.let {
            val pattern = "%${it.lowercase()}%"
            conditions.add(
                DSL.lower(CONTENT).like(pattern)
                    .or(DSL.lower(AUTHOR_NAME).like(pattern))
            )
        }
        startDate?.let { conditions.add(CREATED_AT.greaterOrEqual(it)) }
        endDate?.let { conditions.add(CREATED_AT.lessOrEqual(it)) }

        return conditions
    }

    private fun Record.toComment(): Comment = Comment(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        platform = get(PLATFORM),
        platformCommentId = get(PLATFORM_COMMENT_ID),
        authorName = get(AUTHOR_NAME),
        authorAvatarUrl = get(AUTHOR_AVATAR_URL),
        authorChannelUrl = get(AUTHOR_CHANNEL_URL),
        content = get(CONTENT),
        sentiment = get(SENTIMENT) ?: "NEUTRAL",
        likeCount = get(LIKE_COUNT) ?: 0,
        replyCount = get(REPLY_COUNT) ?: 0,
        parentCommentId = get(PARENT_COMMENT_ID),
        platformVideoId = get(PLATFORM_VIDEO_ID),
        platformReplyId = get(PLATFORM_REPLY_ID),
        platformLikeId = get(PLATFORM_LIKE_ID),
        isReplied = get(IS_REPLIED) ?: false,
        isHidden = get(IS_HIDDEN) ?: false,
        isPinned = get(IS_PINNED) ?: false,
        replyContent = get(REPLY_CONTENT),
        repliedAt = localDateTime(REPLIED_AT),
        publishedAt = localDateTime(PUBLISHED_AT),
        syncedAt = localDateTime(SYNCED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
