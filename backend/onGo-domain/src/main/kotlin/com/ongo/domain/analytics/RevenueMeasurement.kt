package com.ongo.domain.analytics

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 광고 수익 한 칸의 **측정 상태**.
 *
 * `revenue_micro = 0` 하나로는 네 가지 전혀 다른 상황이 구분되지 않는다 — 실제로 0원을
 * 번 날, 권한이 없어 못 물어본 날, 아직 확정되지 않은 날, 애초에 수집하지 않는 플랫폼.
 * 금액과 함께 이 상태를 저장해야 화면이 "0원"과 "모름"을 다르게 말할 수 있다.
 *
 * **API 가 증명한 것만 기록한다.** 예를 들어 "이 채널은 수익화 대상이 아니다" 는 지금
 * 어떤 응답으로도 확인할 수 없으므로 그런 상태는 두지 않는다. 행이 없으면 [PENDING] 이지
 * "0원 확정" 이 아니다.
 */
enum class RevenueStatus {
    /** API 가 금액을 돌려줬다. **실제로 0원인 경우를 포함한다.** */
    MEASURED,

    /** 권한은 있는데 아직 행이 없다. 확정 지연이거나 집계 전이다. */
    PENDING,

    /** 금전 scope 부족(401/403). 사용자가 채널을 다시 연동해 동의해야 한다. */
    PERMISSION_REQUIRED,

    /** 이 플랫폼에서는 수익을 수집하지 않는다. 기본값. */
    UNSUPPORTED,

    /** 조회에 실패했다. 원인을 특정할 수 없다. */
    ERROR,
}

/**
 * 상태 + (측정됐다면) 금액·통화.
 *
 * 통화 없는 금액은 만들 수 없다. `estimatedRevenue` 는 채널의 지급 통화로 내려오므로
 * 통화를 잃은 숫자는 원화로 읽으면 몇백 배 틀린다.
 */
data class RevenueMeasurement(
    val status: RevenueStatus,
    val amountMicro: Long? = null,
    val currency: String? = null,
) {
    init {
        if (status == RevenueStatus.MEASURED) {
            requireNotNull(amountMicro) { "MEASURED 는 금액이 있어야 한다" }
            require(!currency.isNullOrBlank()) { "MEASURED 는 통화가 있어야 한다" }
        } else {
            require(amountMicro == null) { "$status 는 금액을 가질 수 없다" }
            require(currency == null) { "$status 는 통화를 가질 수 없다" }
        }
    }

    companion object {
        const val MICRO_PER_UNIT = 1_000_000L

        /** 이 값을 넘는 금액은 micro 로 바꾸면 Long 을 넘는다. */
        private val MAX_UNITS: BigDecimal =
            BigDecimal(Long.MAX_VALUE).divide(BigDecimal(MICRO_PER_UNIT), 0, RoundingMode.DOWN)

        val PENDING = RevenueMeasurement(RevenueStatus.PENDING)
        val PERMISSION_REQUIRED = RevenueMeasurement(RevenueStatus.PERMISSION_REQUIRED)
        val UNSUPPORTED = RevenueMeasurement(RevenueStatus.UNSUPPORTED)
        val ERROR = RevenueMeasurement(RevenueStatus.ERROR)

        fun measured(amountMicro: Long, currency: String) =
            RevenueMeasurement(RevenueStatus.MEASURED, amountMicro, currency.trim().uppercase())

        /**
         * API 가 준 금액 문자열을 micro 로 바꾼다.
         *
         * 파싱 실패, 범위 초과는 **null** 이다 — 그런 값은 저장하지 않고 [ERROR] 로 남긴다.
         * 0.5 micro 는 반올림한다(HALF_UP). 수익 조정으로 음수가 오는 경우가 실제로 있어
         * 음수는 막지 않는다. 지어낸 값이 아니라 API 가 준 값이다.
         */
        fun toMicro(rawAmount: String?): Long? {
            val decimal = rawAmount?.trim()?.toBigDecimalOrNull() ?: return null
            // 곱하기 전에 자릿수를 먼저 본다. 1E999999 같은 값을 그대로 곱하면 메모리가 터진다.
            if (decimal.abs() > MAX_UNITS) return null
            return decimal
                .multiply(BigDecimal(MICRO_PER_UNIT))
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        }

        /**
         * 통화가 붙은 금액만 측정으로 인정한다. 통화가 비었거나 금액을 못 읽으면 [ERROR].
         */
        fun fromApi(rawAmount: String?, currency: String?): RevenueMeasurement {
            if (currency.isNullOrBlank()) return ERROR
            val micro = toMicro(rawAmount) ?: return ERROR
            return measured(micro, currency)
        }
    }
}

/**
 * 한 영상의 기간별 수익 조회 결과.
 *
 * [status] 가 [RevenueStatus.MEASURED] 가 아니면 조회 자체가 성립하지 않은 것이라
 * [daily] 는 비어 있다. 응답에 없는 날짜를 "0원"으로 채우지 않는다 — API 가 증명하지
 * 않은 값이다.
 */
data class RevenueReport(
    val status: RevenueStatus,
    val daily: Map<java.time.LocalDate, RevenueMeasurement> = emptyMap(),
) {
    init {
        if (status != RevenueStatus.MEASURED) {
            require(daily.isEmpty()) { "$status 결과에는 일별 금액이 있을 수 없다" }
        }
    }

    companion object {
        val PENDING = RevenueReport(RevenueStatus.PENDING)
        val PERMISSION_REQUIRED = RevenueReport(RevenueStatus.PERMISSION_REQUIRED)
        val UNSUPPORTED = RevenueReport(RevenueStatus.UNSUPPORTED)
        val ERROR = RevenueReport(RevenueStatus.ERROR)

        fun measured(daily: Map<java.time.LocalDate, RevenueMeasurement>) =
            RevenueReport(RevenueStatus.MEASURED, daily)
    }
}
