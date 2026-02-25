package com.ongo.application.prediction

import com.ongo.application.prediction.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.prediction.PerformancePrediction
import com.ongo.domain.prediction.PredictionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
