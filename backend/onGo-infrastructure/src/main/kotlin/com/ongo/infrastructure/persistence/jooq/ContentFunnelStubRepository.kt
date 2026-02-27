package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentfunnel.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.FUNNEL_COMPARISONS
import com.ongo.infrastructure.persistence.jooq.Tables.FUNNEL_STAGES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class FunnelStageJooqRepository(
    private val dsl: DSLContext,
) : FunnelStageRepository {

    companion object {
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val STAGE = DSL.field("stage", String::class.java)
        private val COUNT = DSL.field("count", Long::class.java)
        private val RATE = DSL.field("rate", BigDecimal::class.java)
        private val DROP_OFF = DSL.field("drop_off", BigDecimal::class.java)
        private val MEASURED_AT = DSL.field("measured_at", java.time.LocalDateTime::class.java)
    }

    override fun findByVideoId(videoId: Long): List<FunnelStage> =
        dsl.select()
            .from(FUNNEL_STAGES)
            .where(VIDEO_ID.eq(videoId))
            .orderBy(DSL.field("sort_order", Int::class.java).asc())
            .fetch()
            .map { it.toEntity() }

    override fun findByUserId(userId: Long): List<FunnelStage> =
        dsl.select()
            .from(FUNNEL_STAGES)
            .where(USER_ID.eq(userId))
            .orderBy(VIDEO_ID, DSL.field("sort_order", Int::class.java).asc())
            .fetch()
            .map { it.toEntity() }

    override fun save(stage: FunnelStage): FunnelStage {
        val id = dsl.insertInto(FUNNEL_STAGES)
            .set(USER_ID, stage.userId)
            .set(VIDEO_ID, stage.videoId)
            .set(VIDEO_TITLE, stage.videoTitle)
            .set(STAGE, stage.stage)
            .set(COUNT, stage.count)
            .set(RATE, stage.rate)
            .set(DROP_OFF, stage.dropOff)
            .set(MEASURED_AT, stage.measuredAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(FUNNEL_STAGES)
            .where(ID.eq(id))
            .fetchOne()!!
            .toEntity()
    }

    private fun Record.toEntity(): FunnelStage = FunnelStage(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        videoTitle = get(VIDEO_TITLE) ?: "",
        stage = get(STAGE) ?: "",
        count = get(COUNT) ?: 0,
        rate = get(RATE) ?: BigDecimal.ZERO,
        dropOff = get(DROP_OFF) ?: BigDecimal.ZERO,
        measuredAt = localDateTime(MEASURED_AT),
    )
}

@Repository
class FunnelComparisonJooqRepository(
    private val dsl: DSLContext,
) : FunnelComparisonRepository {

    companion object {
        private val VIDEO_ID_A = DSL.field("video_id_a", Long::class.java)
        private val VIDEO_TITLE_A = DSL.field("video_title_a", String::class.java)
        private val VIDEO_ID_B = DSL.field("video_id_b", Long::class.java)
        private val VIDEO_TITLE_B = DSL.field("video_title_b", String::class.java)
        private val WINNER = DSL.field("winner", String::class.java)
    }

    override fun findById(id: Long): FunnelComparison? =
        dsl.select()
            .from(FUNNEL_COMPARISONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<FunnelComparison> =
        dsl.select()
            .from(FUNNEL_COMPARISONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(comparison: FunnelComparison): FunnelComparison {
        val id = dsl.insertInto(FUNNEL_COMPARISONS)
            .set(USER_ID, comparison.userId)
            .set(VIDEO_ID_A, comparison.videoIdA)
            .set(VIDEO_TITLE_A, comparison.videoTitleA)
            .set(VIDEO_ID_B, comparison.videoIdB)
            .set(VIDEO_TITLE_B, comparison.videoTitleB)
            .set(WINNER, comparison.winner)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    private fun Record.toEntity(): FunnelComparison = FunnelComparison(
        id = get(ID),
        userId = get(USER_ID),
        videoIdA = get(VIDEO_ID_A),
        videoTitleA = get(VIDEO_TITLE_A) ?: "",
        videoIdB = get(VIDEO_ID_B),
        videoTitleB = get(VIDEO_TITLE_B) ?: "",
        winner = get(WINNER),
        createdAt = localDateTime(CREATED_AT),
    )
}
