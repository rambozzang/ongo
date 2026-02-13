package com.ongo.application.abtest

import com.ongo.application.abtest.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariant
import com.ongo.domain.abtest.ABTestVariantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ABTestUseCase(
    private val abTestRepository: ABTestRepository,
    private val variantRepository: ABTestVariantRepository,
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
