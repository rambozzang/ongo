package com.ongo.application.ai.economics

import com.ongo.common.enums.AiProvider
import com.ongo.common.enums.CreditPackage
import com.ongo.common.enums.PlanType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.ceil

/**
 * **크레딧 1개가 쓸 수 있는 외부 원가의 상한**을 한 곳에서 정한다. "무조건 수익" 의 기준점이다.
 *
 * ## 기준
 *
 * - 크레딧 1개의 최저 순매출 = 가장 싼 팩의 크레딧당 가격 ÷ 1.1(부가세) × 0.97(PG 수수료).
 *   무료·요금제 포함 크레딧도 같은 예산을 쓴다 — 가장 불리한 값으로 잡아야 어떤 크레딧이든 손해가 없다.
 * - 원가 예산 = 순매출 × [targetCostRatio](기본 0.5). 나머지 절반이 서버·저장소·지원·이익이다.
 * - 환율은 보수적으로 높게(기본 ₩1,500/$) 잡는다. 환율이 오르면 원가가 오른다.
 *
 * ## 단가표를 코드에 두는 이유
 *
 * 단가가 바뀌면 크레딧 가격 판단이 바뀐다. 설정 파일에 두면 아무 검증 없이 바뀌지만, 코드에 두면
 * `AiUnitEconomicsTest` 가 "모든 기능·모든 요금제가 여전히 이익인가" 를 다시 계산해 막는다.
 */
