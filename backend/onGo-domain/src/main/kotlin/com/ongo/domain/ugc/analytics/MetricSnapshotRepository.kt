package com.ongo.domain.ugc.analytics

interface MetricSnapshotRepository {
    fun save(snapshot: MetricSnapshot): MetricSnapshot

    fun findLatestByCampaignPostId(campaignPostId: Long): MetricSnapshot?
}
