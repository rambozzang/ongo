package com.ongo.domain.analytics

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 광고 수익 금액·상태의 불변식.
 *
 * 여기서 통화를 잃거나 반올림이 어긋나면 화면에 몇백 배 틀린 금액이 나온다. 그리고
 * "측정 안 됨"이 "0 원"으로 굳으면 크리에이터는 광고 수익이 정말 0 이라고 읽는다.
 */
class RevenueMeasurementTest {

    // ---- micro 변환 ----

    @Test
    fun `원 단위 금액을 micro 로 정확히 바꾼다`() {
        assertEquals(0L, RevenueMeasurement.toMicro("0"))
        assertEquals(1_000_000L, RevenueMeasurement.toMicro("1"))
        assertEquals(12_340_000L, RevenueMeasurement.toMicro("12.34"))
        assertEquals(1_234_567_000_000L, RevenueMeasurement.toMicro("1234567"))
    }

    @Test
    fun `지수 표기도 읽는다`() {
        assertEquals(10_000_000L, RevenueMeasurement.toMicro("1.0E1"))
    }

    /** micro 아래 자릿수는 반올림한다. 버리면 합계가 조금씩 줄어든다. */
    @Test
    fun `micro 미만은 반올림한다`() {
        assertEquals(1L, RevenueMeasurement.toMicro("0.0000005"))
        assertEquals(0L, RevenueMeasurement.toMicro("0.0000004"))
    }

    /** 수익 조정으로 음수가 실제로 온다. 지어낸 값이 아니므로 막지 않는다. */
    @Test
    fun `음수 조정 금액도 그대로 변환한다`() {
        assertEquals(-2_500_000L, RevenueMeasurement.toMicro("-2.5"))
    }

    @Test
    fun `읽을 수 없는 금액은 null 이다`() {
        assertNull(RevenueMeasurement.toMicro(null))
        assertNull(RevenueMeasurement.toMicro(""))
        assertNull(RevenueMeasurement.toMicro("N/A"))
        assertNull(RevenueMeasurement.toMicro("1,234"))
    }

    /** Long 을 넘는 값은 저장하지 않는다. 곱하기 전에 잘라야 메모리도 지킨다. */
    @Test
    fun `범위를 넘는 금액은 null 이다`() {
        assertNull(RevenueMeasurement.toMicro("1E999999"))
        assertNull(RevenueMeasurement.toMicro("-1E999999"))
        assertNull(RevenueMeasurement.toMicro("10000000000000"))
    }

    /** Long.MAX / 1,000,000 = 9,223,372,036,854 원까지는 micro 로 담긴다. */
    @Test
    fun `Long 경계 바로 아래는 살린다`() {
        assertEquals(9_223_372_036_854_000_000L, RevenueMeasurement.toMicro("9223372036854"))
        assertNull(RevenueMeasurement.toMicro("9223372036855"))
    }

    // ---- 통화 ----

    @Test
    fun `통화 없는 응답은 측정으로 인정하지 않는다`() {
        assertEquals(RevenueStatus.ERROR, RevenueMeasurement.fromApi("1234", null).status)
        assertEquals(RevenueStatus.ERROR, RevenueMeasurement.fromApi("1234", "   ").status)
    }

    @Test
    fun `금액을 못 읽으면 ERROR 다`() {
        assertEquals(RevenueStatus.ERROR, RevenueMeasurement.fromApi("없음", "KRW").status)
    }

    /** **실제로 0 원을 번 것은 측정이다.** PENDING 과 구분돼야 한다. */
    @Test
    fun `KRW 0 원은 측정된 0 이다`() {
        val measurement = RevenueMeasurement.fromApi("0", "KRW")

        assertEquals(RevenueStatus.MEASURED, measurement.status)
        assertEquals(0L, measurement.amountMicro)
        assertEquals("KRW", measurement.currency)
    }

    @Test
    fun `KRW 양수는 micro 로 저장된다`() {
        val measurement = RevenueMeasurement.fromApi("15230.5", "KRW")

        assertEquals(RevenueStatus.MEASURED, measurement.status)
        assertEquals(15_230_500_000L, measurement.amountMicro)
        assertEquals("KRW", measurement.currency)
    }

    // ---- 불변식 ----

    @Test
    fun `측정이 아닌 상태는 금액을 가질 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            RevenueMeasurement(RevenueStatus.PENDING, amountMicro = 100L)
        }
        assertFailsWith<IllegalArgumentException> {
            RevenueMeasurement(RevenueStatus.PERMISSION_REQUIRED, currency = "KRW")
        }
    }

    @Test
    fun `측정은 통화 없이 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            RevenueMeasurement(RevenueStatus.MEASURED, amountMicro = 100L, currency = null)
        }
        assertFailsWith<IllegalArgumentException> {
            RevenueMeasurement(RevenueStatus.MEASURED, amountMicro = null, currency = "KRW")
        }
    }

    @Test
    fun `측정이 아닌 조회 결과에는 일별 금액이 없다`() {
        assertFailsWith<IllegalArgumentException> {
            RevenueReport(
                RevenueStatus.PERMISSION_REQUIRED,
                daily = mapOf(java.time.LocalDate.of(2026, 8, 1) to RevenueMeasurement.measured(1L, "KRW")),
            )
        }
    }
}
