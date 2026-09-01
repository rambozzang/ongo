package com.ongo.domain.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 파이프라인 정산 배분의 순수 계산.
 *
 * 여기서 지키는 것은 셋이다.
 *
 *  1. **합계가 정확히 차감액과 같다** — 크거나 작으면 환불이 차감을 넘거나 잔돈이 남는다
 *  2. **할인이 스텝에 고르게 나뉜다** — 실행 조합이 무엇이든 사용자가 낸 단가와 맞는다
 *  3. **같은 입력에 같은 결과** — 재계산할 때마다 환불액이 흔들리면 대사가 불가능하다
 */
class AiPipelineSettlementTest {

    private val allSteps = AiPipelineStep.entries.toList()

    @Test
    fun `배분 합계는 언제나 차감액과 같다`() {
        // 스텝 조합을 전수로 돌린다. 하나라도 어긋나면 원장이 맞지 않는다.
        for (mask in 1 until (1 shl allSteps.size)) {
            val steps = allSteps.filterIndexed { index, _ -> (mask shr index) and 1 == 1 }
            val total = AiPipelineStep.calculateTotalCost(steps)

            val shares = AiPipelineSettlement.distribute(steps, total)

            assertEquals(total, shares.values.sum(), "steps=$steps total=$total shares=$shares")
        }
    }

    /**
     * 예전 계산은 실행분에 `calculateTotalCost` 를 다시 적용해, 실행 스텝이 할인 임계
     * 미만이면 할인이 사라졌다. 5스텝 할인가를 내고 1스텝만 쓴 사용자가 그 스텝을
     * 정가로 물어냈다.
     */
    @Test
    fun `할인 파이프라인의 스텝 배분은 정가보다 싸다`() {
        val steps = allSteps // 5스텝 → 할인 적용
        val total = AiPipelineStep.calculateTotalCost(steps)
        assertTrue(total < steps.sumOf { it.creditCost }, "할인이 적용되지 않았다")

        val shares = AiPipelineSettlement.distribute(steps, total)

        shares.forEach { (step, share) ->
            assertTrue(
                share <= step.creditCost,
                "$step 배분($share)이 정가(${step.creditCost})보다 비싸다",
            )
        }
    }

    @Test
    fun `배분은 raw cost 가 큰 스텝에 더 많이 준다`() {
        val steps = listOf(AiPipelineStep.STT, AiPipelineStep.GENERATE_HASHTAGS)
        val total = AiPipelineStep.calculateTotalCost(steps)

        val shares = AiPipelineSettlement.distribute(steps, total)

        assertTrue(
            shares.getValue(AiPipelineStep.STT) > shares.getValue(AiPipelineStep.GENERATE_HASHTAGS),
            "raw cost 10 인 STT 가 3 인 해시태그보다 적게 배분됐다: $shares",
        )
    }

    @Test
    fun `같은 입력은 같은 배분을 낸다`() {
        val steps = allSteps
        val total = AiPipelineStep.calculateTotalCost(steps)

        val first = AiPipelineSettlement.distribute(steps, total)
        val second = AiPipelineSettlement.distribute(steps.reversed(), total)

        assertEquals(first, second, "스텝 순서만 바뀌었는데 배분이 달라졌다")
    }

    /* ---- 미사용 금액 ---- */

    @Test
    fun `COMPLETED 와 RUNNING 은 소비로 보고 환불하지 않는다`() {
        val steps = listOf(AiPipelineStep.STT, AiPipelineStep.ANALYZE_SCRIPT)
        val total = AiPipelineStep.calculateTotalCost(steps)
        val statuses = mapOf(
            AiPipelineStep.STT to PipelineStepStatus.COMPLETED,
            AiPipelineStep.ANALYZE_SCRIPT to PipelineStepStatus.RUNNING,
        )

        assertEquals(0, AiPipelineSettlement.unusedAmount(steps, statuses, total))
    }

    /** 외부 호출조차 없었던 스텝이다. 돌려주지 않으면 부르지 않은 AI 에 과금한 것이 된다. */
    @Test
    fun `SKIPPED 와 PENDING 과 FAILED 는 환불 대상이다`() {
        val steps = allSteps
        val total = AiPipelineStep.calculateTotalCost(steps)
        val shares = AiPipelineSettlement.distribute(steps, total)
        val statuses = mapOf(
            AiPipelineStep.STT to PipelineStepStatus.FAILED,
            AiPipelineStep.ANALYZE_SCRIPT to PipelineStepStatus.SKIPPED,
            AiPipelineStep.GENERATE_META to PipelineStepStatus.SKIPPED,
            AiPipelineStep.GENERATE_HASHTAGS to PipelineStepStatus.COMPLETED,
            AiPipelineStep.SUGGEST_SCHEDULE to PipelineStepStatus.PENDING,
        )

        val expected = shares.getValue(AiPipelineStep.STT) +
            shares.getValue(AiPipelineStep.ANALYZE_SCRIPT) +
            shares.getValue(AiPipelineStep.GENERATE_META) +
            shares.getValue(AiPipelineStep.SUGGEST_SCHEDULE)

        assertEquals(expected, AiPipelineSettlement.unusedAmount(steps, statuses, total))
    }

    /**
     * 배분은 **서로 다른 스텝**만 다룬다.
     *
     * 도메인 모델의 상태·결과·오류가 전부 스텝을 키로 하는 맵이라 중복을 표현할 수 없다.
     * 반면 선차감([AiPipelineStep.calculateTotalCost])은 리스트를 그대로 합산해 중복을 두 번
     * 센다. 그 어긋남은 `AiPipelineUseCase` 가 중복 입력을 거절해 막으며, 여기서는 배분이
     * distinct 기준이라는 계약을 고정한다 — 우연이 아니라 의도임을 남긴다.
     */
    @Test
    fun `중복 스텝은 하나로 보고 배분한다`() {
        val steps = listOf(AiPipelineStep.STT, AiPipelineStep.STT, AiPipelineStep.ANALYZE_SCRIPT)
        val distinctTotal = AiPipelineStep.calculateTotalCost(steps.distinct())

        val shares = AiPipelineSettlement.distribute(steps, distinctTotal)

        assertEquals(2, shares.size, "중복 스텝이 따로 배분됐다: $shares")
        assertEquals(distinctTotal, shares.values.sum())
    }

    @Test
    fun `전 스텝 미실행이면 전액을 돌려준다`() {
        val steps = allSteps
        val total = AiPipelineStep.calculateTotalCost(steps)
        val statuses = steps.associateWith { PipelineStepStatus.PENDING }

        assertEquals(total, AiPipelineSettlement.unusedAmount(steps, statuses, total))
    }
}
