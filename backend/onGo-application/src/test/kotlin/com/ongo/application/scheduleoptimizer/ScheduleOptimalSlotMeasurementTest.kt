package com.ongo.application.scheduleoptimizer

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.result.ScheduleOptimalResult
import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.analytics.dto.OptimalTimeSlot
import com.ongo.application.analytics.dto.OptimalTimesResponse
import com.ongo.application.credit.CreditService
import com.ongo.application.schedule.ScheduleUseCase
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.scheduleoptimizer.OptimalSlot
import com.ongo.domain.scheduleoptimizer.OptimalSlotRepository
import com.ongo.domain.scheduleoptimizer.ScheduleRecommendationRepository
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 최적 업로드 시간 슬롯의 숫자는 실측이다. AI 가 지어낸 "예상 시청자 수"·경쟁도·점수는 쓰지 않고,
 * 측정한 적 없는 시간대는 추천하지 않는다.
 */
class ScheduleOptimalSlotMeasurementTest {

    private val slotRepository = mockk<OptimalSlotRepository>()
    private val analyticsUseCase = mockk<AnalyticsUseCase>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val channelRepository = mockk<ChannelRepository>()
    private val useCase = ScheduleOptimizerUseCase(
        slotRepository = slotRepository,
        recRepository = mockk<ScheduleRecommendationRepository>(relaxed = true),
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
        scheduleRepository = mockk<ScheduleRepository>(relaxed = true),
        scheduleUseCase = mockk<ScheduleUseCase>(relaxed = true),
        analyticsUseCase = analyticsUseCase,
        videoRepository = mockk<VideoRepository>(relaxed = true),
        channelRepository = channelRepository,
    )

    private val saved = slot<List<OptimalSlot>>()

    /** 화요일 20시(조회수 중앙값 1500, 점수 200) · 일요일 11시(900, 100) 두 시간대만 측정돼 있다. */
    @BeforeEach
    fun setUp() {
        every { channelRepository.findByUserId(7) } returns listOf(
            Channel(id = 1, userId = 7, platform = Platform.YOUTUBE, platformChannelId = "UC1", channelName = "c", accessToken = EncryptedToken("e")),
        )
        every { analyticsUseCase.getOptimalPublishTimes(7, Platform.YOUTUBE) } returns OptimalTimesResponse(
            slots = listOf(
                OptimalTimeSlot(2, "화요일", 20, "20:00", expectedViews = 1500, engagementRate = 4.0, confidenceScore = 80.0, score = 200.0),
                OptimalTimeSlot(0, "일요일", 11, "11:00", expectedViews = 900, engagementRate = null, confidenceScore = 40.0, score = 100.0),
            ),
        )
        passThroughCredits(creditService, 7, AiFeature.SCHEDULE_SUGGESTION)
        every { slotRepository.deleteByUserIdAndPlatform(7, "YOUTUBE") } just runs
        every { slotRepository.saveBatch(capture(saved)) } answers { saved.captured.mapIndexed { i, s -> s.copy(id = i + 1L) } }
    }

    private fun item(day: String, hour: Int) = ScheduleOptimalResult.SlotItem(
        dayOfWeek = day, hour = hour, score = 99, audienceOnline = 50_000, competitionLevel = "LOW", reason = "근거 $day",
    )

    @Test
    fun `AI 가 지어낸 시청자 수·경쟁도·점수 대신 실측 조회수 중앙값과 상대 점수를 쓴다`() {
        stubChatEntity(chatClientResolver, 7, ScheduleOptimalResult::class.java, ScheduleOptimalResult(listOf(item("TUE", 20), item("sun", 11))))

        val response = useCase.generateOptimalSlots(7, "YOUTUBE")

        assertEquals(listOf(1500, 900), response.map { it.audienceOnline })
        assertEquals(listOf(100, 50), response.map { it.score }) // 최고 시간대 대비 %
        assertTrue(response.all { it.competitionLevel == null && it.measured && it.aiEstimate == null })
        assertEquals(listOf("TUE", "SUN"), response.map { it.dayOfWeek })
        assertEquals(listOf("근거 TUE", "근거 sun"), response.map { it.reason }) // 이유 문장은 AI 몫
        assertTrue(saved.captured.all { it.competitionLevel == ScheduleOptimizerUseCase.UNKNOWN_COMPETITION })
    }

    @Test
    fun `측정한 적 없는 시간대는 AI 가 골라도 버린다`() {
        stubChatEntity(chatClientResolver, 7, ScheduleOptimalResult::class.java, ScheduleOptimalResult(listOf(item("TUE", 20), item("WED", 9), item("XYZ", 1))))

        val response = useCase.generateOptimalSlots(7, "YOUTUBE")

        assertEquals(listOf("TUE"), response.map { it.dayOfWeek })
    }

    @Test
    fun `남는 시간대가 없으면 기존 추천을 지우지 않고 실패한다 — 크레딧은 환불 경로로 간다`() {
        stubChatEntity(chatClientResolver, 7, ScheduleOptimalResult::class.java, ScheduleOptimalResult(listOf(item("WED", 9))))

        val error = assertFailsWith<BusinessException> { useCase.generateOptimalSlots(7, "YOUTUBE") }

        assertEquals("AI_PARSE_ERROR", error.code)
        verify(exactly = 0) { slotRepository.deleteByUserIdAndPlatform(any(), any()) }
        verify(exactly = 0) { slotRepository.saveBatch(any()) }
    }

    @Test
    fun `예전에 AI 값으로 저장된 슬롯은 시청자 수를 숨기고 추정으로 표시한다`() {
        every { slotRepository.findByPlatform(7, "YOUTUBE") } returns listOf(
            OptimalSlot(id = 1, userId = 7, platform = "YOUTUBE", dayOfWeek = "MON", hour = 9, score = 87, audienceOnline = 32_000, competitionLevel = "LOW", reason = "옛 근거"),
        )

        val slot = useCase.getSlots(7, "YOUTUBE").single()

        assertNull(slot.audienceOnline)
        assertNull(slot.competitionLevel)
        assertEquals(false, slot.measured)
        assertEquals(ScheduleOptimizerUseCase.LEGACY_SLOT_BASIS, slot.aiEstimate?.basis)
    }
}
