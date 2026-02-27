package com.ongo.application.abtest

import com.ongo.application.abtest.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariant
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ABTestUseCase(
    private val abTestRepository: ABTestRepository,
    private val variantRepository: ABTestVariantRepository,
    private val videoRepository: VideoRepository,
) {

    fun listTests(userId: Long): ABTestListResponse {
        val tests = abTestRepository.findByUserId(userId)
        val responses = tests.map { test ->
            val variants = variantRepository.findByTestId(test.id!!)
            test.toResponse(variants)
        }
        return ABTestListResponse(tests = responses, totalCount = responses.size)
    }

    fun getTest(userId: Long, testId: Long): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()
        val variants = variantRepository.findByTestId(testId)
        return test.toResponse(variants)
    }

    @Transactional
    fun createTest(userId: Long, request: CreateABTestRequest): ABTestResponse {
        val test = ABTest(
            userId = userId,
            videoId = request.videoId,
            testName = request.testName,
            metricType = request.metricType,
            status = "DRAFT",
        )
        val saved = abTestRepository.save(test)

        val variants = request.variants.map { v ->
            variantRepository.save(
                ABTestVariant(
                    testId = saved.id!!,
                    variantName = v.variantName,
                    title = v.title,
                    description = v.description,
                    thumbnailUrl = v.thumbnailUrl,
                )
            )
        }

        return saved.toResponse(variants)
    }

    @Transactional
    fun updateTest(userId: Long, testId: Long, request: UpdateABTestRequest): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val updated = test.copy(
            testName = request.testName ?: test.testName,
            metricType = request.metricType ?: test.metricType,
        )
        val saved = abTestRepository.update(updated)
        val variants = variantRepository.findByTestId(testId)
        return saved.toResponse(variants)
    }

    @Transactional
    fun deleteTest(userId: Long, testId: Long) {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()
        variantRepository.deleteByTestId(testId)
        abTestRepository.delete(testId)
    }

    @Transactional
    fun startTest(userId: Long, testId: Long): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val updated = test.copy(
            status = "RUNNING",
            startedAt = LocalDateTime.now(),
        )
        val saved = abTestRepository.update(updated)
        val variants = variantRepository.findByTestId(testId)
        return saved.toResponse(variants)
    }

    @Transactional
    fun stopTest(userId: Long, testId: Long): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val updated = test.copy(
            status = "COMPLETED",
            endedAt = LocalDateTime.now(),
        )
        val saved = abTestRepository.update(updated)
        val variants = variantRepository.findByTestId(testId)
        return saved.toResponse(variants)
    }

    fun getVideosForTest(userId: Long): List<ABTestVideoResponse> {
        val videos = videoRepository.findByUserId(userId, page = 0, size = 100)
        return videos.map {
            ABTestVideoResponse(
                id = it.id!!,
                title = it.title,
                thumbnailUrl = it.thumbnailUrls.firstOrNull(),
                duration = it.durationSeconds,
            )
        }
    }

    @Transactional
    fun pauseTest(userId: Long, testId: Long): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val updated = test.copy(status = "PAUSED")
        val saved = abTestRepository.update(updated)
        val variants = variantRepository.findByTestId(testId)
        return saved.toResponse(variants)
    }

    @Transactional
    fun completeTest(userId: Long, testId: Long): ABTestResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val updated = test.copy(
            status = "COMPLETED",
            endedAt = LocalDateTime.now(),
        )
        val saved = abTestRepository.update(updated)
        val variants = variantRepository.findByTestId(testId)
        return saved.toResponse(variants)
    }

    @Transactional
    fun applyWinner(userId: Long, testId: Long) {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val variants = variantRepository.findByTestId(testId)
        if (variants.isEmpty()) throw NotFoundException("A/B 테스트 변형", testId)

        // 가장 높은 클릭률의 variant를 승자로 선정
        val winner = variants.maxByOrNull {
            if (it.views > 0) it.clicks.toDouble() / it.views else 0.0
        } ?: throw NotFoundException("A/B 테스트 변형", testId)

        val updated = test.copy(
            winnerVariantId = winner.id,
            status = if (test.status != "COMPLETED") "COMPLETED" else test.status,
            endedAt = test.endedAt ?: LocalDateTime.now(),
        )
        abTestRepository.update(updated)
    }

    fun getSummary(userId: Long): ABTestSummaryResponse {
        val tests = abTestRepository.findByUserId(userId)
        val activeTests = tests.count { it.status == "RUNNING" || it.status == "PAUSED" }
        val completedTests = tests.count { it.status == "COMPLETED" }

        // 완료된 테스트에서 평균 개선율 계산
        val avgImprovement = if (completedTests > 0) {
            val improvements = tests.filter { it.status == "COMPLETED" && it.winnerVariantId != null }.mapNotNull { test ->
                val variants = variantRepository.findByTestId(test.id!!)
                if (variants.size >= 2) {
                    val rates = variants.map {
                        if (it.views > 0) it.clicks.toDouble() / it.views * 100 else 0.0
                    }
                    val maxRate = rates.maxOrNull() ?: 0.0
                    val minRate = rates.minOrNull() ?: 0.0
                    if (minRate > 0) (maxRate - minRate) / minRate * 100 else 0.0
                } else null
            }
            if (improvements.isNotEmpty()) improvements.average() else 0.0
        } else 0.0

        return ABTestSummaryResponse(
            totalTests = tests.size,
            activeTests = activeTests,
            completedTests = completedTests,
            averageImprovement = Math.round(avgImprovement * 100) / 100.0,
        )
    }

    private fun ABTest.toResponse(variants: List<ABTestVariant>) = ABTestResponse(
        id = id!!,
        videoId = videoId,
        testName = testName,
        status = status,
        metricType = metricType,
        winnerVariantId = winnerVariantId,
        startedAt = startedAt,
        endedAt = endedAt,
        createdAt = createdAt,
        variants = variants.map { it.toResponse() },
    )

    private fun ABTestVariant.toResponse() = ABTestVariantResponse(
        id = id!!,
        variantName = variantName,
        title = title,
        description = description,
        thumbnailUrl = thumbnailUrl,
        views = views,
        clicks = clicks,
        engagementRate = engagementRate,
    )
}
