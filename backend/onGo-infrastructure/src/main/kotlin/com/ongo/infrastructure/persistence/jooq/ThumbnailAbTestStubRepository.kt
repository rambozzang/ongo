package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.thumbnailabtest.ThumbnailAbTest
import com.ongo.domain.thumbnailabtest.ThumbnailAbTestRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class ThumbnailAbTestJooqRepository(
    private val dsl: DSLContext,
) : ThumbnailAbTestRepository {

    companion object {
        private val TABLE = DSL.table("thumbnail_ab_tests")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val VARIANT_A_IMAGE_URL = DSL.field("variant_a_image_url", String::class.java)
        private val VARIANT_A_CTR = DSL.field("variant_a_ctr", BigDecimal::class.java)
        private val VARIANT_A_IMPRESSIONS = DSL.field("variant_a_impressions", Long::class.java)
        private val VARIANT_A_CLICKS = DSL.field("variant_a_clicks", Long::class.java)
        private val VARIANT_B_IMAGE_URL = DSL.field("variant_b_image_url", String::class.java)
        private val VARIANT_B_CTR = DSL.field("variant_b_ctr", BigDecimal::class.java)
        private val VARIANT_B_IMPRESSIONS = DSL.field("variant_b_impressions", Long::class.java)
        private val VARIANT_B_CLICKS = DSL.field("variant_b_clicks", Long::class.java)
        private val WINNER = DSL.field("winner", String::class.java)
        private val STARTED_AT = DSL.field("started_at", LocalDateTime::class.java)
        private val ENDED_AT = DSL.field("ended_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<ThumbnailAbTest> =
        dsl.select()
            .from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(STARTED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<ThumbnailAbTest> =
        dsl.select()
            .from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(STATUS.eq(status))
            .orderBy(STARTED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findById(id: Long): ThumbnailAbTest? =
        dsl.select()
            .from(TABLE)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun save(test: ThumbnailAbTest): ThumbnailAbTest {
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, test.workspaceId)
            .set(VIDEO_TITLE, test.videoTitle)
            .set(PLATFORM, test.platform)
            .set(STATUS, test.status)
            .set(VARIANT_A_IMAGE_URL, test.variantAImageUrl)
            .set(VARIANT_A_CTR, test.variantACtr)
            .set(VARIANT_A_IMPRESSIONS, test.variantAImpressions)
            .set(VARIANT_A_CLICKS, test.variantAClicks)
            .set(VARIANT_B_IMAGE_URL, test.variantBImageUrl)
            .set(VARIANT_B_CTR, test.variantBCtr)
            .set(VARIANT_B_IMPRESSIONS, test.variantBImpressions)
            .set(VARIANT_B_CLICKS, test.variantBClicks)
            .set(WINNER, test.winner)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, winner: String?) {
        dsl.update(TABLE)
            .set(STATUS, status)
            .set(WINNER, winner)
            .set(ENDED_AT, if (status == "COMPLETED") LocalDateTime.now() else null)
            .where(ID.eq(id))
            .execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): ThumbnailAbTest {
        val startedAtRaw = get("started_at")
        val endedAtRaw = get("ended_at")
        return ThumbnailAbTest(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            videoTitle = get(VIDEO_TITLE) ?: "",
            platform = get(PLATFORM) ?: "",
            status = get(STATUS) ?: "PENDING",
            variantAImageUrl = get(VARIANT_A_IMAGE_URL),
            variantACtr = get(VARIANT_A_CTR) ?: BigDecimal.ZERO,
            variantAImpressions = toLong(get("variant_a_impressions")),
            variantAClicks = toLong(get("variant_a_clicks")),
            variantBImageUrl = get(VARIANT_B_IMAGE_URL),
            variantBCtr = get(VARIANT_B_CTR) ?: BigDecimal.ZERO,
            variantBImpressions = toLong(get("variant_b_impressions")),
            variantBClicks = toLong(get("variant_b_clicks")),
            winner = get(WINNER),
            startedAt = when (startedAtRaw) {
                is LocalDateTime -> startedAtRaw
                is java.sql.Timestamp -> startedAtRaw.toLocalDateTime()
                else -> LocalDateTime.now()
            },
            endedAt = when (endedAtRaw) {
                is LocalDateTime -> endedAtRaw
                is java.sql.Timestamp -> endedAtRaw.toLocalDateTime()
                else -> null
            },
        )
    }

    private fun toLong(value: Any?): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        else -> 0L
    }
}
