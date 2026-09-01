package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.domain.ugc.analytics.MetricSnapshotSource
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_POST_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTURED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENTS_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LIKES_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.SHARES_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS_LONG
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_POST_METRIC_SNAPSHOTS
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class JooqMetricSnapshotRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
) : MetricSnapshotRepository {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    private companion object {
        val SOURCE: Field<String> = DSL.field(DSL.name("source"), String::class.java)
        val UNAVAILABLE_METRICS: Field<JSONB> = DSL.field(DSL.name("unavailable_metrics"), JSONB::class.java)
    }

    override fun save(snapshot: MetricSnapshot): MetricSnapshot {
        val id = dsl.insertInto(UGC_POST_METRIC_SNAPSHOTS)
            .set(CAMPAIGN_POST_ID, snapshot.campaignPostId)
            .set(CAPTURED_AT, snapshot.capturedAt)
            .set(VIEWS_LONG, snapshot.views)
            .set(LIKES_LONG, snapshot.likes)
            .set(COMMENTS_LONG, snapshot.comments)
            .set(SHARES_LONG, snapshot.shares)
            // 출처를 함께 남긴다. 이것이 없으면 0 이 측정값인지 미수집인지 알 수 없다.
            .set(SOURCE, snapshot.source.takeIf { it != MetricSnapshotSource.UNKNOWN }?.name)
            .set(UNAVAILABLE_METRICS, JSONB.jsonb(objectMapper.writeValueAsString(snapshot.unavailableMetrics)))
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return dsl.select().from(UGC_POST_METRIC_SNAPSHOTS).where(ID.eq(id)).fetchOne()!!.toSnapshot()
    }

    override fun findLatestByCampaignPostId(campaignPostId: Long): MetricSnapshot? =
        dsl.select().from(UGC_POST_METRIC_SNAPSHOTS)
            .where(CAMPAIGN_POST_ID.eq(campaignPostId))
            .orderBy(CAPTURED_AT.desc())
            .limit(1)
            .fetchOne()?.toSnapshot()

    private fun Record.toSnapshot(): MetricSnapshot = MetricSnapshot(
        id = get(ID),
        campaignPostId = get(CAMPAIGN_POST_ID),
        capturedAt = localDateTime(CAPTURED_AT)!!,
        views = get(VIEWS_LONG),
        likes = get(LIKES_LONG),
        comments = get(COMMENTS_LONG),
        shares = get(SHARES_LONG),
        source = readSource(get(SOURCE)),
        unavailableMetrics = readUnavailableMetrics(get(UNAVAILABLE_METRICS)),
    )

    /**
     * `NULL` 또는 알 수 없는 값은 [MetricSnapshotSource.UNKNOWN] 이다.
     *
     * 임의로 MANUAL 로 승격하면 미수집 0 이 "사람이 적은 0" 으로 둔갑한다.
     */
    private fun readSource(raw: String?): MetricSnapshotSource {
        if (raw.isNullOrBlank()) return MetricSnapshotSource.UNKNOWN
        return runCatching { MetricSnapshotSource.valueOf(raw) }
            .onFailure { log.error("알 수 없는 지표 스냅샷 출처다. UNKNOWN 으로 읽는다: {}", raw) }
            .getOrDefault(MetricSnapshotSource.UNKNOWN)
    }

    /** 읽지 못하면 빈 집합. 판정은 [MetricSnapshot.measured] 가 출처와 함께 내린다. */
    private fun readUnavailableMetrics(raw: Any?): Set<String> {
        val text = when (raw) {
            null -> return emptySet()
            is JSONB -> raw.data()
            else -> raw.toString()
        }
        if (text.isBlank() || text == "null") return emptySet()
        return runCatching {
            objectMapper.readValue(text, Array<String>::class.java).toSet()
        }.onFailure { log.error("지표 스냅샷의 미측정 목록을 읽지 못했다: {}", text, it) }
            .getOrDefault(emptySet())
    }
}
