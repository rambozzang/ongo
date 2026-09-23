package com.ongo.application.ugc.shorts

import com.ongo.common.enums.AiFeature
import com.ongo.application.ai.SttCreditCalculator
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.domain.ugc.shorts.PipelineStage

/**
 * 쇼츠 파이프라인의 단계 ↔ 크레딧 기능 매핑. **단일 진실**이다.
 *
 * ## 왜 한 곳에 두는가
 *
 * 두 곳이 이 매핑을 읽는다: 실행을 만들기 전에 총액을 재는 [ShortsPipelineUseCase] 와,
 * 단계마다 실제로 차감하는 [ShortsPipelineOrchestrator]. 두 곳이 각자 목록을 들고 있으면
 * [AiFeature] 값이 바뀔 때 한쪽만 따라가고, 그러면 "충분하다"고 통과시킨 실행이 중간에
 * 크레딧 부족으로 죽는다 — 정확히 선검사가 막으려던 일이 선검사 때문에 생긴다.
 *
 * RENDER_SPEC 과 SCHEDULE 은 AI 를 부르지 않으므로 여기에 없고, 그래서 차감도 없다.
 */
object ShortsPipelineCreditRequirements {

    /** AI(크레딧) 단계 ↔ AiFeature 매핑. RENDER_SPEC/SCHEDULE 은 차감 없음. */
    val FEATURE_BY_STAGE: Map<PipelineStage, AiFeature> = mapOf(
        PipelineStage.TRANSCRIBE to AiFeature.STT,
        PipelineStage.REFRAME to AiFeature.SHORTS_REFRAME,
        PipelineStage.SEGMENT to AiFeature.SHORTS_SEGMENT,
        PipelineStage.SUBTITLE to AiFeature.SHORTS_SUBTITLE,
        PipelineStage.HOOK to AiFeature.SHORTS_HOOK,
        PipelineStage.TEMPLATE to AiFeature.SHORTS_TEMPLATE,
        PipelineStage.VALIDATE to AiFeature.SHORTS_VALIDATE,
    )

    /**
     * 전사 크레딧을 매기는 시간 단위. 이 길이를 **시작할 때마다** [AiFeature.STT] 단가가
     * 한 번씩 붙는다.
     *
     * ## 왜 `shorts.transcribe.part-seconds` 를 쓰지 않는가
     *
     * 그 설정은 전사 요청 하나에 담을 오디오 조각 크기이며 제공자 한도·메모리를 보고
     * 운영이 조정하는 **인코딩 세부사항**이다. 값이 같아 보인다고 가격을 거기에 묶으면,
     * 조각 크기를 300 초로 낮추는 순간 모든 고객의 청구가 두 배가 된다. 아무도 가격을
     * 바꾸려 하지 않았는데 가격이 바뀌는 것이다.
     *
     * 가격 단위는 가격 결정으로만 바뀌어야 하므로 여기에 따로 둔다.
     */
    const val TRANSCRIBE_BILLING_WINDOW_MS: Long = SttCreditCalculator.BILLING_WINDOW_MS

    /**
     * 원본 길이에 대한 전사(TRANSCRIBE) 크레딧.
     *
     * ## 왜 길이에 비례하는가
     *
     * 전사는 원본을 조각내 조각마다 모델을 부른다([com.ongo.application.ai.SttUseCase]).
     * 원가가 길이에 정비례하는데 정액으로 매기면, 길이 상한이 3 시간인 이상 긴 원본일수록
     * 손실이 커진다. 상한을 손익에 맞춰 조이면 롱폼→쇼츠라는 제품 자체가 성립하지 않는다.
     * 남는 답은 가격이 원가와 같은 축을 갖게 하는 것뿐이다.
     *
     * ## 기존 고객이 겪는 변화
     *
     * [TRANSCRIBE_BILLING_WINDOW_MS] 이하는 **종전과 완전히 동일한 금액**이다. 늘어나는
     * 것은 그보다 긴 원본뿐이다.
     *
     * @param sourceDurationMs 서버가 측정해 수락한 길이. `null` 은 이 과금 도입 이전에
     *   만들어진 실행이며 종전대로 정액이다 — 소급 측정하지 않는다.
     * @throws IllegalArgumentException 0 이하가 들어온 경우. 길이가 아닌 값으로 금액을
     *   계산하면 조용히 0 원이나 음수가 되므로 통과시키지 않는다.
     */
    fun transcribeCredits(sourceDurationMs: Long?, creditsPer10Minutes: Int = AiFeature.STT.creditCost): Int {
        require(creditsPer10Minutes > 0) { "10분 전사 크레딧은 양수여야 합니다" }
        if (sourceDurationMs == null) return creditsPer10Minutes
        require(sourceDurationMs > 0) {
            "원본 길이는 0보다 커야 합니다: $sourceDurationMs"
        }
        // 시작된 구간은 전부 센다. 10분 1ms 는 두 번째 구간을 시작한 것이므로 2단위다.
        val windows = (sourceDurationMs - 1) / TRANSCRIBE_BILLING_WINDOW_MS + 1
        return Math.toIntExact(windows * creditsPer10Minutes)
    }

