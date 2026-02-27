package com.ongo.application.prediction

import com.ongo.application.prediction.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.prediction.PerformancePrediction
import com.ongo.domain.prediction.PredictionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PredictionUseCase(
    private val predictionRepository: PredictionRepository,
) {

    fun listPredictions(userId: Long): List<PredictionResponse> {
        return predictionRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getPrediction(userId: Long, predictionId: Long): PredictionResponse {
        val prediction = predictionRepository.findById(predictionId) ?: throw NotFoundException("예측", predictionId)
        if (prediction.userId != userId) throw ForbiddenException("해당 예측에 대한 권한이 없습니다")
        return prediction.toResponse()
    }

    @Transactional
    fun createPrediction(userId: Long, request: CreatePredictionRequest): PredictionResponse {
        val prediction = PerformancePrediction(
            userId = userId,
            videoId = request.videoId,
            predictionData = request.predictionData,
        )
        return predictionRepository.save(prediction).toResponse()
    }

    @Transactional
    fun updateActuals(userId: Long, predictionId: Long, request: UpdateActualsRequest): PredictionResponse {
        val prediction = predictionRepository.findById(predictionId) ?: throw NotFoundException("예측", predictionId)
        if (prediction.userId != userId) throw ForbiddenException("해당 예측에 대한 권한이 없습니다")
        val updated = prediction.copy(
            actualViews = request.actualViews ?: prediction.actualViews,
            actualLikes = request.actualLikes ?: prediction.actualLikes,
        )
        return predictionRepository.update(updated).toResponse()
    }

    @Transactional
    fun deletePrediction(userId: Long, predictionId: Long) {
        val prediction = predictionRepository.findById(predictionId) ?: throw NotFoundException("예측", predictionId)
        if (prediction.userId != userId) throw ForbiddenException("해당 예측에 대한 권한이 없습니다")
        predictionRepository.delete(predictionId)
    }

    /**
     * 시간대별 예측 히트맵 - predictions 데이터를 기반으로 시간대별 성과 집계
     */
    fun getHeatmap(userId: Long, platform: String?): PredictionHeatmapResponse {
        val predictions = predictionRepository.findByUserId(userId)
        val dayLabels = arrayOf("일", "월", "화", "수", "목", "금", "토")

        val cells = mutableListOf<HeatmapCell>()
        val grouped = predictions
            .filter { it.optimalUploadTime != null }
            .groupBy {
                val dt = it.optimalUploadTime!!
                val dow = dt.dayOfWeek.value % 7 // 0=일요일
                Pair(dow, dt.hour)
            }

        for ((key, items) in grouped) {
            val avgScore = items.mapNotNull { it.confidenceScore?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            cells.add(
                HeatmapCell(
                    dayOfWeek = key.first,
                    dayLabel = dayLabels[key.first],
                    hour = key.second,
                    value = Math.round(avgScore * 100) / 100.0,
                )
            )
        }

        return PredictionHeatmapResponse(data = cells.sortedWith(compareBy({ it.dayOfWeek }, { it.hour })))
    }

    /**
     * 최적 업로드 시간 - optimalUploadTime 필드 기반 상위 시간대
     */
    fun getOptimalTimes(userId: Long, platform: String?): PredictionOptimalTimesResponse {
        val predictions = predictionRepository.findByUserId(userId)
        val dayLabels = arrayOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

        val withOptimalTime = predictions.filter { it.optimalUploadTime != null }
        if (withOptimalTime.isEmpty()) return PredictionOptimalTimesResponse(slots = emptyList())

        val grouped = withOptimalTime.groupBy {
            val dt = it.optimalUploadTime!!
            Pair(dt.dayOfWeek.value % 7, dt.hour)
        }

        val slots = grouped.map { (key, items) ->
            val avgConfidence = items.mapNotNull { it.confidenceScore?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            val avgViews = items.mapNotNull { it.predictedViews }.average().takeIf { !it.isNaN() } ?: 0.0

            PredictionOptimalTimeSlot(
                dayOfWeek = key.first,
                dayLabel = dayLabels[key.first],
                hour = key.second,
                timeLabel = "${key.second.toString().padStart(2, '0')}:00",
                score = Math.round((avgConfidence * 0.6 + avgViews * 0.0001 * 0.4) * 100) / 100.0,
                reason = "${items.size}건의 예측 데이터 기반, 평균 신뢰도 ${Math.round(avgConfidence * 100) / 100.0}%",
            )
        }
            .sortedByDescending { it.score }
            .take(5)

        return PredictionOptimalTimesResponse(slots = slots)
    }

    /**
     * 제목 제안 - 기존 고성과 동영상 제목 패턴 분석
     */
    fun getTitleSuggestions(userId: Long, title: String, platform: String?): TitleSuggestionsResponse {
        val predictions = predictionRepository.findByUserId(userId)
        val highPerformance = predictions
            .filter { (it.predictedViews ?: 0) > 0 }
            .sortedByDescending { it.predictedViews ?: 0 }
            .take(10)

        if (highPerformance.isEmpty()) {
            return TitleSuggestionsResponse(
                suggestions = listOf(
                    TitleSuggestion(title = title, score = 50.0, reason = "분석할 예측 데이터가 부족합니다"),
                )
            )
        }

        @Suppress("UNUSED_VARIABLE")
        val avgViews = highPerformance.mapNotNull { it.predictedViews }.average()
        val suggestions = mutableListOf<TitleSuggestion>()

        // 현재 제목 기반 변형 제안
        suggestions.add(
            TitleSuggestion(
                title = "[$title] 완벽 가이드",
                score = 75.0,
                reason = "가이드 형식의 제목은 높은 클릭률을 보이는 경향이 있습니다",
            )
        )
        suggestions.add(
            TitleSuggestion(
                title = "$title | 꼭 알아야 할 핵심 정리",
                score = 70.0,
                reason = "핵심 정리 키워드가 포함된 제목은 시청자 관심을 유도합니다",
            )
        )
        suggestions.add(
            TitleSuggestion(
                title = "$title - 초보자도 쉽게 따라하기",
                score = 65.0,
                reason = "초보자 대상 제목은 넓은 시청자층을 확보할 수 있습니다",
            )
        )

        return TitleSuggestionsResponse(suggestions = suggestions)
    }

    /**
     * 태그 제안 - 기존 동영상 태그 빈도 분석 (predictions의 predictionData 기반)
     */
    fun getTagSuggestions(userId: Long, tags: String, platform: String?): TagSuggestionsResponse {
        val predictions = predictionRepository.findByUserId(userId)

        // 입력된 태그 파싱
        val inputTags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        // predictions에서 고성과 데이터의 패턴 분석
        val highPerformance = predictions
            .filter { (it.predictedViews ?: 0) > 0 }
            .sortedByDescending { it.predictedViews ?: 0 }

        val suggestions = inputTags.map { tag ->
            val relatedPredictions = highPerformance.take(10)
            val avgViews = relatedPredictions.mapNotNull { it.predictedViews }.average().toLong().coerceAtLeast(0)

            TagSuggestion(
                tag = tag,
                frequency = relatedPredictions.size,
                avgViews = avgViews,
            )
        }.sortedByDescending { it.avgViews }

        return TagSuggestionsResponse(suggestions = suggestions)
    }

    /**
     * 경쟁자 비교 - 예측값 vs 실제값 비교
     */
    fun getCompetitorComparison(userId: Long, videoId: Long): CompetitorComparisonResponse {
        val predictions = predictionRepository.findByVideoId(videoId)
            .filter { it.userId == userId }

        if (predictions.isEmpty()) throw NotFoundException("예측 데이터가 없습니다. videoId", videoId)

        val comparisons = predictions.flatMap { p ->
            val items = mutableListOf<CompetitorComparisonItem>()

            val pv = p.predictedViews
            val av = p.actualViews
            if (pv != null) {
                val accuracy = if (av != null && pv > 0) {
                    val diff = Math.abs(av - pv).toDouble()
                    Math.round((1.0 - diff / pv) * 10000) / 100.0
                } else null
                items.add(CompetitorComparisonItem("조회수", pv, av, accuracy))
            }

            val pl = p.predictedLikes
            val al = p.actualLikes
            if (pl != null) {
                val accuracy = if (al != null && pl > 0) {
                    val diff = Math.abs(al - pl).toDouble()
                    Math.round((1.0 - diff / pl) * 10000) / 100.0
                } else null
                items.add(CompetitorComparisonItem("좋아요", pl, al, accuracy))
            }

            items
        }

        return CompetitorComparisonResponse(videoId = videoId, comparisons = comparisons)
    }

    /**
     * 예측 히스토리 - 최근순 목록
     */
    fun getHistory(userId: Long, limit: Int): PredictionHistoryResponse {
        val items = predictionRepository.findByUserIdOrderByCreatedAtDesc(userId, limit)
            .map { it.toResponse() }
        val total = predictionRepository.countByUserId(userId)
        return PredictionHistoryResponse(items = items, total = total)
    }

    /**
     * 예측 요약 통계
     */
    fun getSummary(userId: Long): PredictionSummaryResponse {
        val all = predictionRepository.findByUserId(userId)
        val total = all.size.toLong()

        val withActuals = all.filter { it.actualViews != null && (it.predictedViews ?: 0) > 0 }
        val avgAccuracy = if (withActuals.isNotEmpty()) {
            withActuals.map { p ->
                val pv = p.predictedViews ?: 0L
                val av = p.actualViews ?: 0L
                val diff = Math.abs((av - pv).toDouble())
                if (pv > 0) (1.0 - diff / pv).coerceIn(0.0, 1.0) * 100 else 0.0
            }.average()
        } else 0.0

        val avgConfidence = all.mapNotNull { it.confidenceScore?.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0

        return PredictionSummaryResponse(
            totalPredictions = total,
            averageAccuracy = Math.round(avgAccuracy * 100) / 100.0,
            bestPlatform = "", // 플랫폼 데이터가 prediction에 없으므로 빈 문자열
            predictionsWithActuals = withActuals.size.toLong(),
            avgConfidenceScore = Math.round(avgConfidence * 100) / 100.0,
        )
    }

    private fun PerformancePrediction.toResponse() = PredictionResponse(
        id = id!!,
        videoId = videoId,
        predictedViews = predictedViews,
        predictedLikes = predictedLikes,
        predictedEngagementRate = predictedEngagementRate,
        confidenceScore = confidenceScore,
        optimalUploadTime = optimalUploadTime,
        predictionData = predictionData,
        actualViews = actualViews,
        actualLikes = actualLikes,
        createdAt = createdAt,
    )
}
