package com.ongo.domain.accountdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 배치용 헬퍼가 **어떤 예외에도 쓰기를 허용하지 않는지** 고정한다.
 *
 * 배치가 `AccountFrozenException` 만 잡으면, 가드 구현이 바뀌어 다른 예외를 던지는 순간
 * 그게 위로 전파되고 호출자가 어떻게 다루느냐에 따라 쓰기가 허용될 수 있다.
 * 예상치 못한 예외를 "쓰기 허용"으로 해석하는 경로를 아예 없애려는 것이다.
 */
class CanWriteHelperTest {

    private fun guard(behavior: (Long) -> Unit) = object : UserWriteGuard {
        override fun requireWritable(userId: Long, origin: WriteOrigin, systemPath: String?) =
            behavior(userId)
    }

    @Test
    @DisplayName("통과하면 true")
    fun writableReturnsTrue() {
        assertTrue(guard { }.canWrite(1L))
    }

    @Test
    @DisplayName("어떤 예외가 나든 false — 종류를 가리지 않는다")
    fun anyExceptionMeansNotWritable() {
        val exceptions = listOf(
            IllegalStateException("DB 오류"),
            IllegalArgumentException("잘못된 인자"),
            RuntimeException("예상 못 한 것"),
            NullPointerException("널"),
        )

        exceptions.forEach { e ->
            assertFalse(guard { throw e }.canWrite(1L)) {
                "${e.javaClass.simpleName} 이 쓰기를 허용했다"
            }
        }
    }

    @Test
    @DisplayName("막힌 이유를 콜백으로 받을 수 있다")
    fun blockedReasonIsReportedToCallback() {
        var seen: Exception? = null

        val result = guard { throw IllegalStateException("상태 조회 실패") }
            .canWrite(1L) { seen = it }

        assertFalse(result)
        assertTrue(seen is IllegalStateException) { "콜백이 예외를 못 받았다: $seen" }
        assertTrue(seen?.message == "상태 조회 실패")
    }

    @Test
    @DisplayName("콜백을 주지 않아도 예외가 새지 않는다")
    fun exceptionsNeverEscapeWithoutCallback() {
        // 배치 루프에서 헬퍼가 예외를 흘리면 항목 하나 때문에 배치 전체가 죽는다.
        assertFalse(guard { throw RuntimeException("펑") }.canWrite(1L))
    }
}
