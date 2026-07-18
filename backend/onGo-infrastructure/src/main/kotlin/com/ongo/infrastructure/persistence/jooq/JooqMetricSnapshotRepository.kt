package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.analytics.MetricSnapshot
import com.ongo.domain.ugc.analytics.MetricSnapshotRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_POST_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CAPTURED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENTS_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LIKES_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.SHARES_LONG
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS_LONG
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_POST_METRIC_SNAPSHOTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqMetricSnapshotRepository(
    private val dsl: DSLContext,
) : MetricSnapshotRepository {

    override fun save(snapshot: MetricSnapshot): MetricSnapshot {
        val id = dsl.insertInto(UGC_POST_METRIC_SNAPSHOTS)
            .set(CAMPAIGN_POST_ID, snapshot.campaignPostId)
            .set(CAPTURED_AT, snapshot.capturedAt)
            .set(VIEWS_LONG, snapshot.views)
            .set(LIKES_LONG, snapshot.likes)
            .set(COMMENTS_LONG, snapshot.comments)
            .set(SHARES_LONG, snapshot.shares)
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
    )
}
