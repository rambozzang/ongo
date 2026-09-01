package com.ongo.application.analytics

import java.time.LocalDate

/**
 * 한 업로드에 대해 **이번 실행에서 어떤 날짜를 조회할지** 정한다.
 *
 * ## 왜 순수 함수인가
 *
 * 날짜·호출 수 계산이 스케줄러 안에 섞여 있으면 "중간 날짜가 빠졌을 때 다음 실행이
 * 그것을 다시 고르는가" 를 확인할 방법이 로그밖에 없다. 여기로 떼어 내면 그 계약을
 * 테스트로 고정할 수 있다.
 *
 * ## 고치려는 결함
 *
 * 예전에는 `MAX(date) + 1` 부터 앞으로만 훑었다(`findLatestDateByVideoUploadId`).
 * 그래서 날짜 D 조회가 실패했는데 같은 실행의 뒤쪽(예: 오늘) 조회가 성공해 D+1 이상이
 * 저장되면, 다음 실행의 시작점이 D 를 지나간다 — **D 는 영원히 다시 조회되지 않는다.**
 *
 * 수익은 그 위에 얹혀 있어 피해가 겹친다. `updateRevenue` 는 이미 있는 분석 행만
 * 갱신하므로(행을 만들지 않는다), 분석 행이 영원히 없는 날짜의 수익도 영원히 유실된다.
 *
 * ## 규칙
 *
 * - 최근 [GAP_WINDOW_DAYS] 일(어제까지)만 본다. 수익 조회 창과 같은 범위이며, 그 밖의
 *   과거를 무한히 다시 묻지 않는다.
 * - **이미 수집한 구간 안의 빈 날짜**를 다시 고른다. 최신일보다 앞서도 고른다.
 * - 아직 한 번도 수집하지 않은 업로드는 종전대로 **어제 하루만** 본다. 게시 전 날짜를
 *   30일치 두드리면 호출만 낭비된다.
 * - 한 실행·업로드당 최대 [MAX_PROVIDER_CALLS_PER_UPLOAD] 회. 최신 누락부터 채운다 —
 *   수익 창에 가까운 날짜가 더 급하고 제공자 쪽에도 더 잘 남아 있다.
 */
object AnalyticsSyncWindow {

    /** 되짚어 볼 최근 기간(일). 수익 재조회 창과 같다. */
    const val GAP_WINDOW_DAYS = 30

    /**
     * 한 실행·업로드당 과거 날짜 조회 상한. 종전 백필 상한(7)을 그대로 지킨다.
     *
     * **오늘 조회는 여기에 포함되지 않는다.** 오늘은 매 실행마다 따로 한 번 더 부른다 —
     * 종전과 같은 정책이며, 그래서 업로드당 최대 호출은 7 + 1 이다.
     */
    const val MAX_PROVIDER_CALLS_PER_UPLOAD = 7

    /**
     * @param today 실행 시각의 날짜.
     * @param latestDate `MAX(date)`. 한 번도 수집한 적 없으면 null.
     *   기존 쿼리 계약을 그대로 쓴다.
     * @param existingDates 최근 창 안에 **실제로 존재하는** 행의 날짜.
     * @return 조회할 날짜. 최신순, 최대 [MAX_PROVIDER_CALLS_PER_UPLOAD] 개.
     */
    fun datesToSync(
        today: LocalDate,
        latestDate: LocalDate?,
        existingDates: Set<LocalDate>,
    ): List<LocalDate> {
        val windowEnd = today.minusDays(1)
        val windowStart = today.minusDays(GAP_WINDOW_DAYS.toLong())
        if (windowEnd.isBefore(windowStart)) return emptyList()

        val knownInWindow = existingDates.filter { !it.isBefore(windowStart) && !it.isAfter(windowEnd) }

        val scanStart = when {
            // 수집 이력이 창 안에 있다 — 그 구간의 빈 날짜부터 어제까지 훑는다.
            knownInWindow.isNotEmpty() -> knownInWindow.min()
            // 수집한 적은 있는데 전부 창보다 오래됐다 — 창 전체가 비었으므로 처음부터.
            latestDate != null -> windowStart
            // 한 번도 수집한 적 없다. 게시 전일 수 있으니 어제 하루만 시도한다.
            else -> windowEnd
        }

        return generateSequence(scanStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(windowEnd) }
            .filter { it !in existingDates }
            .toList()
            // 최신 누락부터. 수익 창에 가까운 날짜가 더 급하다.
            .sortedDescending()
            .take(MAX_PROVIDER_CALLS_PER_UPLOAD)
    }
}
