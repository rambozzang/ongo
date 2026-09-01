package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.ChannelInsightsDaily
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 트래픽 소스·인구통계가 **구현되지 않았다는 사실**을 계약으로 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 두 API 는 언제나 성공 응답을 냈다.
 *
 * ```
 * { "period": "30d", "sources": {}, "total": 0 }
 * { "period": "30d", "ageDistribution": {}, "genderDistribution": {}, ... }
 * ```
 *
 * 이 모양은 **"유입이 0 건이었다" / "그런 시청자가 없었다"** 는 관측과 구분되지 않는다.
 * 실제로는 그 값을 수집하는 경로가 **아예 없다.**
 *
 * - `channel_insights_daily` 를 채우는 [AnalyticsRepository.upsertChannelInsights] 는
 *   저장소 구현만 있고 **호출부가 하나도 없다**.
 * - 어댑터가 돌려주는 `PlatformAnalytics` 에는 트래픽 소스·인구통계 필드가 없다.
 *
 * 그래서 없는 통계를 지어내지 않고 `available = false` 로 그 사실을 알린다.
 * 이 파일은 **"생산자가 없다"** 는 사실 자체도 소스 스캔으로 고정한다 — 나중에 실제
 * 수집이 붙으면 이 테스트가 깨지면서 계약을 다시 보라고 알려준다.
 */
class ChannelInsightsAvailabilityTest {

    private val analyticsRepository = mockk<AnalyticsRepository>(relaxed = true)

    private val useCase = AnalyticsUseCase(
        analyticsRepository = analyticsRepository,
        videoRepository = mockk(relaxed = true),
        videoUploadRepository = mockk(relaxed = true),
        userRepository = mockk(relaxed = true),
        creditRepository = mockk(relaxed = true),
    )

    private val userId = 7L

    private fun givenInsights(rows: List<ChannelInsightsDaily>) {
        every { analyticsRepository.findChannelInsights(userId, null, any(), any()) } returns rows
    }

    private fun insightRow() = ChannelInsightsDaily(
        userId = userId,
        platform = Platform.YOUTUBE,
        date = LocalDate.now().minusDays(1),
        trafficSource = mapOf("SEARCH" to 120L),
        demographicsAge = mapOf("25-34" to 40.0),
        demographicsGender = mapOf("male" to 60.0),
        demographicsCountry = mapOf("KR" to 900L),
    )

    // ══ 1) 생산자가 없다는 사실 ═════════════════════════════════════════════

    /**
     * **이 테스트가 지키는 것은 코드가 아니라 사실이다.**
     *
     * `upsertChannelInsights` 를 부르는 production 코드가 생기면 여기서 깨진다. 그때는
     * 위 `available = false` 계약이 더 이상 맞지 않으므로 함께 고쳐야 한다.
     */
    @Test
    @DisplayName("upsertChannelInsights 를 부르는 production 호출부가 없다")
    fun noProductionCallerWritesChannelInsights() {
        /*
         * **호출만 센다.** 이름이 등장하는 것과 부르는 것은 다르다 — 선언·구현·주석에도
         * 이름은 나온다. 괄호가 붙은 형태만 호출이고, 그중 선언 두 줄(`fun ...`)은 뺀다.
         */
        val callers = mainSourceFiles()
            .filter { file ->
                file.readLines().any { line ->
                    line.contains("upsertChannelInsights(") && !line.contains("fun upsertChannelInsights(")
                }
            }
            .map { it.name }

        assertTrue(
            callers.isEmpty(),
            "채널 인사이트를 쓰는 곳이 생겼다. available=false 계약을 다시 확인하라: $callers",
        )
    }

    /** 선언·구현 자체는 그대로 있어야 한다 — 없으면 위 검사가 아무것도 검사하지 않는다. */
    @Test
    @DisplayName("스캔 대상 선언과 구현이 실제로 존재한다")
    fun theScanHasSomethingToFind() {
        val declaring = mainSourceFiles()
            .filter { it.readText().contains("fun upsertChannelInsights(") }
            .map { it.name }
            .toSet()

        assertEquals(setOf("AnalyticsRepository.kt", "AnalyticsJooqRepository.kt"), declaring)
    }

