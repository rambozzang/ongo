package com.ongo.domain.ai

/**
 * 파이프라인 정산 계산. **순수 함수만 둔다** — DB 도, 시각도, 랜덤도 없다.
 *
 * ## 왜 배분이 필요한가
 *
 * 선예약은 [AiPipelineStep.calculateTotalCost] 로 **할인된 총액**을 한 번에 차감한다.
 * 그런데 환불은 스텝 단위로 일어난다. 예전에는 미실행분을 정가(`calculateTotalCost` 를
 * 실행분에만 다시 적용)로 계산했는데, 실행분이 할인 임계(3스텝) 미만이면 할인이 사라져
 * **실행분 원가가 부풀고 환불이 과소**해졌다. 5스텝 할인가를 내고 1스텝만 쓴 사용자가
 * 그 1스텝을 정가로 물어내는 셈이었다.
 *
 * 그래서 총액을 **raw cost 비례로 스텝에 나눠 놓고** 미사용분만 합산한다. 어떤 조합을
 * 실행해도 사용자가 실제로 낸 단가와 일치한다.
 *
 * ## 합계가 정확히 맞아야 하는 이유
 *
 * 배분 합계가 총액보다 크면 차감보다 많이 환불하고, 작으면 아무도 청구하지 않은 잔돈이
 * 남는다. 정수 나눗셈은 반드시 나머지를 만들므로 **largest remainder** 로 배정한다 —
 * 내림한 뒤 남은 몫을 소수부가 큰 스텝부터 1씩 준다.
 */
object AiPipelineSettlement {

    /** 소비된 것으로 보는 스텝 상태. 외부 호출이 일어났거나, 일어났는지 알 수 없다. */
    val CONSUMED_STATUSES = setOf(PipelineStepStatus.COMPLETED, PipelineStepStatus.RUNNING)

    /**
     * [totalCharged] 를 [steps] 의 raw cost 비례로 나눈다.
     *
     * @return 스텝별 배분액. **합계는 정확히 [totalCharged]** 다.
     */
    fun distribute(steps: List<AiPipelineStep>, totalCharged: Int): Map<AiPipelineStep, Int> {
        if (steps.isEmpty() || totalCharged <= 0) return steps.associateWith { 0 }

        val distinct = steps.distinct()
        val rawTotal = distinct.sumOf { it.creditCost }
        if (rawTotal <= 0) return distinct.associateWith { 0 }

        // 1) 내림 배분
        val base = distinct.associateWith { step ->
            (totalCharged.toLong() * step.creditCost / rawTotal).toInt()
        }
        var remaining = totalCharged - base.values.sum()
        if (remaining <= 0) return base

        /*
         * 2) 남은 몫을 소수부가 큰 순서로 1씩 나눠 준다.
         *
         * 동점이면 raw cost 가 큰 스텝, 그래도 같으면 enum 선언 순서로 정한다. 순서를
         * 고정하지 않으면 같은 입력에 다른 배분이 나와 재계산할 때마다 환불액이 흔들린다.
         */
        val ordered = distinct.sortedWith(
            compareByDescending<AiPipelineStep> { step ->
                (totalCharged.toLong() * step.creditCost) % rawTotal
            }.thenByDescending { it.creditCost }.thenBy { it.ordinal },
        )

        val result = base.toMutableMap()
        for (step in ordered) {
            if (remaining == 0) break
            result[step] = result.getValue(step) + 1
            remaining--
        }
        return result
    }

    /**
     * 아직 소비되지 않은 스텝의 배분액 합계 — 즉 **돌려줘야 할 금액**이다.
     *
     * `COMPLETED` 는 결과를 받았고, `RUNNING` 은 외부 호출이 이미 나갔을 수 있다. 둘 다
     * 소비로 본다. 취소 경로가 쓰던 이 기준을 자연 실패에도 그대로 적용해, 어느 쪽으로
     * 끝나든 사용자가 돌려받는 금액이 같게 한다.
     *
     * `FAILED` 는 결과를 주지 못했고, `SKIPPED`·`PENDING` 은 외부 호출조차 없었다.
     */
    fun unusedAmount(
        steps: List<AiPipelineStep>,
        stepStatuses: Map<AiPipelineStep, PipelineStepStatus>,
        totalCharged: Int,
    ): Int {
        val shares = distribute(steps, totalCharged)
        return steps.distinct()
            .filterNot { stepStatuses[it] in CONSUMED_STATUSES }
            .sumOf { shares[it] ?: 0 }
    }
}
