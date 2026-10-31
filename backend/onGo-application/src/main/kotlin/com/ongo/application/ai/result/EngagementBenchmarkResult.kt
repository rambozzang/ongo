package com.ongo.application.ai.result

/**
 * 참여율 진단의 AI 응답. **AI 몫은 강점·개선점 문장뿐이다.**
 *
 * 숫자 필드는 예전 형식과 호환을 위해 남겨 두지만 쓰지 않는다. 내 참여율은 서버가 실측으로 계산하고,
 * 업계 평균·백분위·등급은 볼 데이터가 없어 없앴다(모델이 지어낸 값이었다).
 */
data class EngagementBenchmarkResult(
    val myEngagementRate: Double? = null,
    val categoryAverage: Double? = null,
    val percentile: Int? = null,
    val platformBenchmarks: List<PlatformBenchmark> = emptyList(),
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
) {
    data class PlatformBenchmark(
        val platform: String,
        val myRate: Double? = null,
        val categoryAverage: Double? = null,
        val rating: String? = null,
    )
}