    /** 어댑터 응답에 애초에 그 필드가 없다 — 수집하려면 어댑터부터 바꿔야 한다. */
    @Test
    @DisplayName("어댑터 분석 응답에 트래픽 소스·인구통계 필드가 없다")
    fun adapterResultCarriesNoInsightFields() {
        val platformClient = mainSourceFiles().single { it.name == "PlatformClient.kt" }.readText()
        val analyticsShape = platformClient
            .substringAfter("data class PlatformAnalytics(")
            .substringBefore(")")

        listOf("trafficSource", "demographics").forEach { field ->
            assertFalse(
                analyticsShape.contains(field, ignoreCase = true),
                "어댑터가 $field 를 돌려주기 시작했다. 수집 경로를 다시 확인하라",
            )
        }
    }

    // ══ 2) 미수집 계약 ══════════════════════════════════════════════════════

    /** **이 케이스가 "유입 0 건" 을 관측처럼 내보내던 자리다.** */
    @Test
    @DisplayName("수집 행이 없으면 트래픽 소스를 미수집으로 표시한다")
    fun trafficSourcesReportUnavailable() {
        givenInsights(emptyList())

        val response = useCase.getTrafficSources(userId, 30)

        assertFalse(response.available, "재지 않은 것을 측정 성공처럼 내보냈다")
        assertEquals(AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON, response.unavailableReason)
        // 기존 필드 모양은 그대로다 — 이 필드를 모르는 클라이언트가 깨지지 않는다.
        assertTrue(response.sources.isEmpty())
        assertEquals(0L, response.total)
        assertEquals("30d", response.period)
    }

    @Test
    @DisplayName("수집 행이 없으면 인구통계를 미수집으로 표시한다")
    fun demographicsReportUnavailable() {
        givenInsights(emptyList())

        val response = useCase.getDemographics(userId, 30)

        assertFalse(response.available, "재지 않은 것을 측정 성공처럼 내보냈다")
        assertEquals(AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON, response.unavailableReason)
        assertTrue(response.ageDistribution.isEmpty())
        assertTrue(response.genderDistribution.isEmpty())
        assertTrue(response.topCountries.isEmpty())
    }

    /** 사유는 숫자가 아니라 **문장**이어야 한다. 화면이 그대로 보여준다. */
    @Test
    @DisplayName("미수집 사유는 숫자가 아닌 문장이다")
    fun reasonIsASentence() {
        val reason = AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON

        assertTrue(reason.isNotBlank())
        assertFalse(Regex("[0-9]").containsMatchIn(reason), "사유에 숫자가 있다: $reason")
    }

    /**
     * **"아직 데이터 없음" 과 다른 문구여야 한다.** 저쪽은 기다리면 쌓이고, 이쪽은
     * 연동 자체가 없어 기다려도 채워지지 않는다.
     */
    @Test
    @DisplayName("미수집 사유가 다른 미측정 문구와 구분된다")
    fun reasonIsDistinctFromOtherUnavailableTexts() {
        val reason = AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON

        assertTrue(reason != AnalyticsUseCase.CTR_UNAVAILABLE)
        assertTrue(reason != AnalyticsUseCase.SUBSCRIBER_CONVERSION_UNAVAILABLE)
    }

    // ══ 2-b) 행은 있는데 분포가 비어 있을 때 ═══════════════════════════════
    //
    // 예전 판정은 `insights.isNotEmpty()` 였다. `channel_insights_daily` 행이 있어도
    // `traffic_source` JSONB 가 `{}` 면 유입을 **잰 적이 없는데**, 그 행 하나로
    // `available = true` 가 열려 빈 분포가 "유입 0 건" 이라는 관측으로 보였다.
    //
    // 판정은 행 수가 아니라 **분포 항목의 존재**로 한다.

    /** 빈 분포만 든 행. 저장은 됐지만 아무것도 관측되지 않은 상태다. */
    private fun emptyRow() = ChannelInsightsDaily(
        userId = userId,
        platform = Platform.YOUTUBE,
        date = LocalDate.now().minusDays(1),
    )

