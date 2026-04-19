package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.comment.CommentAiReply
import com.ongo.domain.comment.CommentAiReplyRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CommentAiReplyJooqRepository(
    private val dsl: DSLContext,
) : CommentAiReplyRepository {

    companion object {
        private val TABLE = DSL.table("comment_ai_replies")
        private val COMMENT_ID = DSL.field("comment_id", Long::class.java)
        private val GENERATED_REPLY = DSL.field("generated_reply", String::class.java)
        private val TONE = DSL.field("tone", String::class.java)
        private val IS_USED = DSL.field("is_used", Boolean::class.java)
    }

    override fun findByCommentId(commentId: Long): List<CommentAiReply> =
        dsl.select()
            .from(TABLE)
            .where(COMMENT_ID.eq(commentId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCommentAiReply() }

    override fun findByUserId(userId: Long): List<CommentAiReply> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCommentAiReply() }

    override fun save(reply: CommentAiReply): CommentAiReply {
        val id = dsl.insertInto(TABLE)
            .set(COMMENT_ID, reply.commentId)
            .set(USER_ID, reply.userId)
            .set(GENERATED_REPLY, reply.generatedReply)
            .set(TONE, reply.tone)
            .set(IS_USED, reply.isUsed)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toCommentAiReply()
    }

    override fun markAsUsed(id: Long) {
        dsl.update(TABLE)
            .set(IS_USED, true)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toCommentAiReply(): CommentAiReply = CommentAiReply(
        id = get(ID),
        commentId = get(COMMENT_ID),
        userId = get(USER_ID),
        generatedReply = get(GENERATED_REPLY),
        tone = get(TONE),
        isUsed = get(IS_USED) ?: false,
        createdAt = localDateTime(CREATED_AT),
    )
}
