package com.ongo.application.ugc.shorts

import com.ongo.common.enums.AiFeature
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.domain.ugc.shorts.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 전사 크레딧 산정 규칙을 고정한다.
 *
 * 이 함수는 **청구 금액**을 정한다. 선검사·차감·환불·기록이 전부 여기서 나오므로,
 * 경계 하나가 틀리면 그 오차가 네 곳에 동시에 퍼진다.
 */
class ShortsPipelineCreditRequirementsTest {

    private val unit = AiFeature.STT.creditCost
    private val window = ShortsPipelineCreditRequirements.TRANSCRIBE_BILLING_WINDOW_MS
    private val economics = AiUnitEconomics()

    /* 이 필드 도입 이전 실행. 소급 측정하지 않고 종전 정액을 유지한다. */
    @Test
    fun `길이를 모르는 실행은 종전 정액이다`() {
        assertEquals(unit, ShortsPipelineCreditRequirements.transcribeCredits(null))
    }

    /*
     * 기존 고객이 겪는 변화가 없다는 증거. 창 이하 원본은 종전과 같은 금액이어야 한다.
     * 이 테스트가 깨지면 "정상 고객을 그대로 둔다"는 약속이 깨진 것이다.
     */
    @Test
    fun `과금 창 이하는 한 단위다`() {
        assertEquals(unit, ShortsPipelineCreditRequirements.transcribeCredits(1))
        assertEquals(unit, ShortsPipelineCreditRequirements.transcribeCredits(window / 2))
        assertEquals(unit, ShortsPipelineCreditRequirements.transcribeCredits(window))
    }

    /*
     * 경계. `ceil` 을 `floor` 나 정수 나눗셈으로 바꾸면 여기서 걸린다 —
     * 창을 1ms 넘긴 것은 두 번째 창을 **시작한** 것이다.
     */
    @Test
    fun `과금 창을 1밀리초라도 넘기면 두 단위다`() {
        assertEquals(unit * 2, ShortsPipelineCreditRequirements.transcribeCredits(window + 1))
    }

    @Test
    fun `창 배수 경계에서 단위가 늘지 않는다`() {
        assertEquals(unit * 2, ShortsPipelineCreditRequirements.transcribeCredits(window * 2))
        assertEquals(unit * 3, ShortsPipelineCreditRequirements.transcribeCredits(window * 2 + 1))
    }

    /* 현재 길이 상한(3시간)에서의 금액. 노출 크기를 숫자로 못박는다. */
    @Test
    fun `180분 원본은 18단위다`() {
        val threeHoursMs = 180L * 60 * 1000
        assertEquals(unit * 18, ShortsPipelineCreditRequirements.transcribeCredits(threeHoursMs))
    }

    /*
     * 0 이나 음수는 길이가 아니다. 통과시키면 조용히 0 크레딧이나 음수 청구가 되고,
     * 그건 무상 제공과 구분되지 않는다.
     */
    @Test
    fun `0 이하 길이는 거절한다`() {
        assertFailsWith<IllegalArgumentException> {
            ShortsPipelineCreditRequirements.transcribeCredits(0)
        }
        assertFailsWith<IllegalArgumentException> {
            ShortsPipelineCreditRequirements.transcribeCredits(-1)
        }
    }

    /* Every stage amount must use the same model economics as the preflight estimate. */
    @Test
    fun `SEGMENT 예산은 전체 전사문과 타임코드 입력 상한을 반영한다`() {
        val durationMs = 180L * 60 * 1000
        val expected = maxOf(
            AiFeature.SHORTS_SEGMENT.creditCost,
            economics.creditsForLlmCall(
                economics.transcriptCharsFor(180.0) +
                    ShortsPipelineCreditRequirements.SEGMENT_TIMECODE_MAX_CHARS +
                    ShortsPipelineCreditRequirements.PROMPT_OVERHEAD_CHARS,
            ),
        )
        val actual = ShortsPipelineCreditRequirements.creditCostForStage(
            PipelineStage.SEGMENT, durationMs, unit, economics,
        )

        assertEquals(expected, actual)
        assertTrue(actual > AiFeature.SHORTS_SEGMENT.creditCost)
        assertEquals(
            actual,
            ShortsPipelineCreditRequirements.creditCostForStage(PipelineStage.SEGMENT, null, unit, economics),
            "길이 미측정 원본도 허용 최대 길이를 기준으로 견적해야 한다",
        )
    }

    @Test
    fun `SUBTITLE 예산은 최대 입력과 여유분을 반영한다`() {
        val conservativeEconomics = AiUnitEconomics(targetCostRatio = 0.05)
        val expected = maxOf(
            AiFeature.SHORTS_SUBTITLE.creditCost,
            conservativeEconomics.creditsForLlmCall(ShortsPipelineCreditRequirements.SUBTITLE_MAX_INPUT_CHARS),
        )
        val actual = ShortsPipelineCreditRequirements.creditCostForStage(
            PipelineStage.SUBTITLE, 60_000, unit, conservativeEconomics,
        )
        assertEquals(expected, actual)
        assertTrue(actual > AiFeature.SHORTS_SUBTITLE.creditCost)
    }

    @Test
    fun `HOOK TEMPLATE VALIDATE REFRAME 은 현재 고정 단가를 유지한다`() {
        listOf(PipelineStage.REFRAME, PipelineStage.HOOK, PipelineStage.TEMPLATE, PipelineStage.VALIDATE).forEach { stage ->
            assertEquals(
                ShortsPipelineCreditRequirements.FEATURE_BY_STAGE.getValue(stage).creditCost,
                ShortsPipelineCreditRequirements.creditCostForStage(stage, window, unit, economics),
            )
        }
    }

    @Test
    fun `사전 예상 총액은 동일한 동적 단계 금액의 합이다`() {
        val durationMs = 90L * 60 * 1000
        val expected = ShortsPipelineCreditRequirements.FEATURE_BY_STAGE.keys.sumOf { stage ->
            ShortsPipelineCreditRequirements.creditCostForStage(stage, durationMs, unit, economics)
        }
        assertEquals(
            expected,
            ShortsPipelineCreditRequirements.totalCreditsForRun(durationMs, unit, economics),
        )
    }

    /**
     * 길이를 모르는 실행(길이 측정 도입 전 행)의 전사 단계는 **허용 최대 길이**로 매긴다. 10분 값으로 매기면
     * 그 실행을 다시 돌릴 때 긴 원본을 10분 가격에 전사한다 — 음성 인식은 원가 가로채기가 막지 못한다.
     */
    @Test
    fun `unknown duration transcribes at the maximum source length, not one window`() {
        val economics = AiUnitEconomics()
        val maxMs = 180 * 60_000L
        val unknown = ShortsPipelineCreditRequirements.creditCostForStage(PipelineStage.TRANSCRIBE, null, 41, economics, maxMs)

        assertEquals(ShortsPipelineCreditRequirements.transcribeCredits(maxMs, 41), unknown)
        assertEquals(18 * 41, unknown)
    }
}
