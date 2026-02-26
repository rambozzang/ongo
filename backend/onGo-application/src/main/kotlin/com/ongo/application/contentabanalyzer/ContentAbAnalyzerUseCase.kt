package com.ongo.application.contentabanalyzer

import com.ongo.application.contentabanalyzer.dto.*
import com.ongo.domain.contentabanalyzer.ContentAbTest
import com.ongo.domain.contentabanalyzer.ContentAbTestRepository
import com.ongo.domain.contentabanalyzer.ContentVariant
import com.ongo.domain.contentabanalyzer.ContentVariantRepository
import org.springframework.stereotype.Service

@Service
class ContentAbAnalyzerUseCase(
    private val testRepository: ContentAbTestRepository,
    private val variantRepository: ContentVariantRepository,
) {

    fun getTests(userId: Long, status: String?): List<ContentAbTestResponse> {
        val tests = if (status != null) {
            testRepository.findByStatus(userId, status)
        } else {
            testRepository.findByUserId(userId)
        }
        return tests.map { it.toResponse() }
    }

    fun getSummary(userId: Long): ContentAbSummaryResponse {
        val tests = testRepository.findByUserId(userId)
        val completed = tests.count { it.status == "COMPLETED" }
        val winA = tests.count { it.winner == "A" }
        val winB = tests.count { it.winner == "B" }
        val total = (winA + winB).coerceAtLeast(1)
        return ContentAbSummaryResponse(
            totalTests = tests.size,
            completedTests = completed,
            avgConfidence = if (tests.isNotEmpty()) tests.map { it.confidence.toDouble() }.average() else 0.0,
            winRateA = winA.toDouble() / total * 100,
            winRateB = winB.toDouble() / total * 100,
        )
    }

    private fun ContentAbTest.toResponse(): ContentAbTestResponse {
        val testId = id!!
        val variants = variantRepository.findByTestId(testId)
        return ContentAbTestResponse(
            id = testId,
            title = title,
            variantA = variants.getOrNull(0)?.toResponse(),
            variantB = variants.getOrNull(1)?.toResponse(),
            status = status,
            winner = winner,
            confidence = confidence,
            startDate = startDate,
            endDate = endDate,
            createdAt = createdAt,
        )
    }

    private fun ContentVariant.toResponse() = ContentVariantResponse(
        id = id!!,
        label = label,
        videoId = videoId,
        videoTitle = videoTitle,
        views = views,
        likes = likes,
        comments = comments,
        ctr = ctr,
        avgWatchTime = avgWatchTime,
    )
}
