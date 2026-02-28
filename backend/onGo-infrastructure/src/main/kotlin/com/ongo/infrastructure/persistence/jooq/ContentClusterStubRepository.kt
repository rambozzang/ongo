package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcluster.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class ContentClusterJooqRepository(
    private val dsl: DSLContext,
) : ContentClusterRepository {

    companion object {
        private val TABLE = DSL.table("content_clusters")
        private val NAME = DSL.field("name", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val CONTENT_COUNT = DSL.field("content_count", Int::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
        private val TOP_CONTENT = DSL.field("top_content", String::class.java)
        private val TAGS = DSL.field("tags", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<ContentCluster> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toCluster() }

    override fun findById(id: Long): ContentCluster? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toCluster()

    private fun Record.toCluster(): ContentCluster {
        val tagsRaw = get("tags")
        val tagsStr = when (tagsRaw) {
            is String -> tagsRaw
            else -> tagsRaw?.toString() ?: "[]"
        }
        val tagsList = tagsStr.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        return ContentCluster(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            name = get(NAME),
            description = get(DESCRIPTION),
            contentCount = get(CONTENT_COUNT) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
            topContent = get(TOP_CONTENT),
            tags = tagsList,
            platform = get(PLATFORM),
            createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
        )
    }
}

@Repository
class ClusterContentJooqRepository(
    private val dsl: DSLContext,
) : ClusterContentRepository {

    companion object {
        private val TABLE = DSL.table("cluster_contents")
        private val CLUSTER_ID = DSL.field("cluster_id", Long::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val VIEWS = DSL.field("views", Long::class.java)
        private val LIKES = DSL.field("likes", Int::class.java)
        private val ENGAGEMENT = DSL.field("engagement", BigDecimal::class.java)
        private val PUBLISHED_AT = DSL.field("published_at", java.time.LocalDateTime::class.java)
        private val SIMILARITY = DSL.field("similarity", BigDecimal::class.java)
    }

    override fun findByClusterId(clusterId: Long): List<ClusterContent> =
        dsl.select().from(TABLE)
            .where(CLUSTER_ID.eq(clusterId))
            .fetch { it.toContent() }

    private fun Record.toContent(): ClusterContent = ClusterContent(
        id = get(ID),
        clusterId = get(CLUSTER_ID),
        title = get(TITLE),
        platform = get(PLATFORM),
        views = get(VIEWS) ?: 0,
        likes = get(LIKES) ?: 0,
        engagement = get(ENGAGEMENT) ?: BigDecimal.ZERO,
        publishedAt = localDateTime(PUBLISHED_AT),
        similarity = get(SIMILARITY) ?: BigDecimal.ZERO,
        createdAt = localDateTime(CREATED_AT) ?: java.time.LocalDateTime.now(),
    )
}
