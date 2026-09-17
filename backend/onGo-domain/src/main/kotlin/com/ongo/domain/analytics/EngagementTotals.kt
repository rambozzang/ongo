package com.ongo.domain.analytics

/**
 * 어느 시점의 **평생 누적** 참여 지표 스냅샷.
 *
 * 누적 플랫폼(YouTube 를 뺀 전부)이 돌려주는 값이 이것이다. 그대로 저장하면 기간 합계가
 * 부풀기 때문에, 직전 스냅샷과 [deltaFrom] 으로 차분해 증분을 만들어 저장한다.
 */
data class EngagementTotals(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
) {
    /**
     * [previous] 이후의 증가분.
     *
     * ## 음수를 0 으로 막는 이유
     *
     * 평생 누적 카운터는 원칙적으로 줄지 않지만, 실제로는 줄어든다.
     *
     *  - 영상에 달린 댓글이나 좋아요가 삭제된다
     *  - 플랫폼이 스팸·봇 조회를 사후에 걷어낸다
     *  - 여러 API 서버 사이의 복제 지연으로 더 낮은 값이 잠깐 보인다
     *
     * 이때 차분은 음수가 된다. 음수를 그대로 저장하면 `analytics_daily` 의
     * `CHECK (views >= 0)` 에 걸려 그 날 동기화가 통째로 실패하고, 실패가 반복되면
     * 그 영상의 수집이 영구히 멈춘다.
     *
     * **그래서 0 으로 자른다.** 이것은 값을 지어내는 것이 아니라 "이 구간에 증가는
     * 없었다" 는 관측을 기록하는 것이다. 감소분 자체를 보존하려면 별도의 부호 있는 칸이
     * 필요한데, 지금 화면 어디에도 "조회수가 줄었다" 를 보여주는 자리가 없다.
     * 스냅샷 원본은 `*_total` 에 그대로 남으므로 나중에 복원할 수 있다.
     */
    fun deltaFrom(previous: EngagementTotals): EngagementDelta =
        EngagementDelta(
            views = increase(previous.views, views),
            likes = increase(previous.likes, likes),
            comments = increase(previous.comments, comments),
            shares = increase(previous.shares, shares),
        )

    private fun increase(before: Long, after: Long): Long = (after - before).coerceAtLeast(0)
}

/**
 * 한 주기 동안의 증가분. `analytics_daily` 의 참여 지표 칸에 들어가는 값이다.
 *
 * 컬럼이 `INTEGER` 라 [toIntClamped] 로 좁혀 넣는다. 평생 누적이 `Long` 인데 증분이
 * `Int` 를 넘는 경우는 하루에 21억 조회가 늘어난 때뿐이지만, **첫 차분이 기준선 없이
 * 계산되는 실수**가 생기면 평생값 전체가 증분 자리에 들어와 넘칠 수 있다.
 * 그때 조용히 음수로 뒤집히는 것(Int 오버플로)보다 상한에서 멈추는 편이 낫다.
 */
data class EngagementDelta(
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
) {
    val viewsInt: Int get() = toIntClamped(views)
    val likesInt: Int get() = toIntClamped(likes)
    val commentsInt: Int get() = toIntClamped(comments)
    val sharesInt: Int get() = toIntClamped(shares)

    private fun toIntClamped(value: Long): Int =
        value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    companion object {
        /** 기준선이 없어 증분을 낼 수 없을 때. [EngagementBasis.BASELINE] 과 함께 쓴다. */
        val NONE = EngagementDelta(0, 0, 0, 0)
    }
}
