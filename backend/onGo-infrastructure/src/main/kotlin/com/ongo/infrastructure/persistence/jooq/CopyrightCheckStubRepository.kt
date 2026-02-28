package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.copyrightcheck.*
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class CopyrightCheckJooqRepository(
    private val dsl: DSLContext,
) : CopyrightCheckRepository {

    companion object {
        private val TABLE = DSL.table("copyright_check_results")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val ISSUES = DSL.field("issues", String::class.java)
        private val MUSIC_DETECTED = DSL.field("music_detected", String::class.java)
        private val MONETIZATION_ELIGIBLE = DSL.field("monetization_eligible", Boolean::class.java)
        private val PLATFORM_CHECKS = DSL.field("platform_checks", String::class.java)
        private val CHECKED_AT = DSL.field("checked_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): CopyrightCheckResult? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toCopyrightCheckResult()

    override fun findByUserId(userId: Long): List<CopyrightCheckResult> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId)).fetch().map { it.toCopyrightCheckResult() }

    override fun save(result: CopyrightCheckResult): CopyrightCheckResult {
        val newId = dsl.insertInto(TABLE)
            .set(USER_ID, result.userId)
            .set(VIDEO_ID, result.videoId)
            .set(VIDEO_TITLE, result.videoTitle)
            .set(STATUS, result.status)
            .set(ISSUES, DSL.field("?::jsonb", String::class.java, result.issues))
            .set(MUSIC_DETECTED, DSL.field("?::jsonb", String::class.java, result.musicDetected))
            .set(MONETIZATION_ELIGIBLE, result.monetizationEligible)
            .set(PLATFORM_CHECKS, DSL.field("?::jsonb", String::class.java, result.platformChecks))
            .set(CHECKED_AT, result.checkedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(newId)!!
    }

    override fun update(result: CopyrightCheckResult): CopyrightCheckResult {
        dsl.update(TABLE)
            .set(VIDEO_TITLE, result.videoTitle)
            .set(STATUS, result.status)
            .set(ISSUES, DSL.field("?::jsonb", String::class.java, result.issues))
            .set(MUSIC_DETECTED, DSL.field("?::jsonb", String::class.java, result.musicDetected))
            .set(MONETIZATION_ELIGIBLE, result.monetizationEligible)
            .set(PLATFORM_CHECKS, DSL.field("?::jsonb", String::class.java, result.platformChecks))
            .set(CHECKED_AT, result.checkedAt)
            .where(ID.eq(result.id))
            .execute()
        return findById(result.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toCopyrightCheckResult(): CopyrightCheckResult {
        val issuesRaw = get("issues")
        val musicRaw = get("music_detected")
        val platformRaw = get("platform_checks")
        return CopyrightCheckResult(
            id = get(ID),
            userId = get(USER_ID),
            videoId = get(VIDEO_ID),
            videoTitle = get(VIDEO_TITLE) ?: "",
            status = get(STATUS) ?: "PENDING",
            issues = when (issuesRaw) {
                is String -> issuesRaw
                else -> issuesRaw?.toString() ?: "[]"
            },
            musicDetected = when (musicRaw) {
                is String -> musicRaw
                else -> musicRaw?.toString() ?: "[]"
            },
            monetizationEligible = get(MONETIZATION_ELIGIBLE) ?: true,
            platformChecks = when (platformRaw) {
                is String -> platformRaw
                else -> platformRaw?.toString() ?: "{}"
            },
            checkedAt = localDateTime(CHECKED_AT),
        )
    }
}
