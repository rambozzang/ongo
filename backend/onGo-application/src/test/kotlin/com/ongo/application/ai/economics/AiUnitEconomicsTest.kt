package com.ongo.application.ai.economics

import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.AiProvider
import com.ongo.common.enums.PlanType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * **"무조건 수익" 을 숫자로 고정한다.** 단가표·크레딧·요금제 중 하나라도 바뀌어 이익이 깨지면 여기서 멈춘다.
 *
 * 집행은 `AiCostGuardAdvisor` 가 호출마다 하고, 이 테스트는 "그 집행 아래에서 모든 기능이 실제로 쓸 만한 답을
 * 낼 수 있는가" 와 "요금제 사용자가 포함된 것을 전부 써도 이익인가" 를 본다.
 */
class AiUnitEconomicsTest {

    private val economics = AiUnitEconomics()

    /** 평범한 요청 — 한국어 1,500자(프롬프트 + 사용자 입력). 3바이트/글자. */
    private val referenceInputTokens = economics.estimateInputTokens(1_500L * 3)

    @Test
    @DisplayName("크레딧 1개의 원가 예산은 가장 싼 팩의 순매출의 절반이다")
    fun budgetPerCredit() {
        // 비즈니스 팩 ₩49,900 / 10,000 = ₩4.99 → 부가세·PG 수수료 제외 ₩4.40 → 절반 ₩2.20
        assertEquals(4.40, economics.netKrwPerCredit, 0.01)
        assertEquals(2.20, economics.budgetKrwPerCredit, 0.01)
    }

    /**
     * 가장 작은 기능(2크레딧)부터 모든 기능이, 제공하는 모든 제공자에서 평범한 입력으로 쓸 만한 답을 살 수 있어야 한다.
     * 못 사면 가로채기가 호출을 거부한다 — 이익은 지키지만 기능이 죽는다. 그래서 여기서 막는다.
     */
    @Test
    @DisplayName("모든 AI 기능이 제공 중인 모든 모델에서 원가 예산 안에 쓸 만한 답을 낸다")
    fun everyFeatureAffordsAUsefulAnswer() {
        val llmFeatures = AiFeature.entries.filter { it.creditCost > 0 && it != AiFeature.STT }
        for (provider in AiProvider.OFFERED) {
            for (feature in llmFeatures) {
                val output = economics.affordableOutputTokens(provider, referenceInputTokens, economics.budgetKrw(feature.creditCost))
                assertTrue(
                    output >= AiUnitEconomics.MIN_USEFUL_OUTPUT_TOKENS,
                    "$feature(${feature.creditCost}크레딧)가 $provider 에서 출력 ${output}토큰밖에 못 산다",
                )
            }
        }
    }

    /** 비싼 모델을 제공 목록에 다시 넣으면 작은 기능이 죽는다는 것을 숫자로 남긴다. */
    @Test
    @DisplayName("Claude 는 2크레딧 기능의 예산으로 평범한 요청을 처리할 수 없다 — 제공 목록에서 뺀 이유")
    fun premiumProvidersCannotServeSmallFeatures() {
        val output = economics.affordableOutputTokens(AiProvider.CLAUDE, referenceInputTokens, economics.budgetKrw(2))
        assertTrue(output < AiUnitEconomics.MIN_USEFUL_OUTPUT_TOKENS, "Claude 가 ${output}토큰을 산다")
        assertTrue(AiProvider.CLAUDE !in AiProvider.OFFERED && AiProvider.GEMINI !in AiProvider.OFFERED && AiProvider.OPENAI !in AiProvider.OFFERED)
    }

    /** 요금제 사용자가 포함 크레딧·저장공간·자동 감정 분석을 **전부 다 써도** 순매출의 절반을 넘지 않는다. */
    @Test
    @DisplayName("유료 요금제는 포함된 것을 전부 써도 원가가 순매출의 절반 이하다")
    fun paidPlansStayProfitableAtFullUse() {
        for (plan in PlanType.entries.filter { it != PlanType.FREE }) {
            val worst = economics.planWorstMonthlyCostKrw(plan)
            val net = economics.planMonthlyNetKrw(plan)
            assertTrue(
                worst <= net * economics.targetCostRatio,
                "$plan: 최악 원가 ₩${worst.toInt()} > 순매출 ₩${net.toInt()} × ${economics.targetCostRatio}",
            )
        }
    }