@Component
class AiUnitEconomics(
    @param:Value("\${ai.economics.krw-per-usd:1500}")
    val krwPerUsd: Double = DEFAULT_KRW_PER_USD,
    @param:Value("\${ai.economics.target-cost-ratio:0.5}")
    val targetCostRatio: Double = DEFAULT_TARGET_COST_RATIO,
) {
    init {
        require(krwPerUsd > 0) { "ai.economics.krw-per-usd 는 양수여야 합니다" }
        require(targetCostRatio in 0.05..0.9) { "ai.economics.target-cost-ratio 는 0.05~0.9 사이여야 합니다" }
    }

    /** 크레딧 1개의 최저 순매출(원). */
    val netKrwPerCredit: Double =
        CreditPackage.entries.minOf { it.price.toDouble() / it.credits } * NET_REVENUE_FACTOR

    /** 크레딧 1개가 쓸 수 있는 외부 원가(원). */
    val budgetKrwPerCredit: Double = netKrwPerCredit * targetCostRatio

    fun budgetKrw(credits: Int): Double = credits.coerceAtLeast(0) * budgetKrwPerCredit

    // ── LLM ────────────────────────────────────────────────────────────

    fun llmPrice(provider: AiProvider): AiPriceBook.TokenPrice = AiPriceBook.llm(provider)

    fun llmCostKrw(provider: AiProvider, inputTokens: Long, outputTokens: Long): Double {
        val price = llmPrice(provider)
        return (inputTokens * price.inputUsdPerMTok + outputTokens * price.outputUsdPerMTok) / 1_000_000.0 * krwPerUsd
    }

    fun inputKrwPerToken(provider: AiProvider): Double = llmPrice(provider).inputUsdPerMTok / 1_000_000.0 * krwPerUsd
    fun outputKrwPerToken(provider: AiProvider): Double = llmPrice(provider).outputUsdPerMTok / 1_000_000.0 * krwPerUsd

    /**
     * 입력 토큰을 **많게** 추정한다: UTF-8 바이트 ÷ 2 + 여유분. 한국어는 글자당 1.5 토큰(실제는 대개 1 안팎),
     * 영어는 2글자당 1 토큰(실제는 대개 4글자당 1)으로 잡힌다. 여유분은 메시지 구분자와 구조화 출력
     * 형식 지시문처럼 집행 지점 뒤에서 붙는 입력이다.
     */
    fun estimateInputTokens(utf8Bytes: Long): Long = ceil(utf8Bytes / 2.0).toLong() + INPUT_OVERHEAD_TOKENS

    /** 남은 예산 [remainingKrw] 로 [inputTokens] 를 보낸 뒤 살 수 있는 최대 출력 토큰. 음수면 입력만으로 넘친다. */
    fun affordableOutputTokens(provider: AiProvider, inputTokens: Long, remainingKrw: Double): Long =
        kotlin.math.floor((remainingKrw - inputTokens * inputKrwPerToken(provider)) / outputKrwPerToken(provider)).toLong()

    /**
     * 입력 [inputChars] 글자(한국어 기준 3바이트)와 출력 최대 [outputTokens] 를 **제공 중인 가장 비싼 모델**로
     * 불러도 손해가 없는 크레딧. 입력이 영상 길이에 비례하는 기능(쇼츠 맥락 컷 등)이 이것으로 크레딧을 정한다.
     */
    fun creditsForLlmCall(inputChars: Long, outputTokens: Long = AI_MAX_OUTPUT_TOKENS.toLong()): Int {
        val inputTokens = estimateInputTokens(inputChars * KOREAN_BYTES_PER_CHAR)
        val worst = AiProvider.OFFERED.maxOf { llmCostKrw(it, inputTokens, outputTokens) }
        return creditsFor(worst)
    }

    /** 말하는 영상 [minutes] 분의 전사문 글자 수 상한. 빠른 한국어 발화 기준으로 넉넉히 잡는다. */
    fun transcriptCharsFor(minutes: Double): Long = ceil(minutes.coerceAtLeast(0.0) * SPOKEN_CHARS_PER_MINUTE_MAX).toLong()

    // ── 음성 인식 · 음성 합성 ─────────────────────────────────────────

    fun sttKrwPerMinute(model: String): Double = AiPriceBook.sttUsdPerMinute(model) * krwPerUsd

    /** 원본 10분마다 받아야 하는 크레딧. 모델 단가에서 나온다 — 모델을 바꾸면 가격도 따라 바뀐다. */
    fun sttCreditsPer10Minutes(model: String): Int = creditsFor(sttKrwPerMinute(model) * 10)

    /** 음성 합성 1,000자마다 받아야 하는 크레딧. */
    fun ttsCreditsPer1000Chars(): Int {
        val minutes = 1000.0 / AiPriceBook.TTS_MIN_CHARS_PER_MINUTE
        return creditsFor(minutes * AiPriceBook.TTS_USD_PER_MINUTE * krwPerUsd)
    }

    /** 원가 [costKrw] 를 목표 원가율 안에 넣는 데 필요한 최소 크레딧. */
    fun creditsFor(costKrw: Double): Int = ceil(costKrw / budgetKrwPerCredit - 1e-9).toInt().coerceAtLeast(1)

    // ── 요금제 ────────────────────────────────────────────────────────

    /** 요금제의 월 순매출(원). 연간 결제가 더 싸므로 그 월 환산으로 본다. */
    fun planMonthlyNetKrw(plan: PlanType): Double =
        minOf(plan.price.toDouble(), plan.yearlyPrice / 12.0) * NET_REVENUE_FACTOR

    /**
     * 요금제 사용자 한 명이 한 달 동안 **포함된 것을 전부 다 써도** 만들 수 있는 최대 외부 원가(원).
     * 포함 크레딧 전부 + 저장공간 가득 + 자동 감정 분석 상한.
     */
    fun planWorstMonthlyCostKrw(plan: PlanType): Double =
        budgetKrw(plan.freeCredits) +
            plan.storageGB * AiPriceBook.STORAGE_USD_PER_GB_MONTH * krwPerUsd +
            sentimentMonthlyCapKrw(plan)

    /** 자동 댓글 감정 분석이 한 사용자에게 한 달에 쓸 수 있는 최대 원가. 무료 요금제는 0 이다. */
    fun sentimentMonthlyCapKrw(plan: PlanType): Double =
        if (plan == PlanType.FREE) 0.0 else SENTIMENT_BATCHES_PER_DAY * 30 * SENTIMENT_BATCH_BUDGET_KRW

    companion object {
        const val DEFAULT_KRW_PER_USD = 1500.0
        const val DEFAULT_TARGET_COST_RATIO = 0.5

        /** 판매가 → 순매출. 부가세 10% 를 빼고 PG 수수료 3% 를 뺀다. */
        const val NET_REVENUE_FACTOR = 0.97 / 1.1

        /** 자동 감정 분석: 사용자당 하루 최대 배치 수와 배치당 원가 예산. 요금제 매출에서 낸다. */
        const val SENTIMENT_BATCHES_PER_DAY = 8
        const val SENTIMENT_BATCH_BUDGET_KRW = 10.0
        const val SENTIMENT_MAX_COMMENTS_PER_BATCH = 25

        /** 쓸 만한 답의 최소 출력. 이보다 적게밖에 못 사면 부르지 않는다. */
        const val MIN_USEFUL_OUTPUT_TOKENS = 256
        /** 채팅 호출 한 번의 출력 상한(AiCostGuardAdvisor.DEFAULT_MAX_OUTPUT_TOKENS 와 같다). */
        const val AI_MAX_OUTPUT_TOKENS = 4096
        const val KOREAN_BYTES_PER_CHAR = 3L
        /** 빠른 한국어 발화는 분당 약 300자다. 여유를 둔다. */
        const val SPOKEN_CHARS_PER_MINUTE_MAX = 350.0
        const val INPUT_OVERHEAD_TOKENS = 512
    }
}

