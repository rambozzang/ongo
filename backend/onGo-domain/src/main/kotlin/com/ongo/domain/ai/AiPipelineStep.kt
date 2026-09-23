package com.ongo.domain.ai

enum class AiPipelineStep(
    val displayName: String,
    val creditCost: Int,
) {
    /**
     * **10분 이하 원본 1회 전사**의 값이다. 파이프라인은 단계별 고정 가격을 비례 배분해 정산하므로 길이에 따라
     * 가격을 바꾸지 않는다. 대신 AiPipelineUseCase 가 10분 넘는 원본의 STT 단계를 시작 전에 거절하고, 할인 뒤
     * 몫(52 × 0.8 = 41)이 설정 모델의 10분 전사 크레딧(whisper-1 기준 41)을 덮지 못하면 역시 거절한다.
     */
    STT("음성 텍스트 변환 (10분 이하)", 52),
    ANALYZE_SCRIPT("대본 분석", 5),
    GENERATE_META("메타데이터 생성", 5),
    GENERATE_HASHTAGS("해시태그 생성", 3),
    SUGGEST_SCHEDULE("업로드 시간 추천", 3),
    ;

    companion object {
        const val PIPELINE_DISCOUNT_RATE = 0.20
        const val MIN_STEPS_FOR_DISCOUNT = 3

        /** 파이프라인 STT 가 받는 원본 길이 상한. 더 긴 영상은 개별 STT(길이 비례)나 쇼츠를 쓴다. */
        const val STT_MAX_SOURCE_MS: Long = 10 * 60 * 1000L

        /** 할인까지 적용했을 때 STT 단계에 배분되는 최소 크레딧. 원가를 덮는지 판단하는 값이다. */
        fun sttShareAfterDiscount(): Int = (STT.creditCost * (1 - PIPELINE_DISCOUNT_RATE)).toInt()

        fun calculateTotalCost(steps: List<AiPipelineStep>): Int {
            val rawCost = steps.sumOf { it.creditCost }
            return if (steps.size >= MIN_STEPS_FOR_DISCOUNT) {
                (rawCost * (1 - PIPELINE_DISCOUNT_RATE)).toInt()
            } else {
                rawCost
            }
        }
    }
}
