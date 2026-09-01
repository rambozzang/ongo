package com.ongo.application.abtest

import com.ongo.application.abtest.dto.*
import com.ongo.common.exception.BusinessException
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
        require(request.testName.trim().length in 1..120) { "A/B 테스트 이름은 1~120자여야 합니다" }
        require(request.variants.size in 2..4) { "A/B 테스트 변형은 2~4개여야 합니다" }
        require(request.variants.map { it.variantName.trim().uppercase() }.distinct().size == request.variants.size) {
            "A/B 테스트 변형 이름은 중복될 수 없습니다"
        }
        require(request.metricType in ALLOWED_METRICS) { "지원하지 않는 A/B 테스트 지표입니다" }
        require(request.durationHours == null || request.durationHours in 1..168) {
            "A/B 테스트 기간은 1~168시간이어야 합니다"
        }
        request.variants.forEach {
            require(it.variantName.trim().length in 1..20) { "변형 이름은 1~20자여야 합니다" }
        }
        request.videoId?.let { videoId ->
            val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
            if (video.userId != userId) throw ForbiddenException()
        }
        val test = ABTest(
            userId = userId,
            videoId = request.videoId,
            testName = request.testName,
            metricType = request.metricType,
            durationHours = request.durationHours,
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
        val activeVideoIds = abTestRepository.findByUserId(userId)
            .filter { it.status == "RUNNING" || it.status == "PAUSED" }
            .mapNotNull { it.videoId }
            .toSet()
        return videos.map {
            ABTestVideoResponse(
                id = it.id!!,
                title = it.title,
                thumbnailUrl = it.thumbnailUrls.firstOrNull(),
                duration = null,
                hasActiveTest = it.id in activeVideoIds,
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

        /*
         * **측정하지 않은 실험에 우승을 정하지 않는다.**
         *
         * 예전에는 `maxByOrNull { if (views > 0) clicks/views else 0.0 }` 였다. 모든 변형의
         * 노출이 0 이면 비교값이 전부 0 이라 `maxByOrNull` 은 **목록의 첫 변형**을 돌려주고,
         * 그것이 `winnerVariantId` 로 저장되며 테스트가 COMPLETED 가 됐다. 화면은 그 변형에
         * "우승" 배지를 붙이고 "우승 적용" 버튼을 보여준다.
         *
         * 사용자는 실험 결과라고 믿고 썸네일·제목을 바꾼다. 실제로는 순서상 첫 번째다.
         *
         * 지금 `views`/`clicks` 를 채우는 경로는 코드 어디에도 없다 — 변형 생성 시 기본값
         * 0 이고 갱신하는 스케줄러·엔드포인트·동기화가 없다. onGo 는 썸네일을 직접 서빙하지
         * 않으므로 노출·클릭을 관측할 수단 자체가 없다. 그래서 이 경로는 사실상 **항상**
         * 첫 변형을 우승으로 만들고 있었다.
         *
         * [ABTestStatisticsService] 와 [ABTestEvaluator] 는 이미 fail-closed 다 —
         * 0/0 이면 `isSignificant = false` 라 자동 종료하지 않는다. 수동 버튼만 뚫려 있었다.
         */
        /*
         * **비교는 최소 두 개가 측정돼야 성립한다.**
         *
         * 0 개만 막으면 부족하다. 측정된 변형이 **하나뿐**일 때도 `maxByOrNull` 은 그것을
         * 돌려주고 우승으로 저장한다. 겨룬 상대가 없는데 "이겼다" 가 되는 것이고,
         * 미측정 변형들은 겨루지도 않은 채 패배 처리된다. 결과는 0 개일 때와 같다 —
         * 실험하지 않은 결론을 실험 결과로 제시한다.
         */
        val measured = variants.filter { it.views > 0 }
        if (measured.size < MIN_MEASURED_VARIANTS) {
            throw BusinessException(
                "AB_TEST_NO_MEASUREMENT",
                "노출이 측정된 변형이 ${measured.size}개뿐이라 우승을 정할 수 없습니다. " +
                    "비교하려면 최소 ${MIN_MEASURED_VARIANTS}개 변형에 노출 데이터가 있어야 합니다.",
            )
        }

        // 측정된 변형끼리만 비교한다. 미측정 변형을 0% 로 섞으면 자동으로 패배 처리된다.
        val winner = measured.maxByOrNull { it.clicks.toDouble() / it.views }
            ?: throw NotFoundException("A/B 테스트 변형", testId)

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

        /*
         * **개선율은 측정된 실험에서만 나온다.**
         *
         * 예전에는 노출이 0 인 변형의 클릭률을 `0.0` 으로 두고, 기준값이 0 이면 개선율도
         * `0.0` 으로 채운 뒤 그것들을 평균에 넣었다. 화면은 그 값을 초록색으로
         * **"평균 CTR 개선율 +0.0%"** 라고 보여준다 — 아무것도 측정하지 않았는데 성과 지표가
         * 생긴다.
         *
         * 측정된 변형이 2 개 이상이고 기준 클릭률이 0 보다 큰 실험만 센다. 그런 실험이
         * 하나도 없으면 평균은 **`null`** 이다. 0 은 "개선이 없었다" 는 관측 결과다.
         */
        val improvements = tests
            .filter { it.status == "COMPLETED" && it.winnerVariantId != null }
            .mapNotNull { test ->
                val measuredVariants = variantRepository.findByTestId(test.id!!).filter { it.views > 0 }
                if (measuredVariants.size < 2) return@mapNotNull null
                val rates = measuredVariants.map { it.clicks.toDouble() / it.views * 100 }
                val maxRate = rates.max()
                val minRate = rates.min()
                // 기준이 0 이면 비율의 분모가 없다. 0% 로 채우면 "차이 없음" 이 된다.
                if (minRate > 0) (maxRate - minRate) / minRate * 100 else null
            }
        val avgImprovement = improvements.takeIf { it.isNotEmpty() }?.average()

        return ABTestSummaryResponse(
            totalTests = tests.size,
            activeTests = activeTests,
            completedTests = completedTests,
            averageImprovement = avgImprovement?.let { Math.round(it * 100) / 100.0 },
        )
    }

    private fun ABTest.toResponse(variants: List<ABTestVariant>) = ABTestResponse(
        id = id!!,
        videoId = videoId,
        testName = testName,
        status = status,
        metricType = metricType,
        durationHours = durationHours,
        winnerVariantId = winnerVariantId,
        startedAt = startedAt,
        endedAt = endedAt,
        createdAt = createdAt,
        variants = variants.map { it.toResponse() },
    )

    private fun ABTestVariant.toResponse(): ABTestVariantResponse {
        // 노출이 있어야 클릭도 CTR 도 존재할 수 있다. 그것이 측정 여부의 유일한 기준이다.
        val measured = views > 0
        return ABTestVariantResponse(
        id = id!!,
        variantName = variantName,
        title = title,
        description = description,
        thumbnailUrl = thumbnailUrl,
        /*
         * 노출이 0 이면 세 지표 모두 측정된 적이 없다. 도메인 기본값 0 을 그대로 내보내면
         * 결과 차트가 "0.0% 성과" 를 그린다 — 재지 않은 것과 성과가 없는 것은 다르다.
         *
         * `views > 0` 인 변형은 그대로 보존한다. 그때의 클릭 0 은 측정된 사실이다.
         */
        views = views.takeIf { measured },
        clicks = clicks.takeIf { measured },
        engagementRate = engagementRate.takeIf { measured },
        metricsUnavailableReason = if (measured) null else VARIANT_METRICS_UNAVAILABLE,
        )
    }

    companion object {
        private val ALLOWED_METRICS = setOf(
            "CTR", "VIEWS", "ENGAGEMENT",
            "THUMBNAIL", "TITLE", "DESCRIPTION", "TAGS",
        )

        /**
         * 우승을 정하려면 **실제로 노출이 측정된** 변형이 이만큼 있어야 한다.
         *
         * 하나로는 비교가 성립하지 않는다 — 겨룬 상대가 없는데 "이겼다" 가 되고,
         * 미측정 변형들은 겨루지도 않은 채 패배 처리된다.
         */
        const val MIN_MEASURED_VARIANTS = 2

        /**
         * 변형 지표를 낼 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * 숫자가 아니라 **문장**이어야 한다. 0 을 넣으면 "노출 0회" 라는 관측 결과가 된다.
         */
        const val VARIANT_METRICS_UNAVAILABLE =
            "노출이 수집되지 않아 이 변형의 성과를 측정할 수 없습니다"
    }

}
