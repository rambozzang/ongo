package com.ongo.api.ai

import com.ongo.application.ai.AiBatchProcessingUseCase
import com.ongo.application.ai.AnalyzeScriptUseCase
import com.ongo.application.ai.CompetitorInsightUseCase
import com.ongo.application.ai.ContentGapAnalysisUseCase
import com.ongo.application.ai.GenerateHashtagsUseCase
import com.ongo.application.ai.GenerateMetaUseCase
import com.ongo.application.ai.GenerateReportUseCase
import com.ongo.application.ai.GenerateReplyUseCase
import com.ongo.application.ai.GenerateRevenueReportUseCase
import com.ongo.application.ai.StrategyCoachUseCase
import com.ongo.application.ai.SttCreditCalculator
import com.ongo.application.ai.SttUseCase
import com.ongo.application.ai.SuggestScheduleUseCase
import com.ongo.application.ai.WeeklyDigestUseCase
import com.ongo.common.enums.AiFeature
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AiFeaturePricingControllerTest {
    private val sttCreditCalculator = SttCreditCalculator(model = "whisper-1")
    private val controller = AiController(
        generateMetaUseCase = mockk(relaxed = true),
        generateHashtagsUseCase = mockk(relaxed = true),
        sttUseCase = mockk(relaxed = true),
        analyzeScriptUseCase = mockk(relaxed = true),
        generateReplyUseCase = mockk(relaxed = true),
        suggestScheduleUseCase = mockk(relaxed = true),
        generateReportUseCase = mockk(relaxed = true),
        aiPipelineUseCase = mockk(relaxed = true),
        weeklyDigestUseCase = mockk(relaxed = true),
        contentGapAnalysisUseCase = mockk(relaxed = true),
        aiBatchProcessingUseCase = mockk(relaxed = true),
        competitorInsightUseCase = mockk(relaxed = true),
        strategyCoachUseCase = mockk(relaxed = true),
        generateRevenueReportUseCase = mockk(relaxed = true),
        sttCreditCalculator = sttCreditCalculator,
    )

    @Test
    fun `AI cost response mirrors the server AiFeature source`() {
        val response = controller.getFeaturePricing()
        val body = response.body ?: error("AI 비용 응답 본문이 없습니다")

        assertTrue(body.success)
        assertEquals(AiFeature.entries.size, body.data?.size)
        AiFeature.entries.filter { it != AiFeature.STT && it != AiFeature.VIDEO_TTS }.forEach { feature ->
            val item = body.data?.first { it.key == feature.name }
            assertEquals(feature.displayName, item?.displayName)
            assertEquals(feature.creditCost, item?.creditCost)
        }
    }

    /** 음성 합성은 글자 수 비례다. 0 을 보이면 무료로 오해한다. */
    @Test
    fun `TTS price is the per-1000-characters credits, never zero`() {
        val tts = controller.getFeaturePricing().body!!.data!!.first { it.key == AiFeature.VIDEO_TTS.name }

        assertEquals(sttCreditCalculator.unitEconomics.ttsCreditsPer1000Chars(), tts.creditCost)
        assertTrue(tts.creditCost > 0 && "1,000자당" in tts.displayName)
    }

    /** 음성 인식은 길이 비례다. 고정 enum 값(10)이 아니라 설정 모델의 10분당 크레딧을 보여야 한다. */
    @Test
    fun `STT price is the configured model's per-10-minute credits`() {
        val stt = controller.getFeaturePricing().body!!.data!!.first { it.key == AiFeature.STT.name }

        assertEquals(sttCreditCalculator.creditsPer10Minutes(), stt.creditCost)
        assertTrue("10분당" in stt.displayName)
    }
}
