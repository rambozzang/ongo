package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoseo.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SeoAnalysisJooqRepository(
    private val dsl: DSLContext,
) : SeoAnalysisRepository {

    companion object {
        private val TABLE = DSL.table("seo_analyses")
        private val VIDEO_TITLE = DSL.field("video_title", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val OVERALL_SCORE = DSL.field("overall_score", Int::class.java)
        private val TITLE_SCORE = DSL.field("title_score", Int::class.java)
        private val DESCRIPTION_SCORE = DSL.field("description_score", Int::class.java)
        private val TAG_SCORE = DSL.field("tag_score", Int::class.java)
        private val THUMBNAIL_SCORE = DSL.field("thumbnail_score", Int::class.java)
        private val SUGGESTIONS = DSL.field("suggestions", String::class.java)
        private val ANALYZED_AT = DSL.field("analyzed_at", LocalDateTime::class.java)
    }

    override fun findByWorkspaceId(workspaceId: Long): List<SeoAnalysis> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .fetch { it.toAnalysis() }

    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<SeoAnalysis> =
        dsl.select().from(TABLE)
            .where(WORKSPACE_ID.eq(workspaceId))
            .and(PLATFORM.eq(platform))
            .fetch { it.toAnalysis() }

    override fun save(analysis: SeoAnalysis): SeoAnalysis {
        val suggestionsJson = "[${analysis.suggestions.joinToString(",") { "\"$it\"" }}]"
        val id = dsl.insertInto(TABLE)
            .set(WORKSPACE_ID, analysis.workspaceId)
            .set(VIDEO_TITLE, analysis.videoTitle)
            .set(PLATFORM, analysis.platform)
            .set(OVERALL_SCORE, analysis.overallScore)
            .set(TITLE_SCORE, analysis.titleScore)
            .set(DESCRIPTION_SCORE, analysis.descriptionScore)
            .set(TAG_SCORE, analysis.tagScore)
            .set(THUMBNAIL_SCORE, analysis.thumbnailScore)
            .set(SUGGESTIONS, DSL.field("?::jsonb", String::class.java, suggestionsJson))
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toAnalysis()
    }

    private fun Record.toAnalysis(): SeoAnalysis {
        val suggestionsRaw = get("suggestions")
        val suggestionsList: List<String> = when (suggestionsRaw) {
            is String -> parseJsonArray(suggestionsRaw)
            else -> suggestionsRaw?.toString()?.let { parseJsonArray(it) } ?: emptyList()
        }
        return SeoAnalysis(
            id = get(ID),
            workspaceId = get(WORKSPACE_ID),
            videoTitle = get(VIDEO_TITLE),
            platform = get(PLATFORM),
            overallScore = get(OVERALL_SCORE) ?: 0,
            titleScore = get(TITLE_SCORE) ?: 0,
            descriptionScore = get(DESCRIPTION_SCORE) ?: 0,
            tagScore = get(TAG_SCORE) ?: 0,
            thumbnailScore = get(THUMBNAIL_SCORE) ?: 0,
            suggestions = suggestionsList,
            analyzedAt = localDateTime(ANALYZED_AT) ?: LocalDateTime.now(),
        )
    }

    private fun parseJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (trimmed == "[]" || trimmed.isBlank()) return emptyList()
        return trimmed.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }
}

@Repository
class SeoKeywordJooqRepository(
    private val dsl: DSLContext,
) : SeoKeywordRepository {

    companion object {
        private val TABLE = DSL.table("seo_keywords")
        private val ANALYSIS_ID = DSL.field("analysis_id", Long::class.java)
        private val KEYWORD = DSL.field("keyword", String::class.java)
        private val SEARCH_VOLUME = DSL.field("search_volume", Long::class.java)
        private val COMPETITION = DSL.field("competition", String::class.java)
        private val RELEVANCE = DSL.field("relevance", Int::class.java)
        private val TREND = DSL.field("trend", String::class.java)
    }

    override fun findByAnalysisId(analysisId: Long): List<SeoKeyword> =
        dsl.select().from(TABLE)
            .where(ANALYSIS_ID.eq(analysisId))
            .fetch { it.toKeyword() }

    private fun Record.toKeyword(): SeoKeyword =
        SeoKeyword(
            id = get(ID),
            analysisId = get(ANALYSIS_ID),
            keyword = get(KEYWORD),
            searchVolume = get(SEARCH_VOLUME) ?: 0,
            competition = get(COMPETITION) ?: "MEDIUM",
            relevance = get(RELEVANCE) ?: 0,
            trend = get(TREND) ?: "STABLE",
            createdAt = localDateTime(CREATED_AT) ?: LocalDateTime.now(),
        )
}