/**
 * 외부 모델 단가. **2026-09-24 공개 가격** 기준(출처는 항목 주석). 가격이 바뀌면 여기를 고치고
 * `AiUnitEconomicsTest` 를 돌린다 — 이익이 깨지는 기능이 있으면 테스트가 막는다.
 * 모르는 값은 알려진 값보다 **비싸게** 잡는다.
 */
object AiPriceBook {
    data class TokenPrice(val model: String, val inputUsdPerMTok: Double, val outputUsdPerMTok: Double)

    /** 생각 모드를 끈 상태의 가격이다(`AiConfig` 가 끈다). 켜면 보이지 않는 추론 토큰이 출력으로 청구된다. */
    private val LLM = mapOf(
        // Alibaba Model Studio International — Qwen3.5-Plus $0.40/$2.40 (≤256K 입력)
        AiProvider.QWEN to TokenPrice("qwen3.5-plus", 0.40, 2.40),
        // Model Studio 게시가 미확인 — 제3자 목록 $0.574/$3.011 을 올려 잡음
        AiProvider.KIMI to TokenPrice("kimi-k2.5", 0.60, 3.10),
        // 제3자 목록의 >32K 구간 $0.86/$3.154 를 전 구간에 적용
        AiProvider.GLM to TokenPrice("glm-5", 0.90, 3.20),
        // Model Studio 게시가 미확인 — 타 제공자 $0.30/$1.20 의 두 배
        AiProvider.MINIMAX to TokenPrice("minimax-m2.5", 0.60, 2.40),
        // 아래 셋은 사용자 선택·대체 경로에서 제외돼 있다(ChatClientResolver). 다시 열 때를 위한 값이다.
        AiProvider.CLAUDE to TokenPrice("claude-sonnet-4-5", 3.00, 15.00),
        AiProvider.GEMINI to TokenPrice("gemini-2.5-pro", 2.50, 15.00),
        AiProvider.OPENAI to TokenPrice("gpt-5-mini", 0.25, 2.00),
    )

    fun llm(provider: AiProvider): TokenPrice = LLM.getValue(provider)

    /** 음성 인식 분당 가격(USD). OpenAI 공개가. 모르는 모델은 알려진 최고가의 두 배로 본다. */
    private val STT_USD_PER_MINUTE = mapOf(
        "whisper-1" to 0.006,
        "gpt-4o-transcribe" to 0.006,
        "gpt-transcribe" to 0.0045,
        "gpt-4o-mini-transcribe" to 0.003,
    )

    fun sttUsdPerMinute(model: String): Double =
        STT_USD_PER_MINUTE[model.trim().lowercase()] ?: (STT_USD_PER_MINUTE.values.max() * 2)

    /** gpt-4o-mini-tts 약 $0.015/분. 한국어 낭독은 분당 250자 이상 — 적게 잡을수록 보수적이다. */
    const val TTS_USD_PER_MINUTE = 0.015
    const val TTS_MIN_CHARS_PER_MINUTE = 250.0

    /** Cloudflare R2 저장 $0.015/GB·월. 내려받기(egress)는 무료다. */
    const val STORAGE_USD_PER_GB_MONTH = 0.015
}
