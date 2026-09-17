package com.ongo.domain.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 평생 누적 스냅샷의 차분 계산을 고정한다.
 *
 * 이 계산이 틀리면 대시보드의 모든 참여 지표가 틀린다. 어느 방향으로 틀리든 치명적이다 —
 * 과하면 크리에이터가 없는 성과를 믿고, 모자라면 실제 성과가 사라진다.
 */
class EngagementTotalsTest {

    private fun totals(views: Long, likes: Long = 0, comments: Long = 0, shares: Long = 0) =
        EngagementTotals(views, likes, comments, shares)

    @Test
    fun `두 스냅샷 사이의 증가분을 낸다`() {
        val delta = totals(1_000, 80, 30, 12).deltaFrom(totals(900, 70, 25, 10))

        assertEquals(100, delta.views)
        assertEquals(10, delta.likes)
        assertEquals(5, delta.comments)
        assertEquals(2, delta.shares)
    }

    /** 변화가 없으면 0 이다. 이것은 **실측 0** 이라 합산해도 맞다. */
    @Test
    fun `변화가 없으면 0 이다`() {
        val delta = totals(1_000, 80).deltaFrom(totals(1_000, 80))
        assertEquals(0, delta.views)
        assertEquals(0, delta.likes)
    }

    /**
     * **음수를 0 으로 막는다.**
     *
     * 누적 카운터는 실제로 줄어든다 — 댓글·좋아요 삭제, 스팸 조회 사후 정정, API 서버
     * 사이의 복제 지연. 음수를 저장하면 `CHECK (views >= 0)` 에 걸려 그 날 동기화가
     * 통째로 실패하고, 실패가 반복되면 그 영상의 수집이 영구히 멈춘다.
     */
    @Test
    fun `누적이 줄어도 음수를 내지 않는다`() {
        val delta = totals(800, 60, 20, 5).deltaFrom(totals(1_000, 80, 30, 12))

        assertEquals(0, delta.views)
        assertEquals(0, delta.likes)
        assertEquals(0, delta.comments)
        assertEquals(0, delta.shares)
    }

    /** 일부만 줄어드는 경우가 흔하다 — 좋아요만 취소되고 조회수는 는다. */
    @Test
    fun `지표마다 따로 판단한다`() {
        val delta = totals(1_200, 50).deltaFrom(totals(1_000, 80))

        assertEquals(200, delta.views, "조회수는 늘었다")
        assertEquals(0, delta.likes, "좋아요는 줄었으므로 0")
    }

    // ------------------------------------------------------------ Int 변환

    /**
     * 컬럼이 `INTEGER` 라 좁혀 넣어야 한다. 기준선 없이 평생값 전체가 증분 자리에 들어오는
     * 실수가 생기면 `Int` 를 넘길 수 있는데, 그때 **조용히 음수로 뒤집히는 것**(오버플로)
     * 보다 상한에서 멈추는 편이 낫다.
     */
    @Test
    fun `Int 범위를 넘으면 상한에서 멈춘다`() {
        val huge = EngagementDelta(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)

        assertEquals(Int.MAX_VALUE, huge.viewsInt)
        assertEquals(Int.MAX_VALUE, huge.likesInt)
        assertEquals(Int.MAX_VALUE, huge.commentsInt)
        assertEquals(Int.MAX_VALUE, huge.sharesInt)
    }

    @Test
    fun `보통 값은 그대로 좁혀진다`() {
        val delta = EngagementDelta(100, 10, 5, 2)
        assertEquals(100, delta.viewsInt)
        assertEquals(10, delta.likesInt)
        assertEquals(5, delta.commentsInt)
        assertEquals(2, delta.sharesInt)
    }

    /** 기준선이 없을 때 쓰는 값. 0 이지만 [EngagementBasis.BASELINE] 과 함께여야 의미가 맞다. */
    @Test
    fun `NONE 은 모두 0 이다`() {
        assertEquals(0, EngagementDelta.NONE.viewsInt)
        assertEquals(0, EngagementDelta.NONE.likesInt)
        assertEquals(0, EngagementDelta.NONE.commentsInt)
        assertEquals(0, EngagementDelta.NONE.sharesInt)
    }
}
