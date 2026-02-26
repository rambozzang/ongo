package com.ongo.application.videoseo

import com.ongo.application.videoseo.dto.*
import com.ongo.domain.videoseo.*
import org.springframework.stereotype.Service

@Service
class VideoSeoUseCase(
    private val analysisRepository: SeoAnalysisRepository,
    private val keywordRepository: SeoKeywordRepository,
) {

    fun getAnalyses(workspaceId: Long, platform: String?): List<SeoAnalysisResponse> {
        val analyses = if (platform != null) {
            analysisRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        } else {
            analysisRepository.findByWorkspaceId(workspaceId)
        }
        return analyses.map { toAnalysisResponse(it) }
    }

    fun analyze(workspaceId: Long, request: SeoOptimizeRequest): SeoAnalysisResponse {
        val analysis = SeoAnalysis(
            workspaceId = workspaceId,
            videoTitle = "Video #${request.videoId}",
            platform = request.platform,
        )
        return toAnalysisResponse(analysisRepository.save(analysis))
    }

    fun getKeywords(analysisId: Long): List<SeoKeywordResponse> {
        return keywordRepository.findByAnalysisId(analysisId).map { toKeywordResponse(it) }
    }

    fun getSummary(workspaceId: Long): VideoSeoSummaryResponse {
        val analyses = analysisRepository.findByWorkspaceId(workspaceId)
        val avgScore = if (analyses.isNotEmpty()) analyses.map { it.overallScore }.average() else 0.0
        val allSuggestions = analyses.flatMap { it.suggestions }
        val weakest = listOf("title" to analyses.map { it.titleScore }.average(),
            "description" to analyses.map { it.descriptionScore }.average(),
            "tag" to analyses.map { it.tagScore }.average(),
            "thumbnail" to analyses.map { it.thumbnailScore }.average())
            .minByOrNull { it.second }?.first ?: "-"
        return VideoSeoSummaryResponse(analyses.size, avgScore, "-", 0.0, weakest)
    }

    private fun toAnalysisResponse(a: SeoAnalysis) = SeoAnalysisResponse(
        id = a.id, videoTitle = a.videoTitle, platform = a.platform,
        overallScore = a.overallScore, titleScore = a.titleScore,
        descriptionScore = a.descriptionScore, tagScore = a.tagScore,
        thumbnailScore = a.thumbnailScore, suggestions = a.suggestions,
        analyzedAt = a.analyzedAt.toString(),
    )

    private fun toKeywordResponse(k: SeoKeyword) = SeoKeywordResponse(
        id = k.id, keyword = k.keyword, searchVolume = k.searchVolume,
        competition = k.competition, relevance = k.relevance, trend = k.trend,
    )
}
