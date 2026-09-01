package com.ongo.application.revenue

import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.revenue.PlatformRevenueStatusCount

/**
 * 광고 수익을 지금 보여줄 수 있는지, 없다면 **왜 없는지**.
 *
 * 판정 근거는 저장된 `analytics_daily.revenue_status`와 현재 수익 수집 계약이다.
 * 단순히 연결된 플랫폼 목록만으로 추측하지 않는다 — YouTube 를 연결했어도 재연동 전이면
 * 수익을 못 읽고, TikTok 만 있으면 애초에 수집 대상이 아니다. 둘 다 "0 원"으로 보이지만
 * 사용자가 할 일은 다르다.
 */
object RevenueAvailability {

    /** 수익이 아직 없을 때 사용자가 실제로 할 수 있는 일 순서로 본다. */
    fun evaluate(counts: List<PlatformRevenueStatusCount>): Result {
        val present = counts.filter { it.rows > 0 }

        if (present.isEmpty()) {
            return Result(
                available = false,
                reason = "아직 수집된 성과 데이터가 없습니다. 영상을 게시하고 분석이 쌓이면 수익도 함께 표시됩니다.",
            )
        }

        // 수익을 실제로 수집하는 플랫폼의 실측만 가용성의 근거로 삼는다. 현재는
        // YouTube만 estimatedRevenue를 지원하므로, 다른 플랫폼의 레거시/오염된
        // MEASURED 행이 생겨도 수익이 측정됐다고 잘못 열지 않는다.
        val measurable = present.filter {
            PlatformMetricAvailability.isAvailable(it.platform, PlatformMetricAvailability.REVENUE_MICRO)
        }
        val statuses = measurable.map { it.status }.toSet()

        if (RevenueStatus.MEASURED.name in statuses) {
            return Result(available = true, reason = null)
        }
        if (RevenueStatus.PERMISSION_REQUIRED.name in statuses) {
            return Result(
                available = false,
                reason = "YouTube 수익 조회 권한이 없습니다. 채널을 다시 연동하면서 수익 보기 권한에 동의해주세요.",
                reconnectRequired = true,
            )
        }
        if (RevenueStatus.PENDING.name in statuses) {
            return Result(
                available = false,
                reason = "광고 수익은 YouTube 집계가 확정될 때까지 며칠 걸립니다. 확정되면 표시됩니다.",
            )
        }
        if (RevenueStatus.ERROR.name in statuses) {
            return Result(
                available = false,
                reason = "수익 데이터를 가져오는 중 오류가 발생했습니다. 잠시 후 다시 확인해주세요.",
            )
        }

        // V107 이전에 생성된 분석 행과 V107 직후 새 분석 행은 DB 기본값이
        // UNSUPPORTED다. YouTube의 경우 수익 API를 아직 호출하지 않았다는 뜻이지
        // 플랫폼이 미지원이라는 뜻이 아니다. 마이그레이션에서 모든 기존 행을
        // UPDATE하면 파티션 전체를 다시 쓰고 관측하지 않은 권한 상태를 지어내므로,
        // 읽기 시점에만 이 의미를 보정한다.
        if (measurable.any { it.platform.equals("YOUTUBE", ignoreCase = true) }) {
            return Result(
                available = false,
                reason = "YouTube 광고 수익을 아직 조회하지 않았습니다. 수익 보기 권한을 확인하려면 채널을 다시 연결해주세요.",
                reconnectRequired = true,
            )
        }

        return Result(
            available = false,
            reason = "현재 연결된 플랫폼에서는 광고 수익을 자동 수집하지 않습니다. 브랜드딜 수익은 브랜드딜 탭에서 확인할 수 있습니다.",
        )
    }

    data class Result(
        val available: Boolean,
        val reason: String?,
        val reconnectRequired: Boolean = false,
    )
}