    /** 무료 사용자는 매출이 없다. 대신 한 명이 만들 수 있는 원가에 상한이 있어야 한다. */
    @Test
    @DisplayName("무료 사용자 한 명의 월 최대 원가는 ₩100 이하다")
    fun freeUserCostIsBounded() {
        val worst = economics.planWorstMonthlyCostKrw(PlanType.FREE)
        assertTrue(worst <= 100.0, "무료 사용자 최악 원가 ₩$worst")
        assertEquals(0.0, economics.sentimentMonthlyCapKrw(PlanType.FREE))
    }

    /** 음성 인식은 모델 단가에서 크레딧이 나온다. 싼 모델로 바꾸면 가격이 따라 내려가고, 어느 쪽이든 이익이다. */
    @Test
    @DisplayName("음성 인식 크레딧은 모델 원가를 덮고, 싼 모델이면 더 싸다")
    fun sttCreditsCoverModelCost() {
        for (model in listOf("whisper-1", "gpt-4o-mini-transcribe", "gpt-transcribe", "unknown-model")) {
            val credits = economics.sttCreditsPer10Minutes(model)
            val cost = economics.sttKrwPerMinute(model) * 10
            assertTrue(economics.budgetKrw(credits) >= cost, "$model: ${credits}크레딧 예산 < 원가 ₩$cost")
        }
        assertTrue(economics.sttCreditsPer10Minutes("gpt-4o-mini-transcribe") < economics.sttCreditsPer10Minutes("whisper-1"))
        // 모르는 모델은 알려진 최고가의 두 배로 본다 — 싸게 잘못 매기지 않는다.
        assertTrue(economics.sttCreditsPer10Minutes("unknown-model") > economics.sttCreditsPer10Minutes("whisper-1"))
    }

    /** 파이프라인은 단계 가격이 고정이다. 할인 뒤 STT 몫이 기본 모델의 10분 전사 크레딧을 덮어야 한다. */
    @Test
    @DisplayName("AI 파이프라인의 STT 단계는 할인 뒤에도 10분 전사 원가를 덮는다")
    fun pipelineSttCoversTenMinutes() {
        assertTrue(
            com.ongo.domain.ai.AiPipelineStep.sttShareAfterDiscount() >= economics.sttCreditsPer10Minutes("whisper-1"),
            "파이프라인 STT 몫 ${com.ongo.domain.ai.AiPipelineStep.sttShareAfterDiscount()} < 10분 전사 ${economics.sttCreditsPer10Minutes("whisper-1")}",
        )
    }

    @Test
    @DisplayName("음성 합성 크레딧은 원가를 덮는다")
    fun ttsCreditsCoverCost() {
        val credits = economics.ttsCreditsPer1000Chars()
        val cost = 1000.0 / AiPriceBook.TTS_MIN_CHARS_PER_MINUTE * AiPriceBook.TTS_USD_PER_MINUTE * economics.krwPerUsd
        assertTrue(economics.budgetKrw(credits) >= cost)
    }

    /** 자동 감정 분석 한 배치(댓글 25개 × 200자)가 배치 예산 안에서 결과를 낼 수 있어야 한다. */
    @Test
    @DisplayName("감정 분석 한 배치가 배치 예산 안에서 처리된다")
    fun sentimentBatchFitsBudget() {
        val input = economics.estimateInputTokens(AiUnitEconomics.SENTIMENT_MAX_COMMENTS_PER_BATCH * 200L * 3 + 1_500)
        for (provider in AiProvider.OFFERED) {
            val output = economics.affordableOutputTokens(provider, input, AiUnitEconomics.SENTIMENT_BATCH_BUDGET_KRW)
            assertTrue(output >= AiUnitEconomics.MIN_USEFUL_OUTPUT_TOKENS, "$provider: 감정 분석 배치 출력 $output")
        }
    }

    @Test
    @DisplayName("환율이 오르면 원가가 오르고, 목표 원가율 밖의 설정은 거부한다")
    fun configurationGuards() {
        val expensive = AiUnitEconomics(krwPerUsd = 2000.0)
        assertTrue(expensive.llmCostKrw(AiProvider.QWEN, 1000, 1000) > economics.llmCostKrw(AiProvider.QWEN, 1000, 1000))
        assertTrue(runCatching { AiUnitEconomics(targetCostRatio = 1.2) }.isFailure)
        assertTrue(runCatching { AiUnitEconomics(krwPerUsd = 0.0) }.isFailure)
    }
}