    /**
     * 처음부터 끝까지 한 번 완주하는 데 드는 크레딧.
     *
     * 단가를 상수로 적지 않고 [FEATURE_BY_STAGE] 에서 더한다. 숫자를 박아두면 [AiFeature]
     * 의 단가가 바뀔 때 조용히 틀린 값으로 검사하게 된다.
     *
     * 재실행은 포함하지 않는다 — 재실행은 사용자가 그때 결정하는 일이고, 미리 잡아두면
     * 첫 실행조차 못 만드는 사용자가 생긴다.
     *
     * @param sourceDurationMs [transcribeCredits] 와 **같은 값**을 받아야 한다. 선검사와
     *   실제 차감이 다른 근거를 쓰면, 통과시킨 실행이 중간에 크레딧 부족으로 죽는다.
     */
    fun totalCreditsForRun(
        sourceDurationMs: Long?,
        sttCreditsPer10Minutes: Int = AiFeature.STT.creditCost,
        unitEconomics: AiUnitEconomics = AiUnitEconomics(),
        unknownDurationFallbackMs: Long = DEFAULT_MAX_SOURCE_DURATION_MS,
    ): Int =
        FEATURE_BY_STAGE.entries.sumOf { (stage, feature) ->
            creditCostForStage(stage, sourceDurationMs, sttCreditsPer10Minutes, unitEconomics, unknownDurationFallbackMs)
        }

    /** 사전 견적·단계 차감·환불·원장 금액이 모두 쓰는 단 하나의 계산. */
    fun creditCostForStage(
        stage: PipelineStage,
        sourceDurationMs: Long?,
        sttCreditsPer10Minutes: Int,
        unitEconomics: AiUnitEconomics,
        unknownDurationFallbackMs: Long = DEFAULT_MAX_SOURCE_DURATION_MS,
    ): Int {
        val feature = FEATURE_BY_STAGE[stage] ?: return 0
        return when (stage) {
            // 길이를 모르는 실행(길이 측정 도입 전 행)은 허용 최대 길이로 본다 — 10분 값으로 전사하면 긴 원본에서 손해다.
            PipelineStage.TRANSCRIBE -> transcribeCredits(sourceDurationMs ?: unknownDurationFallbackMs, sttCreditsPer10Minutes)
            PipelineStage.SEGMENT -> maxOf(
                feature.creditCost,
                unitEconomics.creditsForLlmCall(segmentInputChars(sourceDurationMs, unitEconomics, unknownDurationFallbackMs)),
            )
            PipelineStage.SUBTITLE -> maxOf(
                feature.creditCost,
                unitEconomics.creditsForLlmCall(SUBTITLE_MAX_INPUT_CHARS),
            )
            else -> feature.creditCost
        }
    }

    /** 전사문 전체 + 타임코드 상한 + 프롬프트 여유분. 맥락 컷 단계의 입력 상한이다. */
    fun segmentInputChars(
        sourceDurationMs: Long?,
        unitEconomics: AiUnitEconomics,
        unknownDurationFallbackMs: Long = DEFAULT_MAX_SOURCE_DURATION_MS,
    ): Long {
        require(unknownDurationFallbackMs > 0) { "미측정 원본 길이 상한은 양수여야 합니다" }
        val minutes = (sourceDurationMs ?: unknownDurationFallbackMs).coerceAtLeast(0).div(60_000.0)
        return Math.addExact(
            Math.addExact(unitEconomics.transcriptCharsFor(minutes), SEGMENT_TIMECODE_MAX_CHARS),
            PROMPT_OVERHEAD_CHARS,
        )
    }

    const val SEGMENT_TIMECODE_MAX_CHARS = 8_000L
    const val SUBTITLE_INPUT_MAX_CHARS = 8_000L
    const val PROMPT_OVERHEAD_CHARS = 1_500L
    const val SUBTITLE_MAX_INPUT_CHARS = SUBTITLE_INPUT_MAX_CHARS + PROMPT_OVERHEAD_CHARS
    const val DEFAULT_MAX_SOURCE_DURATION_MS = 180L * 60 * 1000
}
