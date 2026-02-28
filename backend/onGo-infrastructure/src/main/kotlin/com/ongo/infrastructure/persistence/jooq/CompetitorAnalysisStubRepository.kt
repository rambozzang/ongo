package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.competitoranalysis.*
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class CompetitorAnalysisJooqRepository(
    private val dsl: DSLContext,
) : CompetitorAnalysisRepository {

    companion object {
        private val PROFILE_TABLE = DSL.table("competitor_profiles")
        private val REPORT_TABLE = DSL.table("competitor_reports")

        // competitor_profiles fields
        private val NAME = DSL.field("name", String::class.java)
        private val AVATAR_URL = DSL.field("avatar_url", String::class.java)
        private val PLATFORMS = DSL.field("platforms", String::class.java)
        private val SUBSCRIBER_COUNT = DSL.field("subscriber_count", Long::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
        private val ADDED_AT = DSL.field("added_at", LocalDateTime::class.java)

        // competitor_reports fields
        private val PERIOD = DSL.field("period", String::class.java)
        private val COMPARISON = DSL.field("comparison", String::class.java)
        private val CONTENT_GAPS = DSL.field("content_gaps", String::class.java)
        private val TRENDING_TOPICS = DSL.field("trending_topics", String::class.java)
        private val GENERATED_AT = DSL.field("generated_at", LocalDateTime::class.java)
    }

    // --- CompetitorProfile ---

    override fun findProfileById(id: Long): CompetitorProfile? =
        dsl.select().from(PROFILE_TABLE).where(ID.eq(id)).fetchOne()?.toProfile()

    override fun findProfilesByUserId(userId: Long): List<CompetitorProfile> =
        dsl.select().from(PROFILE_TABLE).where(USER_ID.eq(userId))
            .orderBy(ADDED_AT.desc())
            .fetch().map { it.toProfile() }

    override fun saveProfile(profile: CompetitorProfile): CompetitorProfile {
        val id = dsl.insertInto(PROFILE_TABLE)
            .set(USER_ID, profile.userId)
            .set(NAME, profile.name)
            .set(AVATAR_URL, profile.avatarUrl)
            .set(PLATFORMS, DSL.field("?::jsonb", String::class.java, profile.platforms))
            .set(SUBSCRIBER_COUNT, profile.subscriberCount)
            .set(AVG_VIEWS, profile.avgViews)
            .set(AVG_ENGAGEMENT, profile.avgEngagement)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findProfileById(id)!!
    }

    override fun updateProfile(profile: CompetitorProfile): CompetitorProfile {
        dsl.update(PROFILE_TABLE)
            .set(NAME, profile.name)
            .set(AVATAR_URL, profile.avatarUrl)
            .set(PLATFORMS, DSL.field("?::jsonb", String::class.java, profile.platforms))
            .set(SUBSCRIBER_COUNT, profile.subscriberCount)
            .set(AVG_VIEWS, profile.avgViews)
            .set(AVG_ENGAGEMENT, profile.avgEngagement)
            .where(ID.eq(profile.id))
            .execute()

        return findProfileById(profile.id!!)!!
    }

    override fun deleteProfile(id: Long) {
        dsl.deleteFrom(PROFILE_TABLE).where(ID.eq(id)).execute()
    }

    // --- CompetitorReport ---

    override fun findReportById(id: Long): CompetitorReport? =
        dsl.select().from(REPORT_TABLE).where(ID.eq(id)).fetchOne()?.toReport()

    override fun findReportsByUserId(userId: Long): List<CompetitorReport> =
        dsl.select().from(REPORT_TABLE).where(USER_ID.eq(userId))
            .orderBy(GENERATED_AT.desc())
            .fetch().map { it.toReport() }

    override fun saveReport(report: CompetitorReport): CompetitorReport {
        val id = dsl.insertInto(REPORT_TABLE)
            .set(USER_ID, report.userId)
            .set(PERIOD, report.period)
            .set(COMPARISON, DSL.field("?::jsonb", String::class.java, report.comparison))
            .set(CONTENT_GAPS, DSL.field("?::jsonb", String::class.java, report.contentGaps))
            .set(TRENDING_TOPICS, DSL.field("?::jsonb", String::class.java, report.trendingTopics))
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findReportById(id)!!
    }

    override fun deleteReport(id: Long) {
        dsl.deleteFrom(REPORT_TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toProfile(): CompetitorProfile {
        val platformsRaw = get("platforms")
        return CompetitorProfile(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            avatarUrl = get(AVATAR_URL),
            platforms = when (platformsRaw) {
                is String -> platformsRaw
                else -> platformsRaw?.toString() ?: "[]"
            },
            subscriberCount = get(SUBSCRIBER_COUNT) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
            addedAt = localDateTime(ADDED_AT),
        )
    }

    private fun Record.toReport(): CompetitorReport {
        val comparisonRaw = get("comparison")
        val contentGapsRaw = get("content_gaps")
        val trendingTopicsRaw = get("trending_topics")
        return CompetitorReport(
            id = get(ID),
            userId = get(USER_ID),
            period = get(PERIOD),
            comparison = when (comparisonRaw) {
                is String -> comparisonRaw
                else -> comparisonRaw?.toString() ?: "{}"
            },
            contentGaps = when (contentGapsRaw) {
                is String -> contentGapsRaw
                else -> contentGapsRaw?.toString() ?: "[]"
            },
            trendingTopics = when (trendingTopicsRaw) {
                is String -> trendingTopicsRaw
                else -> trendingTopicsRaw?.toString() ?: "[]"
            },
            generatedAt = localDateTime(GENERATED_AT),
        )
    }
}
