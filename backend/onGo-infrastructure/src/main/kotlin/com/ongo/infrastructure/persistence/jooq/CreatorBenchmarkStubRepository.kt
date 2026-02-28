package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatorbenchmark.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class BenchmarkPeerJooqRepository(
    private val dsl: DSLContext,
) : BenchmarkPeerRepository {

    companion object {
        private val TABLE = DSL.table("benchmark_peers")
        private val NAME = DSL.field("name", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val SUBSCRIBERS = DSL.field("subscribers", Long::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val ENGAGEMENT_RATE = DSL.field("engagement_rate", BigDecimal::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<BenchmarkPeer> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toBenchmarkPeer() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkPeer> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toBenchmarkPeer() }

    private fun Record.toBenchmarkPeer(): BenchmarkPeer =
        BenchmarkPeer(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            platform = get(PLATFORM),
            subscribers = get(SUBSCRIBERS) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            engagementRate = get(ENGAGEMENT_RATE) ?: BigDecimal.ZERO,
            category = get(CATEGORY),
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}

@Repository
class BenchmarkResultJooqRepository(
    private val dsl: DSLContext,
) : BenchmarkResultRepository {

    companion object {
        private val TABLE = DSL.table("benchmark_results")
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val MY_VALUE = DSL.field("my_value", BigDecimal::class.java)
        private val AVG_VALUE = DSL.field("avg_value", BigDecimal::class.java)
        private val TOP_VALUE = DSL.field("top_value", BigDecimal::class.java)
        private val PERCENTILE = DSL.field("percentile", Int::class.java)
        private val METRIC = DSL.field("metric", String::class.java)
        private val TREND = DSL.field("trend", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<BenchmarkResult> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toBenchmarkResult() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkResult> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toBenchmarkResult() }

    private fun Record.toBenchmarkResult(): BenchmarkResult =
        BenchmarkResult(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            platform = get(PLATFORM),
            category = get(CATEGORY),
            myValue = get(MY_VALUE) ?: BigDecimal.ZERO,
            avgValue = get(AVG_VALUE) ?: BigDecimal.ZERO,
            topValue = get(TOP_VALUE) ?: BigDecimal.ZERO,
            percentile = get(PERCENTILE) ?: 0,
            metric = get(METRIC),
            trend = get(TREND) ?: "STABLE",
            updatedAt = localDateTime(UPDATED_AT) ?: LocalDateTime.now(),
        )
}
