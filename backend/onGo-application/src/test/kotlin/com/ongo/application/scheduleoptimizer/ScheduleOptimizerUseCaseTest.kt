package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.result.ScheduleOptimalResult
import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.credit.CreditService
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.application.analytics.dto.OptimalTimeSlot
import com.ongo.application.analytics.dto.OptimalTimesResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendation
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import com.ongo.domain.video.VideoRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDateTime
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleOptimizerUseCaseTest {

    private val recommendations = InMemoryRecommendationRepository()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val scheduleUseCase = mockk<ScheduleUseCase>(relaxed = true)
    private val analyticsUseCase = mockk<AnalyticsUseCase>()
    private val videoRepository = mockk<VideoRepository>()
    private val slotRepository = mockk<OptimalSlotRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val channelRepository = mockk<ChannelRepository>()
    private val useCase = ScheduleOptimizerUseCase(
        slotRepository = slotRepository,
        recRepository = recommendations,
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
        scheduleRepository = scheduleRepository,
        scheduleUseCase = scheduleUseCase,
        analyticsUseCase = analyticsUseCase,
        videoRepository = videoRepository,
        channelRepository = channelRepository,
    )

    init {
        every { scheduleRepository.findByUserId(7L) } returns listOf(
            Schedule(
                id = 99L,
                videoId = 42L,
                userId = 7L,
                scheduledAt = LocalDateTime.of(2026, 8, 10, 8, 0),
                status = ScheduleStatus.SCHEDULED,
                platforms = mapOf(
                    "YOUTUBE#101" to mapOf("scheduledAt" to "2026-08-10T08:00:00"),
                    "YOUTUBE#303" to mapOf("scheduledAt" to "2026-08-10T08:15:00"),
                    "TIKTOK#202" to mapOf("scheduledAt" to "2026-08-10T08:30:00"),
                ),
            ),
        )
        every { scheduleRepository.findByUserId(8L) } returns emptyList()
        every { videoRepository.findById(42L) } returns null
    }

    @Test
    fun `apply recommendation only changes a recommendation owned by the current user`() {
        val recommendation = recommendations.save(recommendation(userId = 7L))
        val recommendationId = recommendation.id!!

        val applied = useCase.applyRecommendation(userId = 7L, id = recommendationId)

        assertEquals("APPLIED", applied.status)
        assertEquals("APPLIED", recommendations.findByIdAndUserId(recommendationId, 7L)?.status)
        verify {
            scheduleUseCase.updateSchedule(
                userId = 7L,
                scheduleId = 99L,
                request = match { request ->
                    request.platforms.orEmpty().first { it.channelId == 101L }.scheduledAt == LocalDateTime.of(2026, 8, 10, 9, 0) &&
                        request.platforms.orEmpty().first { it.channelId == 303L }.scheduledAt == LocalDateTime.of(2026, 8, 10, 8, 15)
                },
            )
        }
    }

    @Test
    fun `apply recommendation hides another user's recommendation`() {
        val recommendation = recommendations.save(recommendation(userId = 7L))
        val recommendationId = recommendation.id!!

        assertFailsWith<RuntimeException> {
            useCase.applyRecommendation(userId = 8L, id = recommendationId)
        }
        assertEquals("PENDING", recommendations.findByIdAndUserId(recommendationId, 7L)?.status)
    }

    @Test
    fun `legacy provider-wide recommendation is rejected for multiple accounts`() {
        val recommendation = recommendations.save(recommendation(userId = 7L).copy(channelId = null))

        assertFailsWith<RuntimeException> {
            useCase.applyRecommendation(userId = 7L, id = recommendation.id!!)
        }
        assertEquals("PENDING", recommendations.findByIdAndUserId(recommendation.id!!, 7L)?.status)
        verify(exactly = 0) { scheduleUseCase.updateSchedule(any(), any(), any()) }
    }

    @Test
    fun `recommendation listing materializes an analytics backed next slot`() {
        val tomorrow = LocalDateTime.now(ScheduleUseCase.KST).toLocalDate().plusDays(1)
        every { analyticsUseCase.getOptimalPublishTimes(7L, Platform.YOUTUBE) } returns OptimalTimesResponse(
            slots = listOf(
                OptimalTimeSlot(
                    dayOfWeek = tomorrow.dayOfWeek.value % 7,
                    dayLabel = "내일",
                    hour = 9,
                    timeLabel = "09:00",
                    expectedViews = 100,
                    engagementRate = 4.2,
                    confidenceScore = 76.0,
                    score = 90.0,
                ),
            ),
        )

        val result = useCase.getRecommendations(userId = 7L)

        assertEquals(2, result.size)
        assertEquals(setOf(101L, 303L), result.map { it.channelId }.toSet())
        assertEquals(setOf("YOUTUBE"), result.map { it.platform }.toSet())
        assertEquals(setOf("PENDING"), result.map { it.status }.toSet())
        assertEquals(setOf(76), result.map { it.confidence }.toSet())
    }

    /**
     * 프롬프트에는 실제 데이터만 들어간다. 예전에는 채널 ID·카테고리·성과 데이터 자리에
     * 전부 `"자동 분석"` 문자열을 넣고 정가로 과금했다.
     */
    @Test
    fun `최적 시간 생성 프롬프트에 실제 채널 ID 와 분석 데이터가 들어간다`() {
        stubConnectedChannels(Platform.YOUTUBE, "UC-main", "UC-second")
        stubOptimalTimes(
            timeSlot(dayLabel = "화요일", timeLabel = "20:00", expectedViews = 1500, engagement = 4.25, confidence = 80.0, score = 91.0),
            timeSlot(dayLabel = "일요일", timeLabel = "11:00", expectedViews = 900, engagement = 2.5, confidence = 40.0, score = 55.0),
        )
        val prompt = stubAiAndCredits()
        every { slotRepository.deleteByUserIdAndPlatform(7L, "YOUTUBE") } returns Unit
        every { slotRepository.saveBatch(any()) } answers {
            firstArg<List<OptimalSlot>>().mapIndexed { index, s -> s.copy(id = index + 1L) }
        }

        val result = useCase.generateOptimalSlots(userId = 7L, platform = "YOUTUBE")

        assertEquals(1, result.size)
        // 실제 platformChannelId — 연동된 계정 전부가 분석 범위와 같다.
        assertContains(prompt.captured, "채널 ID: UC-main, UC-second")
        // 카테고리 데이터는 없다. 꾸미지 않고 부재를 명시한다.
        assertContains(prompt.captured, "카테고리: 미지정")
        // 실제 분석 수치가 들어간다.
        assertContains(prompt.captured, "화요일 20:00: 조회수 중앙값 1500, 참여율 4.25%, 신뢰도 80%")
        assertContains(prompt.captured, "일요일 11:00: 조회수 중앙값 900, 참여율 2.50%, 신뢰도 40%")
        assertFalse(prompt.captured.contains("자동 분석"), "가짜 값이 남아 있으면 안 된다")
    }

    /**
     * **슬롯의 참여율이 `null` 일 때 프롬프트에 `"null"` 이나 `0` 을 넣지 않는다.**
     *
     * 그 슬롯의 게시물이 전부 참여 지표를 보고하지 않는 플랫폼(예: Pinterest)이면
     * 표본이 없어 참여율을 잰 적이 없다. 예전 서버는 빈 표본의 중앙값 `0.0` 을 채웠고
     * 프롬프트에는 "참여율 0.00%" 가 들어가, 모델이 그것을 관측으로 읽고 "참여가 없는
     * 시간대" 라는 근거로 일정을 추천했다. 유료 호출이라 대가까지 치른다.
     *
     * 이제 서버가 `null` 을 주는데, `String.format("%.2f", null)` 은 문자열 `"null"` 을
     * 만든다. 그래서 문장으로 바꾼다.
     */
    @Test
    fun `참여율을 재지 못한 슬롯은 프롬프트에 0이 아니라 문장으로 들어간다`() {
        stubConnectedChannels(Platform.YOUTUBE, "UC-main")
        stubOptimalTimes(
            timeSlot(dayLabel = "화요일", timeLabel = "20:00", expectedViews = 1500, engagement = null, confidence = 80.0, score = 91.0),
        )
        val prompt = stubAiAndCredits()
        every { slotRepository.deleteByUserIdAndPlatform(7L, "YOUTUBE") } returns Unit
        every { slotRepository.saveBatch(any()) } answers {
            firstArg<List<OptimalSlot>>().mapIndexed { index, s -> s.copy(id = index + 1L) }
        }

        useCase.generateOptimalSlots(userId = 7L, platform = "YOUTUBE")

        assertContains(
            prompt.captured,
            "화요일 20:00: 조회수 중앙값 1500, 참여율 ${ScheduleOptimizerUseCase.ENGAGEMENT_NOT_MEASURED}, 신뢰도 80%",
        )
        assertFalse(prompt.captured.contains("참여율 0.00%"), "재지 않은 참여율을 0 으로 보냈다")
        assertFalse(prompt.captured.contains("null"), "프롬프트에 'null' 이 남았다")
        // 미측정 문구는 단위를 들고 오지 않는다 — 밖에 `%` 를 붙이면 문장이 깨진다.
        assertFalse(prompt.captured.contains("${ScheduleOptimizerUseCase.ENGAGEMENT_NOT_MEASURED}%"))
    }

    /** **측정된 0% 는 관측이다.** 문장으로 감추면 실제 관찰을 잃는다. */
    @Test
    fun `측정된 참여율 0은 프롬프트에 0으로 들어간다`() {
        stubConnectedChannels(Platform.YOUTUBE, "UC-main")
        stubOptimalTimes(
            timeSlot(dayLabel = "화요일", timeLabel = "20:00", expectedViews = 1500, engagement = 0.0, confidence = 80.0, score = 91.0),
        )
        val prompt = stubAiAndCredits()
        every { slotRepository.deleteByUserIdAndPlatform(7L, "YOUTUBE") } returns Unit
        every { slotRepository.saveBatch(any()) } answers {
            firstArg<List<OptimalSlot>>().mapIndexed { index, s -> s.copy(id = index + 1L) }
        }

        useCase.generateOptimalSlots(userId = 7L, platform = "YOUTUBE")

        assertContains(prompt.captured, "참여율 0.00%")
        assertFalse(prompt.captured.contains(ScheduleOptimizerUseCase.ENGAGEMENT_NOT_MEASURED))
    }

    /** 미측정 문구는 숫자가 아니라 문장이어야 한다. */
    @Test
    fun `미측정 문구에 숫자가 들어가지 않는다`() {
        val text = ScheduleOptimizerUseCase.ENGAGEMENT_NOT_MEASURED

        assertTrue(text.isNotBlank())
        assertFalse(Regex("[0-9]").containsMatchIn(text), "미측정 문구에 숫자가 있다: $text")
    }

    /** 근거가 없으면 유료 호출을 하지 않는다. 차감 전에 거절해야 크레딧이 사라지지 않는다. */
    @Test
    fun `분석 데이터가 없으면 AI 호출도 크레딧 차감도 하지 않는다`() {
        stubConnectedChannels(Platform.YOUTUBE, "UC-main")
        stubOptimalTimes()

        val error = assertFailsWith<BusinessException> {
            useCase.generateOptimalSlots(userId = 7L, platform = "YOUTUBE")
        }

        assertEquals("ANALYTICS_DATA_UNAVAILABLE", error.code)
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        verify(exactly = 0) { slotRepository.deleteByUserIdAndPlatform(any(), any()) }
    }

    /** 채널이 없으면 분석도 프롬프트도 성립하지 않는다. 역시 차감 전에 거절한다. */
    @Test
    fun `연동 채널이 없으면 분석을 시도하지 않고 거절한다`() {
        every { channelRepository.findByUserId(7L) } returns emptyList()

        val error = assertFailsWith<BusinessException> {
            useCase.generateOptimalSlots(userId = 7L, platform = "YOUTUBE")
        }

        assertEquals("CHANNEL_NOT_CONNECTED", error.code)
        verify(exactly = 0) { analyticsUseCase.getOptimalPublishTimes(any(), any()) }
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }

    @Test
    fun `알 수 없는 플랫폼은 조회 전에 거절한다`() {
        val error = assertFailsWith<BusinessException> {
            useCase.generateOptimalSlots(userId = 7L, platform = "MYSPACE")
        }

        assertEquals("UNSUPPORTED_PLATFORM", error.code)
        verify(exactly = 0) { channelRepository.findByUserId(any()) }
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
    }

    private fun stubConnectedChannels(platform: Platform, vararg platformChannelIds: String) {
        every { channelRepository.findByUserId(7L) } returns platformChannelIds.mapIndexed { index, id ->
            Channel(
                id = index + 1L,
                userId = 7L,
                platform = platform,
                platformChannelId = id,
                channelName = "채널 $id",
                accessToken = EncryptedToken("enc"),
            )
        } + Channel(
            // 다른 플랫폼 채널은 프롬프트에 섞이면 안 된다.
            id = 900L,
            userId = 7L,
            platform = Platform.TIKTOK,
            platformChannelId = "TT-other",
            channelName = "틱톡",
            accessToken = EncryptedToken("enc"),
        )
    }

    private fun stubOptimalTimes(vararg slots: OptimalTimeSlot) {
        every { analyticsUseCase.getOptimalPublishTimes(7L, Platform.YOUTUBE) } returns
            OptimalTimesResponse(slots = slots.toList())
    }

    /** 크레딧 경로는 블록을 실제로 실행하고, AI 체인은 사용자 프롬프트를 잡아둔다. */
    private fun stubAiAndCredits(): CapturingSlot<String> {
        every { creditService.withCredits(7L, AiFeature.SCHEDULE_SUGGESTION, any<() -> Any>()) } answers {
            thirdArg<() -> Any>()()
        }
        val prompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(7L) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(prompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(ScheduleOptimalResult::class.java) } returns ScheduleOptimalResult(
            slots = listOf(
                ScheduleOptimalResult.SlotItem(
                    dayOfWeek = "TUE",
                    hour = 20,
                    score = 91,
                    audienceOnline = 1500,
                    competitionLevel = "LOW",
                    reason = "최근 성과가 가장 높은 구간",
                ),
            ),
        )
        return prompt
    }

    private fun timeSlot(
        dayLabel: String,
        timeLabel: String,
        expectedViews: Long,
        engagement: Double?,
        confidence: Double,
        score: Double,
    ) = OptimalTimeSlot(
        dayOfWeek = 2,
        dayLabel = dayLabel,
        hour = timeLabel.substringBefore(':').toInt(),
        timeLabel = timeLabel,
        expectedViews = expectedViews,
        engagementRate = engagement,
        confidenceScore = confidence,
        score = score,
    )

    private fun recommendation(userId: Long) = ScheduleRecommendation(
        userId = userId,
        videoId = 42L,
        channelId = 101L,
        videoTitle = "테스트 영상",
        recommendedSchedule = LocalDateTime.of(2026, 8, 10, 9, 0),
        platform = "YOUTUBE",
    )

    private class InMemoryRecommendationRepository : ScheduleRecommendationRepository {
        private var nextId = 1L
        private val records = linkedMapOf<Long, ScheduleRecommendation>()

        override fun findByIdAndUserId(id: Long, userId: Long) =
            records[id]?.takeIf { it.userId == userId }

        override fun findByUserId(userId: Long) = records.values.filter { it.userId == userId }

        override fun save(rec: ScheduleRecommendation) = rec.copy(id = nextId++).also {
            records[it.id!!] = it
        }

        override fun updateStatus(id: Long, userId: Long, status: String): Boolean {
            val current = findByIdAndUserId(id, userId) ?: return false
            records[id] = current.copy(status = status)
            return true
        }
    }
}
