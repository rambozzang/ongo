package com.ongo.domain.ai

enum class AiPipelineStep(
    val displayName: String,
    val creditCost: Int,
) {
    STT("음성 텍스트 변환", 10),
    ANALYZE_SCRIPT("대본 분석", 5),
    GENERATE_META("메타데이터 생성", 5),
    GENERATE_HASHTAGS("해시태그 생성", 3),
    SUGGEST_SCHEDULE("업로드 시간 추천", 3),
    ;

    companion object {
        const val PIPELINE_DISCOUNT_RATE = 0.20
        const val MIN_STEPS_FOR_DISCOUNT = 3

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
