package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentrewriter.*
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
class ContentRewriteResultJooqRepository(
    private val dsl: DSLContext,
) : ContentRewriteResultRepository {

    companion object {
        private val TABLE = DSL.table("content_rewrite_results")
        private val SOURCE_TEXT = DSL.field("source_text", String::class.java)
        private val SOURCE_TYPE = DSL.field("source_type", String::class.java)
        private val TARGET_FORMATS = DSL.field("target_formats", String::class.java)
        private val RESULTS = DSL.field("results", String::class.java)
    }

    override fun findById(id: Long): ContentRewriteResult? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toResult()

    override fun findByUserId(userId: Long): List<ContentRewriteResult> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toResult() }

    override fun save(result: ContentRewriteResult): ContentRewriteResult {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, result.userId)
            .set(SOURCE_TEXT, result.sourceText)
            .set(SOURCE_TYPE, result.sourceType)
            .set(TARGET_FORMATS, result.targetFormats)
            .set(RESULTS, DSL.field("?::jsonb", String::class.java, result.results))
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(result: ContentRewriteResult): ContentRewriteResult {
        dsl.update(TABLE)
            .set(SOURCE_TEXT, result.sourceText)
            .set(SOURCE_TYPE, result.sourceType)
            .set(TARGET_FORMATS, result.targetFormats)
            .set(RESULTS, DSL.field("?::jsonb", String::class.java, result.results))
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(result.id))
            .execute()

        return findById(result.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toResult(): ContentRewriteResult {
        val resultsRaw = get("results")
        return ContentRewriteResult(
            id = get(ID),
            userId = get(USER_ID),
            sourceText = get(SOURCE_TEXT),
            sourceType = get(SOURCE_TYPE),
            targetFormats = get(TARGET_FORMATS) ?: "[]",
            results = when (resultsRaw) {
                is String -> resultsRaw
                else -> resultsRaw?.toString() ?: "[]"
            },
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