    /** **(a) 이 케이스가 빈 분포를 수집 완료로 열던 자리다.** */
    @Test
    @DisplayName("행은 있으나 분포가 모두 비면 두 API 모두 미수집이다")
    fun emptyDistributionsStayUnavailable() {
        givenInsights(listOf(emptyRow()))

        val traffic = useCase.getTrafficSources(userId, 30)
        val demographics = useCase.getDemographics(userId, 30)

        assertFalse(traffic.available, "빈 분포를 수집 완료로 열었다")
        assertEquals(AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON, traffic.unavailableReason)
        assertFalse(demographics.available, "빈 분포를 수집 완료로 열었다")
        assertEquals(AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON, demographics.unavailableReason)
    }

    /** **(b) 한쪽만 관측된 행은 그쪽만 열린다.** */
    @Test
    @DisplayName("트래픽만 관측된 행은 트래픽만 열린다")
    fun trafficOnlyRowOpensTrafficOnly() {
        givenInsights(listOf(emptyRow().copy(trafficSource = mapOf("SEARCH" to 120L))))

        val traffic = useCase.getTrafficSources(userId, 30)
        val demographics = useCase.getDemographics(userId, 30)

        assertTrue(traffic.available)
        assertNull(traffic.unavailableReason)
        assertEquals(120L, traffic.sources["SEARCH"])

        assertFalse(demographics.available, "관측되지 않은 인구통계를 함께 열었다")
        assertEquals(AnalyticsUseCase.CHANNEL_INSIGHTS_UNAVAILABLE_REASON, demographics.unavailableReason)
    }

    /** 반대 방향도 같다 — 인구통계만 있으면 트래픽은 닫혀 있어야 한다. */
    @Test
    @DisplayName("인구통계만 관측된 행은 트래픽을 열지 않는다")
    fun demographicsOnlyRowDoesNotOpenTraffic() {
        givenInsights(listOf(emptyRow().copy(demographicsAge = mapOf("25-34" to 40.0))))

        assertFalse(useCase.getTrafficSources(userId, 30).available, "관측되지 않은 트래픽을 열었다")
        assertTrue(useCase.getDemographics(userId, 30).available)
    }

    /** **(c) 부분 관측은 열되, 관측되지 않은 분포는 빈 맵 그대로 둔다.** */
    @Test
    @DisplayName("인구통계 부분 관측은 열리고 없는 분포는 빈 맵이다")
    fun partialDemographicsOpenWithEmptyRemainder() {
        givenInsights(listOf(emptyRow().copy(demographicsAge = mapOf("25-34" to 40.0))))

        val response = useCase.getDemographics(userId, 30)

        assertTrue(response.available, "부분 관측을 미수집으로 닫았다")
        assertNull(response.unavailableReason)
        assertEquals(40.0, response.ageDistribution["25-34"])
        // 없는 분포를 지어내지 않는다.
        assertTrue(response.genderDistribution.isEmpty(), "관측되지 않은 성별 분포를 만들었다")
        assertTrue(response.topCountries.isEmpty(), "관측되지 않은 국가 분포를 만들었다")
    }

    /** 국가만 관측돼도 열린다 — 세 분포 중 어느 하나로도 판정된다. */
    @Test
    @DisplayName("국가 분포만 있어도 인구통계가 열린다")
    fun countryOnlyOpensDemographics() {
        givenInsights(listOf(emptyRow().copy(demographicsCountry = mapOf("KR" to 900L))))

        val response = useCase.getDemographics(userId, 30)

        assertTrue(response.available)
        assertEquals(900L, response.topCountries["KR"])
        assertTrue(response.ageDistribution.isEmpty())
    }

    // ══ 2-c) 빈 맵과 0 관측은 다르다 ═══════════════════════════════════════

    /**
     * **(d) 항목이 있고 값이 `0` 인 것은 관측이다.**
     *
     * "검색 유입이 0 건이었다" 는 사실이므로 열려야 한다. 값이 아니라 **키**로 판정하기
     * 때문에 자연히 그렇게 된다 — 값으로 판정하면 이 관측을 잃는다.
     */
    @Test
    @DisplayName("트래픽 항목 값이 0이어도 관측이므로 열린다")
    fun zeroValuedTrafficEntryIsMeasured() {
        givenInsights(listOf(emptyRow().copy(trafficSource = mapOf("SEARCH" to 0L))))

        val response = useCase.getTrafficSources(userId, 30)

        assertTrue(response.available, "실측 0 을 미수집으로 닫았다")
        assertNull(response.unavailableReason)
        assertEquals(0L, response.sources["SEARCH"])
        // 합계 계산 방식은 그대로다.
        assertEquals(0L, response.total)
    }

