package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.publicapi.PublicApiPost
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.publicapi.PublicApiPostStatus
import com.ongo.domain.publicapi.PublicApiPostType
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PublicApiPostJooqRepository(
    private val dsl: DSLContext,
) : PublicApiPostRepository {

    override fun save(post: PublicApiPost): PublicApiPost {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, post.userId)
            .set(WORKSPACE_ID, post.workspaceId)
            .set(VIDEO_ID, post.videoId)
            .set(POST_TYPE, post.type.name)
            .set(STATUS, post.status.name)
            .set(SCHEDULED_AT, post.scheduledAt)
            .set(ERROR_MESSAGE, post.errorMessage?.take(MAX_ERROR_LENGTH))
            .set(PAYLOAD_JSON, post.payloadJson)
            .set(CREATED_AT, post.createdAt ?: LocalDateTime.now())
            .set(UPDATED_AT, post.updatedAt ?: LocalDateTime.now())
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id) ?: error("공개 API 게시 작업을 저장한 뒤 조회할 수 없습니다: $id")
    }

    override fun update(post: PublicApiPost): PublicApiPost {
        dsl.update(TABLE)
            .set(WORKSPACE_ID, post.workspaceId)
            .set(VIDEO_ID, post.videoId)
            .set(POST_TYPE, post.type.name)
            .set(STATUS, post.status.name)
            .set(SCHEDULED_AT, post.scheduledAt)
            .set(ERROR_MESSAGE, post.errorMessage?.take(MAX_ERROR_LENGTH))
            .set(PAYLOAD_JSON, post.payloadJson)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(post.id))
            .and(USER_ID.eq(post.userId))
            .execute()
        return findById(post.id) ?: error("공개 API 게시 작업을 찾을 수 없습니다: ${post.id}")
    }

    override fun findById(id: Long): PublicApiPost? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toPost()

    override fun findByIdAndUserId(id: Long, userId: Long): PublicApiPost? =
        dsl.select().from(TABLE).where(ID.eq(id)).and(USER_ID.eq(userId)).fetchOne()?.toPost()

    override fun findByUserId(userId: Long, limit: Int): List<PublicApiPost> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(limit.coerceIn(1, 100))
            .fetch()
            .map { it.toPost() }

    override fun findByUserIdAndWorkspaceId(userId: Long, workspaceId: Long, limit: Int): List<PublicApiPost> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .and(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .limit(limit.coerceIn(1, 100))
            .fetch()
            .map { it.toPost() }

    override fun findByUserIdAndDateRange(
        userId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        limit: Int,
    ): List<PublicApiPost> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .and(
                SCHEDULED_AT.between(start, end)
                    .or(SCHEDULED_AT.isNull().and(CREATED_AT.between(start, end)))
            )
            .orderBy(CREATED_AT.desc())
            .limit(limit.coerceIn(1, 100))
            .fetch()
            .map { it.toPost() }

    override fun findByUserIdAndWorkspaceIdAndDateRange(
        userId: Long,
        workspaceId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        limit: Int,
    ): List<PublicApiPost> =
        dsl.select()
            .from(TABLE)
            .where(USER_ID.eq(userId))
            .and(WORKSPACE_ID.eq(workspaceId))
            .and(
                SCHEDULED_AT.between(start, end)
                    .or(SCHEDULED_AT.isNull().and(CREATED_AT.between(start, end)))
            )
            .orderBy(CREATED_AT.desc())
            .limit(limit.coerceIn(1, 100))
            .fetch()
            .map { it.toPost() }

    override fun deleteDraft(id: Long, userId: Long): Boolean =
        dsl.deleteFrom(TABLE)
            .where(ID.eq(id))
            .and(USER_ID.eq(userId))
            .and(STATUS.eq(PublicApiPostStatus.DRAFT.name))
            .execute() == 1

    private fun Record.toPost() = PublicApiPost(
        id = get(ID)!!,
        userId = get(USER_ID)!!,
        workspaceId = get(WORKSPACE_ID),
        videoId = get(VIDEO_ID)!!,
        type = PublicApiPostType.valueOf(get(POST_TYPE)!!),
        status = PublicApiPostStatus.valueOf(get(STATUS)!!),
        scheduledAt = get(SCHEDULED_AT),
        errorMessage = get(ERROR_MESSAGE),
        payloadJson = get(PAYLOAD_JSON)!!,
        createdAt = get(CREATED_AT),
        updatedAt = get(UPDATED_AT),
    )

    companion object {
        private val TABLE = DSL.table(DSL.name("public_api_posts"))
        private val ID = DSL.field(DSL.name("id"), Long::class.java)
        private val USER_ID = DSL.field(DSL.name("user_id"), Long::class.java)
        private val WORKSPACE_ID = DSL.field(DSL.name("workspace_id"), Long::class.java)
        private val VIDEO_ID = DSL.field(DSL.name("video_id"), Long::class.java)
        private val POST_TYPE = DSL.field(DSL.name("post_type"), String::class.java)
        private val STATUS = DSL.field(DSL.name("status"), String::class.java)
        private val SCHEDULED_AT = DSL.field(DSL.name("scheduled_at"), LocalDateTime::class.java)
        private val ERROR_MESSAGE = DSL.field(DSL.name("error_message"), String::class.java)
        private val PAYLOAD_JSON = DSL.field(DSL.name("payload_json"), String::class.java)
        private val CREATED_AT = DSL.field(DSL.name("created_at"), LocalDateTime::class.java)
        private val UPDATED_AT = DSL.field(DSL.name("updated_at"), LocalDateTime::class.java)
        private const val MAX_ERROR_LENGTH = 2_000
    }
}
