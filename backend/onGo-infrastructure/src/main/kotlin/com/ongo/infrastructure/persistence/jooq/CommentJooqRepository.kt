package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_AVATAR_URL
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_REPLIED
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_COMMENT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REPLIED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REPLY_CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.SENTIMENT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.COMMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

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
            .set(CONTENT, comment.content)
            .set(SENTIMENT, comment.sentiment)
            .set(IS_REPLIED, comment.isReplied)
            .set(REPLY_CONTENT, comment.replyContent)
            .set(REPLIED_AT, comment.repliedAt)
            .set(PUBLISHED_AT, comment.publishedAt)
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
            .where(ID.eq(comment.id))
            .execute()

        return findById(comment.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(COMMENTS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toComment(): Comment = Comment(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        platform = get(PLATFORM),
        platformCommentId = get(PLATFORM_COMMENT_ID),
        authorName = get(AUTHOR_NAME),
        authorAvatarUrl = get(AUTHOR_AVATAR_URL),
        content = get(CONTENT),
        sentiment = get(SENTIMENT) ?: "NEUTRAL",
        isReplied = get(IS_REPLIED) ?: false,
        replyContent = get(REPLY_CONTENT),
        repliedAt = localDateTime(REPLIED_AT),
        publishedAt = localDateTime(PUBLISHED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
