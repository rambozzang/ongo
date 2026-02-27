package com.ongo.application.contentabanalyzer

import com.ongo.application.contentabanalyzer.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.contentabanalyzer.ContentAbTest
import com.ongo.domain.contentabanalyzer.ContentAbTestRepository
import com.ongo.domain.contentabanalyzer.ContentVariant
import com.ongo.domain.contentabanalyzer.ContentVariantRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ContentAbAnalyzerUseCase(
    private val testRepository: ContentAbTestRepository,
    private val variantRepository: ContentVariantRepository,
    private val videoRepository: VideoRepository,
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

    @Transactional
    fun createTest(userId: Long, request: CreateAbTestRequest): ContentAbTestResponse {
        val videoA = videoRepository.findById(request.videoIdA) ?: throw NotFoundException("영상", request.videoIdA)
        val videoB = videoRepository.findById(request.videoIdB) ?: throw NotFoundException("영상", request.videoIdB)

        val test = ContentAbTest(
            userId = userId,
            title = request.title,
            status = "RUNNING",
            startDate = LocalDate.now(),
        )
        val saved = testRepository.save(test)

        val variantA = variantRepository.save(
            ContentVariant(
                testId = saved.id!!,
                label = "A",
                videoId = videoA.id!!,
                videoTitle = videoA.title,
            )
        )
        val variantB = variantRepository.save(
            ContentVariant(
                testId = saved.id!!,
                label = "B",
                videoId = videoB.id!!,
                videoTitle = videoB.title,
            )
        )

        return ContentAbTestResponse(
            id = saved.id!!,
            title = saved.title,
            variantA = variantA.toResponse(),
            variantB = variantB.toResponse(),
            status = saved.status,
            winner = saved.winner,
            confidence = saved.confidence,
            startDate = saved.startDate,
            endDate = saved.endDate,
            createdAt = saved.createdAt,
        )
    }

    @Transactional
    fun completeTest(userId: Long, testId: Long): ContentAbTestResponse {
        val test = testRepository.findById(testId) ?: throw NotFoundException("콘텐츠 A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException("해당 테스트에 대한 권한이 없습니다")

        val variants = variantRepository.findByTestId(testId)
        val variantA = variants.getOrNull(0)
        val variantB = variants.getOrNull(1)

        // 승자 결정: CTR이 높은 쪽 또는 조회수가 높은 쪽
        val winner = if (variantA != null && variantB != null) {
            if (variantA.ctr > variantB.ctr) "A"
            else if (variantB.ctr > variantA.ctr) "B"
            else if (variantA.views > variantB.views) "A"
            else "B"
        } else null

        testRepository.updateStatus(testId, "COMPLETED", winner)

        val updated = test.copy(status = "COMPLETED", winner = winner, endDate = LocalDate.now())
        return ContentAbTestResponse(
            id = updated.id!!,
            title = updated.title,
            variantA = variantA?.toResponse(),
            variantB = variantB?.toResponse(),
            status = updated.status,
            winner = updated.winner,
            confidence = updated.confidence,
            startDate = updated.startDate,
            endDate = updated.endDate,
            createdAt = updated.createdAt,
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
