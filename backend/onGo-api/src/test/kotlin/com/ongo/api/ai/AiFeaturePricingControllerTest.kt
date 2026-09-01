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
import com.ongo.application.ai.SttUseCase
import com.ongo.application.ai.SuggestScheduleUseCase
import com.ongo.application.ai.WeeklyDigestUseCase
import com.ongo.common.enums.AiFeature
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AiFeaturePricingControllerTest {
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
    )

    @Test
    fun `AI cost response mirrors the server AiFeature source`() {
        val response = controller.getFeaturePricing()
        val body = response.body ?: error("AI 비용 응답 본문이 없습니다")

        assertTrue(body.success)
        assertEquals(AiFeature.entries.size, body.data?.size)
        AiFeature.entries.forEach { feature ->
            val item = body.data?.first { it.key == feature.name }
            assertEquals(feature.displayName, item?.displayName)
            assertEquals(feature.creditCost, item?.creditCost)
        }
    }
}
