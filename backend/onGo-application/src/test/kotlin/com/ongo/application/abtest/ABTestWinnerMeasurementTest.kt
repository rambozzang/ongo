package com.ongo.application.abtest

import com.ongo.common.exception.BusinessException
import com.ongo.domain.abtest.ABTest
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariant
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A/B 테스트 **우승 판정이 실제 측정 위에서만 일어나는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val winner = variants.maxByOrNull {
 *     if (it.views > 0) it.clicks.toDouble() / it.views else 0.0
 * }
 * ```
 *
 * 모든 변형의 노출이 0 이면 비교값이 전부 `0.0` 이라 `maxByOrNull` 은 **목록의 첫 변형**을
 * 돌려준다. 그것이 `winnerVariantId` 로 저장되고 테스트가 `COMPLETED` 가 된다. 화면은
 * 그 변형에 "우승" 배지를 붙이고 "우승 적용" 버튼을 보여준다.
 *
 * 사용자는 실험 결과라고 믿고 썸네일·제목을 바꾼다. **실제로는 순서상 첫 번째다.**
 *
 * 게다가 지금 `views`/`clicks` 를 채우는 경로가 코드 어디에도 없다 — 변형 생성 시 기본값
 * 0 이고 갱신하는 스케줄러·엔드포인트·동기화가 없다. onGo 는 썸네일을 직접 서빙하지
 * 않으므로 노출·클릭을 관측할 수단 자체가 없다. 즉 이 경로는 사실상 **항상** 첫 변형을
 * 우승으로 만들고 있었다.
 *
 * [ABTestStatisticsService] 와 [ABTestEvaluator] 는 이미 fail-closed 다 — 0/0 이면
 * `isSignificant = false` 라 자동 종료하지 않는다. 수동 버튼만 뚫려 있었다.
 */
class ABTestWinnerMeasurementTest {

    private val abTestRepository = mockk<ABTestRepository>()
    private val variantRepository = mockk<ABTestVariantRepository>()
    private val videoRepository = mockk<VideoRepository>(relaxed = true)

    private val useCase = ABTestUseCase(abTestRepository, variantRepository, videoRepository)

    private val userId = 7L
    private val testId = 100L

    private fun test(status: String = "RUNNING", winnerVariantId: Long? = null) = ABTest(
        id = testId, userId = userId, videoId = 1, testName = "썸네일 실험",
        status = status, winnerVariantId = winnerVariantId,
    )

    private fun variant(id: Long, name: String, views: Long, clicks: Long) = ABTestVariant(
        id = id, testId = testId, variantName = name, views = views, clicks = clicks,
    )

    private fun givenTest(vararg variants: ABTestVariant) {
        every { abTestRepository.findById(testId) } returns test()
        every { variantRepository.findByTestId(testId) } returns variants.toList()
        every { abTestRepository.update(any()) } answers { firstArg() }
    }

    // ── 측정 없는 우승 금지 ──────────────────────────────────────────────────

    /** **이 케이스가 첫 변형을 우승으로 만들던 자리다.** */
    @Test
    @DisplayName("노출이 하나도 없으면 우승을 정하지 않는다")
    fun noImpressionsMeansNoWinner() {
        givenTest(variant(1, "A", views = 0, clicks = 0), variant(2, "B", views = 0, clicks = 0))

        val error = assertFailsWith<BusinessException> { useCase.applyWinner(userId, testId) }

        assertEquals("AB_TEST_NO_MEASUREMENT", error.code)
        // 상태도 우승도 저장하지 않는다. 저장하면 되돌릴 방법이 없다.
        verify(exactly = 0) { abTestRepository.update(any()) }
    }

    /** 실패 사유가 "왜 정할 수 없는지" 를 말해야 사용자가 다음에 무엇을 할지 안다. */
    @Test
    @DisplayName("실패 메시지가 측정 데이터 부재를 설명한다")
    fun failureExplainsWhy() {
        givenTest(variant(1, "A", views = 0, clicks = 0), variant(2, "B", views = 0, clicks = 0))

        val error = assertFailsWith<BusinessException> { useCase.applyWinner(userId, testId) }

        assertTrue(error.message!!.contains("노출"), "무엇이 없는지 알려주지 않는다: ${error.message}")
    }

    // ── 측정된 실험은 그대로 동작 ────────────────────────────────────────────

    @Test
    @DisplayName("측정된 실험은 클릭률이 가장 높은 변형이 이긴다")
    fun measuredTestPicksTheHighestCtr() {
        givenTest(
            variant(1, "A", views = 1000, clicks = 50), // 5%
            variant(2, "B", views = 1000, clicks = 120), // 12%
        )
        val updated = slot<ABTest>()
        every { abTestRepository.update(capture(updated)) } answers { updated.captured }

        useCase.applyWinner(userId, testId)

        assertEquals(2L, updated.captured.winnerVariantId)
        assertEquals("COMPLETED", updated.captured.status)
    }

    /**
     * **측정된 변형이 하나뿐이면 비교가 성립하지 않는다.**
     *
     * 0 개만 막는 것으로는 부족하다. 하나만 측정됐을 때도 `maxByOrNull` 은 그것을 돌려주고
     * 우승으로 저장한다 — 겨룬 상대가 없는데 "이겼다" 가 되고, 미측정 변형은 겨루지도
     * 않은 채 패배 처리된다. 결과적으로 실험하지 않은 결론을 실험 결과로 제시한다.
     */
    @Test
    @DisplayName("측정된 변형이 하나뿐이면 우승을 정하지 않는다")
    fun singleMeasuredVariantIsNotAWinner() {
        givenTest(
            variant(1, "A", views = 0, clicks = 0), // 미측정
            variant(2, "B", views = 500, clicks = 25), // 5% — 유일한 측정값
        )

        val error = assertFailsWith<BusinessException> { useCase.applyWinner(userId, testId) }

        assertEquals("AB_TEST_NO_MEASUREMENT", error.code)
        // 상태도 우승도 저장하지 않는다.
        verify(exactly = 0) { abTestRepository.update(any()) }
    }

    /** 실패 메시지가 **몇 개가 측정됐고 몇 개가 필요한지**를 말해야 다음 행동을 안다. */
    @Test
    @DisplayName("실패 메시지가 측정된 변형 수와 필요 조건을 알려 준다")
    fun failureReportsMeasuredCountAndRequirement() {
        givenTest(
            variant(1, "A", views = 0, clicks = 0),
            variant(2, "B", views = 500, clicks = 25),
        )

        val error = assertFailsWith<BusinessException> { useCase.applyWinner(userId, testId) }

        assertTrue(error.message!!.contains("1개"), "측정된 변형 수가 없다: ${error.message}")
        assertTrue(
            error.message!!.contains("${ABTestUseCase.MIN_MEASURED_VARIANTS}개"),
            "필요 조건이 없다: ${error.message}",
        )
    }

    /** 최소 두 개가 측정되면 기존대로 판정한다. 경계를 과하게 조이면 기능이 죽는다. */
    @Test
    @DisplayName("두 개가 측정되면 정상적으로 판정한다")
    fun twoMeasuredVariantsAreJudged() {
        givenTest(
            variant(1, "A", views = 500, clicks = 25), // 5%
            variant(2, "B", views = 500, clicks = 50), // 10%
        )
        val updated = slot<ABTest>()
        every { abTestRepository.update(capture(updated)) } answers { updated.captured }

        useCase.applyWinner(userId, testId)

        assertEquals(2L, updated.captured.winnerVariantId)
    }

    /** 클릭이 0 이어도 **노출이 있으면 측정된 0%** 다. 판정 대상이다. */
    @Test
    @DisplayName("노출은 있고 클릭이 0이면 측정된 결과로 판정한다")
    fun zeroClicksWithImpressionsIsStillMeasured() {
        givenTest(
            variant(1, "A", views = 1000, clicks = 0),
            variant(2, "B", views = 1000, clicks = 30),
        )
        val updated = slot<ABTest>()
        every { abTestRepository.update(capture(updated)) } answers { updated.captured }

        useCase.applyWinner(userId, testId)

        assertEquals(2L, updated.captured.winnerVariantId)
    }

    // ── 요약 지표 ────────────────────────────────────────────────────────────

    /**
     * 화면은 이 값을 초록색으로 **"평균 CTR 개선율 +0.0%"** 라고 보여준다. 아무것도
     * 측정하지 않았는데 성과 지표가 생긴다.
     */
    @Test
    @DisplayName("측정된 완료 실험이 없으면 평균 개선율은 null이다")
    fun averageImprovementIsNullWithoutMeasurement() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test(status = "COMPLETED", winnerVariantId = 1))
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 0, clicks = 0),
            variant(2, "B", views = 0, clicks = 0),
        )

        val summary = useCase.getSummary(userId)

        assertNull(summary.averageImprovement, "0.0 은 '개선이 없었다' 는 관측 결과가 된다")
        assertEquals(1, summary.completedTests, "다른 집계까지 죽이면 안 된다")
    }

    @Test
    @DisplayName("측정된 실험이 있으면 실제 개선율을 계산한다")
    fun averageImprovementUsesMeasuredTests() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test(status = "COMPLETED", winnerVariantId = 2))
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 1000, clicks = 50), // 5%
            variant(2, "B", views = 1000, clicks = 100), // 10%
        )

        val summary = useCase.getSummary(userId)

        // (10 - 5) / 5 * 100 = 100%
        assertEquals(100.0, summary.averageImprovement)
    }

    /** 기준 클릭률이 0 이면 비율의 분모가 없다. 0% 로 채우면 "차이 없음" 이 된다. */
    @Test
    @DisplayName("기준 클릭률이 0이면 개선율을 만들어내지 않는다")
    fun zeroBaselineProducesNoImprovement() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test(status = "COMPLETED", winnerVariantId = 2))
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 1000, clicks = 0), // 0%
            variant(2, "B", views = 1000, clicks = 100), // 10%
        )

        val summary = useCase.getSummary(userId)

        assertNull(summary.averageImprovement, "0 기준선에서 개선율을 계산했다")
    }

    /** 완료된 실험이 아예 없어도 0% 를 주장하지 않는다. */
    @Test
    @DisplayName("완료된 실험이 없으면 평균 개선율은 null이다")
    fun noCompletedTestsMeansNull() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test(status = "RUNNING"))

        val summary = useCase.getSummary(userId)

        assertNull(summary.averageImprovement)
        assertEquals(0, summary.completedTests)
    }

    // ── 변형 지표 응답 계약 ──────────────────────────────────────────────────
    //
    // 도메인 기본값 0 이 그대로 응답에 실려 나가면 결과 차트가 "0.0% · 노출 0 · 클릭 0" 을
    // 정상 측정값처럼 그린다. 이 값들을 채우는 경로가 없으므로 **모든 변형이 항상
    // "0.0% 성과"** 로 표시되고 있었다.

    @Test
    @DisplayName("노출이 없는 변형은 지표를 null로 내려보낸다")
    fun unmeasuredVariantReportsNullMetrics() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test())
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 0, clicks = 0),
        )

        val response = useCase.listTests(userId).tests.single().variants.single()

        assertNull(response.views, "노출 0 이 측정값으로 나갔다")
        assertNull(response.clicks)
        assertNull(response.engagementRate)
        assertEquals(ABTestUseCase.VARIANT_METRICS_UNAVAILABLE, response.metricsUnavailableReason)
    }

    /** 측정된 변형의 값은 그대로 보존한다. 과하게 막으면 실제 성과가 사라진다. */
    @Test
    @DisplayName("노출이 있는 변형의 값은 그대로 보존한다")
    fun measuredVariantKeepsItsNumbers() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test())
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 1000, clicks = 37),
        )

        val response = useCase.listTests(userId).tests.single().variants.single()

        assertEquals(1000L, response.views)
        assertEquals(37L, response.clicks)
        assertNull(response.metricsUnavailableReason, "측정된 변형에 사유를 붙였다")
    }

    /** 노출이 있으면 클릭 0 은 **측정된 사실**이다. null 로 감추면 관측을 잃는다. */
    @Test
    @DisplayName("노출이 있고 클릭이 0이면 0을 그대로 내려보낸다")
    fun measuredZeroClicksStaysZero() {
        every { abTestRepository.findByUserId(userId) } returns listOf(test())
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 1000, clicks = 0),
        )

        val response = useCase.listTests(userId).tests.single().variants.single()

        assertEquals(0L, response.clicks)
        assertNull(response.metricsUnavailableReason)
    }

    /** 사유는 숫자가 아니라 문장이어야 한다. 0 을 넣으면 "노출 0회" 가 된다. */
    @Test
    @DisplayName("미측정 사유에 숫자가 들어가지 않는다")
    fun unavailableReasonIsNotANumber() {
        assertTrue(
            !Regex("[0-9]").containsMatchIn(ABTestUseCase.VARIANT_METRICS_UNAVAILABLE),
            "사유에 숫자가 있다: ${ABTestUseCase.VARIANT_METRICS_UNAVAILABLE}",
        )
    }

    // ── 완료 처리가 우승을 만들지 않는다 ─────────────────────────────────────

    /**
     * `completeTest`/`stopTest` 는 측정 없이도 COMPLETED 를 허용한다. 그것 자체는 사용자가
     * 실험을 접는 정당한 행위다. 다만 **우승을 만들어서는 안 된다** — 화면은
     * `variants[].isWinner` 로 트로피를 그린다.
     */
    @Test
    @DisplayName("완료 처리는 우승을 만들지 않는다")
    fun completingDoesNotInventAWinner() {
        every { abTestRepository.findById(testId) } returns test()
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 0, clicks = 0),
            variant(2, "B", views = 0, clicks = 0),
        )
        val updated = slot<ABTest>()
        every { abTestRepository.update(capture(updated)) } answers { updated.captured }

        val response = useCase.completeTest(userId, testId)

        assertEquals("COMPLETED", updated.captured.status)
        assertNull(updated.captured.winnerVariantId, "완료 처리가 우승을 지어냈다")
        assertNull(response.winnerVariantId)
    }

    @Test
    @DisplayName("중단 처리도 우승을 만들지 않는다")
    fun stoppingDoesNotInventAWinner() {
        every { abTestRepository.findById(testId) } returns test()
        every { variantRepository.findByTestId(testId) } returns listOf(
            variant(1, "A", views = 0, clicks = 0),
            variant(2, "B", views = 0, clicks = 0),
        )
        val updated = slot<ABTest>()
        every { abTestRepository.update(capture(updated)) } answers { updated.captured }

        useCase.stopTest(userId, testId)

        assertEquals("COMPLETED", updated.captured.status)
        assertNull(updated.captured.winnerVariantId)
    }
}