    @Test
    @DisplayName("인구통계 항목 값이 0이어도 관측이므로 열린다")
    fun zeroValuedDemographicsEntryIsMeasured() {
        givenInsights(
            listOf(
                emptyRow().copy(
                    demographicsAge = mapOf("25-34" to 0.0),
                    demographicsGender = mapOf("male" to 0.0),
                    demographicsCountry = mapOf("KR" to 0L),
                ),
            ),
        )

        val response = useCase.getDemographics(userId, 30)

        assertTrue(response.available, "실측 0 을 미수집으로 닫았다")
        assertNull(response.unavailableReason)
        assertEquals(0.0, response.ageDistribution["25-34"])
        assertEquals(0.0, response.genderDistribution["male"])
        assertEquals(0L, response.topCountries["KR"])
    }

    /** 여러 행 중 하나만 관측돼도 열린다 — 빈 행이 관측을 덮지 않는다. */
    @Test
    @DisplayName("빈 행이 섞여 있어도 관측된 행이 있으면 열린다")
    fun oneMeasuredRowAmongEmptyOnesOpens() {
        givenInsights(listOf(emptyRow(), insightRow(), emptyRow()))

        assertTrue(useCase.getTrafficSources(userId, 30).available)
        assertTrue(useCase.getDemographics(userId, 30).available)
    }

    // ══ 3) 수집이 붙으면 자동으로 열린다 ════════════════════════════════════

    /**
     * 판정을 하드코딩하지 않고 **행의 존재**로 한다. 나중에 실제 수집이 붙으면 이
     * 코드를 고치지 않아도 열려야 한다 — 그렇지 않으면 진짜 데이터가 묻힌다.
     */
    @Test
    @DisplayName("수집 행이 생기면 트래픽 소스가 열린다")
    fun trafficSourcesOpenWhenRowsExist() {
        givenInsights(listOf(insightRow()))

        val response = useCase.getTrafficSources(userId, 30)

        assertTrue(response.available, "실제 수집 행이 있는데 미수집으로 막았다")
        assertNull(response.unavailableReason)
        assertEquals(120L, response.sources["SEARCH"])
        assertEquals(120L, response.total)
    }

    @Test
    @DisplayName("수집 행이 생기면 인구통계가 열린다")
    fun demographicsOpenWhenRowsExist() {
        givenInsights(listOf(insightRow()))

        val response = useCase.getDemographics(userId, 30)

        assertTrue(response.available, "실제 수집 행이 있는데 미수집으로 막았다")
        assertNull(response.unavailableReason)
        assertEquals(40.0, response.ageDistribution["25-34"])
        assertEquals(60.0, response.genderDistribution["male"])
        assertEquals(900L, response.topCountries["KR"])
    }

    /** 조회 자체는 그대로 한다 — 응답을 비운다고 쿼리를 건너뛰면 수집이 붙어도 안 열린다. */
    @Test
    @DisplayName("두 API 모두 저장소를 실제로 조회한다")
    fun bothEndpointsStillQueryTheRepository() {
        givenInsights(emptyList())

        useCase.getTrafficSources(userId, 30)
        useCase.getDemographics(userId, 30)

        verify(exactly = 2) { analyticsRepository.findChannelInsights(userId, null, any(), any()) }
    }

    // ══ 공통 ════════════════════════════════════════════════════════════════

    /**
     * 백엔드 모든 모듈의 production 소스 파일.
     *
     * 테스트 소스와 빌드 산출물은 뺀다 — 테스트가 저장소를 스텁하는 것은 생산자가
     * 아니고, `build/` 안의 사본을 세면 같은 파일이 두 번 잡힌다.
     */
    private fun mainSourceFiles(): List<File> {
        val backendRoot = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }

        return backendRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/src/main/kotlin/") }
            .filterNot { it.path.contains("/build/") }
            .toList()
            .also { assertNotNull(it.firstOrNull(), "production 소스를 하나도 찾지 못했다") }
    }
}
