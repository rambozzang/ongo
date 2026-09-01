package com.ongo.domain.analytics

/**
 * 두 기간 사이의 **증감률**.
 *
 * ## 왜 별도 함수인가
 *
 * 예전에는 저장소 안에 이렇게 들어 있었다.
 *
 * ```
 * if (previous == 0L) { if (current > 0) 100.0 else 0.0 }
 * ```
 *
 * `previous = 0` 에서 증감률은 **정의되지 않는다** — 0 으로 나누기 때문이다. 그 자리에
 * `100.0` 이라는 임의의 숫자를 넣어 측정값처럼 내려보냈다. 그래서 첫 주에 조회수 50,000 을
 * 낸 채널과 100 → 200 으로 는 채널이 대시보드에서 **똑같이 "▲100%"** 로 보였다.
 * 서로 다른 사실이 같은 숫자가 된다.
 *
 * `0 → 0` 도 `0.0` 이 되어 "변화 없음"으로 표시됐다. 비교할 기간에 데이터가 없다는 사실과
 * 실제로 변하지 않았다는 사실이 구분되지 않았다.
 *
 * 이 판정은 DB 없이도 검증할 수 있어야 해서 도메인의 순수 함수로 뺐다.
 */
object MetricChange {

    /**
     * 이전 기간 대비 증감률(%).
     *
     * @return 이전 기간이 0 이면 **`null`** — 비율을 계산할 기준이 없다는 뜻이다.
     *   호출자는 이것을 0 이나 100 으로 채우지 말고 "비교 불가"로 표시해야 한다.
     */
    fun percentChange(previous: Long, current: Long): Double? {
        if (previous == 0L) return null
        return ((current - previous).toDouble() / previous.toDouble()) * 100.0
    }

    /**
     * 사람이 읽을 문자열. **단위(`%`)를 값이 직접 들고 있다.**
     *
     * 호출부 템플릿에 `{viewsChange}%` 처럼 `%` 를 붙여 두면, 비교 불가일 때
     * `비교 불가%` 라는 문장이 만들어진다. 그래서 단위를 여기서 붙인다.
     *
     * `String.format("%.1f", null)` 은 **문자열 `"null"`** 을 만든다. 그 값이 AI 프롬프트에
     * 그대로 들어가면 모델이 그것을 수치로 읽고 없는 추세를 지어낸다.
     */
    fun describePercent(value: Double?): String =
        value?.let { String.format("%.1f%%", it) } ?: UNAVAILABLE_TEXT

    /**
     * 절대 수치를 사람이 읽는 문자열로. **`null` 은 "재지 않았다" 이며 0 이 아니다.**
     *
     * 구독 증가처럼 비율이 아닌 값에 쓴다. `Long?.toString()` 은 문자열 `"null"` 을 만들고,
     * 그 값이 AI 프롬프트에 그대로 들어가면 모델이 없는 사실을 지어낸다 — 실제로
     * `StrategyCoachUseCase`·`WeeklyDigestUseCase`·`GenerateReportUseCase` 가 그렇게
     * `"{subscriberChange}" = "null"` 을 보내고 있었다.
     */
    fun describeCount(value: Long?): String = value?.toString() ?: NOT_MEASURED_TEXT

    /** 비교 기준이 없을 때의 표현. 숫자가 아니라는 것이 문장으로 드러나야 한다. */
    const val UNAVAILABLE_TEXT = "비교 불가(이전 기간 데이터 없음)"

    /**
     * 측정 자체가 없을 때의 표현. [UNAVAILABLE_TEXT] 와 구분한다 — 저쪽은 "비교할 이전
     * 기간이 없다" 이고, 이쪽은 "그 지표를 수집하는 곳이 없다" 이다.
     */
    const val NOT_MEASURED_TEXT = "측정 불가(수집하는 플랫폼 없음)"
}
