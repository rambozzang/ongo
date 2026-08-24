package com.ongo.application.ugc.shorts

import com.ongo.common.enums.AiFeature
import com.ongo.domain.ugc.shorts.PipelineStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 전사 크레딧 산정 규칙을 고정한다.
 *
 * 이 함수는 **청구 금액**을 정한다. 선검사·차감·환불·기록이 전부 여기서 나오므로,
 * 경계 하나가 틀리면 그 오차가 네 곳에 동시에 퍼진다.
 */
class ShortsPipelineCreditRequirementsTest {

    private val unit = AiFeature.STT.creditCost
    private val window = ShortsPipelineCreditRequirements.TRANSCRIBE_BILLING_WINDOW_MS

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

    /*
     * 총액은 전사만 길이에 따르고 나머지는 단가 그대로다. 다른 단계까지 비례하기
     * 시작하면 원가와 무관한 곳에서 요금이 오른다.
     */
    @Test
    fun `총액은 전사만 길이에 비례하고 나머지 단계는 그대로다`() {
        val others = ShortsPipelineCreditRequirements.FEATURE_BY_STAGE
            .filterKeys { it != PipelineStage.TRANSCRIBE }
            .values
            .sumOf { it.creditCost }

        assertEquals(others + unit, ShortsPipelineCreditRequirements.totalCreditsForRun(null))
        assertEquals(others + unit, ShortsPipelineCreditRequirements.totalCreditsForRun(window))
        assertEquals(others + unit * 2, ShortsPipelineCreditRequirements.totalCreditsForRun(window + 1))
        assertEquals(others + unit * 18, ShortsPipelineCreditRequirements.totalCreditsForRun(180L * 60 * 1000))
    }

    /*
     * 도입 이전 동작과의 동치. 이 값이 바뀌면 기존 실행·기존 테스트의 기대가 전부 어긋난다.
     * 37 을 리터럴로 적지 않고 매핑에서 더해 비교한다 — 단가가 바뀌면 함께 움직여야 한다.
     */
    @Test
    fun `길이를 모르는 총액은 단계 단가 합과 같다`() {
        val legacySum = ShortsPipelineCreditRequirements.FEATURE_BY_STAGE.values.sumOf { it.creditCost }
        assertEquals(legacySum, ShortsPipelineCreditRequirements.totalCreditsForRun(null))
    }
}
