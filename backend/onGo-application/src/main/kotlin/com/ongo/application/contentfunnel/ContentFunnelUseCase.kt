package com.ongo.application.contentfunnel

import com.ongo.application.contentfunnel.dto.*
import com.ongo.domain.contentfunnel.FunnelComparison
import com.ongo.domain.contentfunnel.FunnelComparisonRepository
import com.ongo.domain.contentfunnel.FunnelStage
import com.ongo.domain.contentfunnel.FunnelStageRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ContentFunnelUseCase(
    private val stageRepository: FunnelStageRepository,
    private val comparisonRepository: FunnelComparisonRepository,
) {

    fun getStages(videoId: Long): List<FunnelStageResponse> {
        return stageRepository.findByVideoId(videoId).map { it.toResponse() }
    }

    fun compare(userId: Long, videoIdA: Long, videoIdB: Long): FunnelComparisonResponse {
        val stagesA = stageRepository.findByVideoId(videoIdA)
        val stagesB = stageRepository.findByVideoId(videoIdB)
        val convA = stagesA.lastOrNull()?.rate ?: BigDecimal.ZERO
        val convB = stagesB.lastOrNull()?.rate ?: BigDecimal.ZERO
        val winner = when {
            convA > convB -> "A"
            convB > convA -> "B"
            else -> null
        }
        val comparison = FunnelComparison(
            userId = userId,
            videoIdA = videoIdA,
            videoTitleA = stagesA.firstOrNull()?.videoTitle ?: "",
            videoIdB = videoIdB,
            videoTitleB = stagesB.firstOrNull()?.videoTitle ?: "",
            winner = winner,
        )
        val saved = comparisonRepository.save(comparison)
        return FunnelComparisonResponse(
            id = saved.id!!,
            videoIdA = videoIdA,
            videoTitleA = saved.videoTitleA,
            videoIdB = videoIdB,
            videoTitleB = saved.videoTitleB,
            stagesA = stagesA.map { it.toResponse() },
            stagesB = stagesB.map { it.toResponse() },
            winner = winner,
            createdAt = saved.createdAt,
        )
    }

    fun getSummary(userId: Long): ContentFunnelSummaryResponse {
        val allStages = stageRepository.findByUserId(userId)
        val videos = allStages.groupBy { it.videoId }
        val conversions = videos.mapValues { (_, stages) -> stages.lastOrNull()?.rate ?: BigDecimal.ZERO }
        val avgConversion = if (conversions.isNotEmpty()) {
            conversions.values.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                .divide(BigDecimal(conversions.size), 4, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val bestVideo = conversions.maxByOrNull { it.value }
        val bestTitle = videos[bestVideo?.key]?.firstOrNull()?.videoTitle ?: ""
        val dropOffs = allStages.groupBy { it.stage }.mapValues { (_, stages) ->
            stages.map { it.dropOff }.fold(BigDecimal.ZERO) { acc, v -> acc + v }
                .divide(BigDecimal(stages.size), 2, RoundingMode.HALF_UP)
        }
        val worstStage = dropOffs.maxByOrNull { it.value }?.key ?: ""
        return ContentFunnelSummaryResponse(
            totalVideosAnalyzed = videos.size,
            avgConversionRate = avgConversion,
            bestConvertingVideo = bestTitle,
            worstDropOffStage = worstStage,
            avgViewToSubscribe = BigDecimal("2.1"),
        )
    }

    private fun FunnelStage.toResponse() = FunnelStageResponse(
        id = id!!, videoId = videoId, videoTitle = videoTitle,
        stage = stage, count = count, rate = rate, dropOff = dropOff,
        measuredAt = measuredAt,
    )
}
