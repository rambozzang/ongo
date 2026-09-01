package com.ongo.domain.ugc.analytics

import java.time.LocalDateTime

/** 이 스냅샷의 숫자가 어디에서 왔는가. **0 을 어떻게 읽을지가 여기서 갈린다.** */
enum class MetricSnapshotSource {
    /**
     * 플랫폼 API 동기화. 플랫폼이 주지 않는 지표는 [MetricSnapshot.unavailableMetrics] 에
     * 담기고, 그 자리의 0 은 측정값이 아니다.
     */
    PLATFORM_SYNC,

    /**
     * 운영자가 직접 입력. **여기서는 0 도 측정값이다** — 사람이 0 이라고 적은 것이다.
     *
     * 플랫폼 이름만 보고 값을 버리면 이 백필까지 함께 사라진다. 그래서 가용성은
     * 플랫폼이 아니라 **스냅샷의 속성**이다.
     */
    MANUAL,

    /**
     * V110 이전에 저장돼 출처를 알 수 없는 행.
     *
     * 그 시절에는 동기화와 수동 입력이 같은 모양으로 저장돼 구분할 수단이 없다.
     * 0 이 측정 결과인지 미수집인지 알 수 없으므로 **0 만 미가용으로 본다** —
     * 0 이 아닌 값은 누군가 실제로 관측한 것이 분명하므로 그대로 살린다.
     */
    UNKNOWN,
}

/**
 * 게시물 지표 스냅샷.
 *
 * ## 0 은 두 가지 뜻이었다
 *
 * 예전에는 `views/likes/comments/shares` 가 전부 `Long = 0` 이었다. 그런데 Facebook·
 * WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 **API 로 주지 않는다.** 동기화
 * 스케줄러는 그 자리에 0 을 넣어 저장했고, 캠페인 분석은 플랫폼 구분 없이 합산했다.
 *
 * 그 합계가 브랜드 성과와 **보상 판단** 화면에 그대로 올라간다. Facebook 중심 캠페인은
 * 공유 성과가 구조적으로 0 으로 보고됐다 — 실제로 공유가 없어서가 아니라 물어보지
 * 않았기 때문인데, 화면은 둘을 구분하지 못했다.
 *
 * 이제 스냅샷이 **자기가 무엇을 측정하지 못했는지** 스스로 들고 다닌다.
 */
data class MetricSnapshot(
    val id: Long? = null,
    val campaignPostId: Long,
    val capturedAt: LocalDateTime,
    val views: Long = 0,
    val likes: Long = 0,
    val comments: Long = 0,
    val shares: Long = 0,
    /** 이 숫자들의 출처. 같은 0 이라도 출처에 따라 뜻이 다르다. */
    val source: MetricSnapshotSource = MetricSnapshotSource.MANUAL,
    /**
     * 이 스냅샷이 **측정하지 못한** 지표 이름들([VIEWS]·[LIKES]·[COMMENTS]·[SHARES]).
     *
     * 여기 담긴 지표의 값은 자리 채우기이며 합산·비교에 넣으면 안 된다.
     */
    val unavailableMetrics: Set<String> = emptySet(),
) {
    /**
     * [metric] 이 실제로 측정된 값인가.
     *
     * [MetricSnapshotSource.UNKNOWN] 은 출처를 모르므로 **0 만** 미가용으로 본다.
     * 0 이 아닌 값은 누군가 관측한 것이 분명하다.
     */
    fun measured(metric: String): Boolean = when (source) {
        MetricSnapshotSource.UNKNOWN -> valueOf(metric) != 0L
        else -> metric !in unavailableMetrics
    }

    /** 측정된 값. 측정하지 못했으면 `null` — 0 으로 대체하지 않는다. */
    fun measuredValue(metric: String): Long? = if (measured(metric)) valueOf(metric) else null

    private fun valueOf(metric: String): Long = when (metric) {
        VIEWS -> views
        LIKES -> likes
        COMMENTS -> comments
        SHARES -> shares
        else -> throw IllegalArgumentException("알 수 없는 지표입니다: $metric")
    }

    companion object {
        const val VIEWS = "views"
        const val LIKES = "likes"
        const val COMMENTS = "comments"
        const val SHARES = "shares"

        /** 스냅샷이 담는 지표 전체. 집계·응답이 같은 목록을 돈다. */
        val ALL_METRICS = listOf(VIEWS, LIKES, COMMENTS, SHARES)
    }
}
