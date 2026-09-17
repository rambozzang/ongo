package com.ongo.domain.analytics

/**
 * `analytics_daily` 한 행의 참여 지표(조회·좋아요·댓글·공유)가 **무엇을 뜻하는지**.
 *
 * ## 왜 필요한가
 *
 * 스케줄러는 어댑터가 준 숫자를 그 날짜 행에 넣고, 화면은 그 행들을 기간으로 `SUM` 한다.
 * 이 조합은 저장된 값이 "그 날 하루의 증가분" 일 때만 맞다.
 *
 * 그런데 어댑터 13개 중 YouTube 하나만 그 조건을 만족한다. 나머지 12개는 **평생 누적
 * 카운터**를 돌려준다. 그 값을 그대로 합산하면 30일 창에서 조회수가 약 30배가 된다.
 *
 * 금액에 [RevenueStatus] 가 필요한 이유와 같다 — 숫자만으로는 그 숫자가 무엇인지
 * 알 수 없다. 의미를 값 옆에 저장해야 화면이 올바로 해석한다.
 *
 * @see com.ongo.application.analytics.PlatformMetricAccumulation 어느 플랫폼이 누적인지
 */
enum class EngagementBasis {
    /**
     * 그 날짜 하루의 증가분. **합산해도 되는 유일한 값이다.**
     *
     * 기간값을 주는 플랫폼(YouTube)의 응답이거나, 누적 플랫폼의 값을 직전 스냅샷과
     * 차분해 만든 값이다.
     */
    INCREMENTAL,

    /**
     * 누적 플랫폼의 **첫 관측**이라 증분을 낼 수 없다.
     *
     * 비교할 이전 스냅샷이 없다. 이 행의 `views` 등은 0 이지만 그것은 "실측 0" 이 아니라
     * **기준선을 세우는 중**이라는 뜻이다. 다음 주기부터 [INCREMENTAL] 이 나온다.
     *
     * 0 을 그대로 두고 라벨로 구분하는 이유는, 이 컬럼들이 `NOT NULL CHECK (>= 0)` 이라
     * NULL 을 넣으려면 파티션 전체의 제약을 바꿔야 하기 때문이다. **판정은 라벨이 한다.**
     */
    BASELINE,

    /**
     * 차분 도입(V115) 이전에 저장된 누적 스냅샷.
     *
     * 이 행들은 평생 누적값을 "그 날의 증분" 이라고 잘못 부르며 저장한 것이다.
     * 값 자체는 `*_total` 칸으로 옮겨 보존하고, 합계에서는 뺀다.
     *
     * **차분으로 과거를 복원하지 않는다.** 백필 루프가 오늘의 누적값을 과거 날짜들에
     * 써 넣었기 때문에, 그 행들의 차분은 대부분 0 이고 하루만 거대한 값이 된다.
     * 그것은 복원이 아니라 날조다.
     */
    LEGACY_CUMULATIVE,

    ;

    /** 기간 합계에 넣어도 되는가. **여기가 유일한 판정 지점이다.** */
    val summable: Boolean get() = this == INCREMENTAL

    companion object {
        /**
         * 알 수 없는 문자열은 [LEGACY_CUMULATIVE] 로 본다 (fail-closed).
         *
         * [INCREMENTAL] 로 추정하면 정체 모를 행이 곧바로 합계에 섞여 숫자를 부풀린다.
         * 빠지는 쪽은 눈에 띄고, 섞이는 쪽은 눈에 띄지 않는다.
         */
        fun from(value: String?): EngagementBasis =
            entries.firstOrNull { it.name == value } ?: LEGACY_CUMULATIVE

        /** 합산 대상 라벨 전체. SQL 필터가 목록을 복사해 두지 않게 한다. */
        fun summableNames(): Set<String> = entries.filter { it.summable }.map { it.name }.toSet()
    }
}
