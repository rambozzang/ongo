package com.ongo.application.revenue

import com.ongo.application.revenue.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.user.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RevenueUseCase(
    private val revenueRepository: RevenueRepository,
    private val userRepository: UserRepository,
) {

    /**
     * 가용성은 **저장된 측정 상태**로 정한다. 연결된 플랫폼 목록으로 추측하지 않는다 —
     * YouTube 가 있어도 재연동 전이면 못 읽고, 그건 사용자가 조치할 수 있는 다른 상황이다.
     */
    private fun revenueAvailability(userId: Long, from: LocalDate, to: LocalDate): RevenueAvailability.Result =
        RevenueAvailability.evaluate(revenueRepository.getRevenueStatusCounts(userId, from, to))

    @Cacheable(value = ["revenueSummary"], key = "#userId + '-' + #days")
    fun getRevenueSummary(userId: Long, days: Int): RevenueSummaryResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val previousFrom = from.minusDays(days.toLong())

        val currentTotal = revenueRepository.getTotalRevenue(userId, from, now)
        val previousTotal = revenueRepository.getTotalRevenue(userId, previousFrom, from.minusDays(1))

        /*
         * 이전 기간 수익이 0 이면 성장률은 **정의되지 않는다**(0 으로 나눔).
         *
         * 예전에는 그 자리에 `100.0` 을 넣었다. 그래서 첫 수익 1,000 원과 100만 원이
         * 화면에서 똑같이 "+100%" 로 보였고, 크리에이터는 그것을 실제 성장률로 읽었다.
         * `0 → 0` 은 `0.0` 이 되어 "변화 없음"으로 보였는데, 비교할 기간에 데이터가
         * 없다는 사실과 구분되지 않았다.
         *
         * 대시보드 증감률과 **같은 정책**을 쓴다([MetricChange]). 판정이 갈라지면 같은
         * 사용자가 화면마다 다른 답을 본다.
         */
        val growthPercent = MetricChange.percentChange(previousTotal, currentTotal)

        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, now)
        val availability = revenueAvailability(userId, from, now)
        val breakdown = platformRevenue.map { pr ->
            PlatformRevenueItem(
                platform = pr.platform,
                revenueMicro = pr.totalRevenueMicro,
                revenueKrw = pr.totalRevenueMicro / 1_000_000,
                // 분모가 0 이면 비율이 성립하지 않는다 — `0.0` 은 "비중 0%" 라는 관측이 된다.
                percentage = sharePercent(pr.totalRevenueMicro, currentTotal),
            )
        }

        return RevenueSummaryResponse(
            totalRevenue = currentTotal,
            totalRevenueKrw = currentTotal / 1_000_000,
            // null 은 비교 불가다. 반올림하려다 0 으로 만들면 "변화 없음"이 되어 버린다.
            growthPercent = growthPercent?.let { Math.round(it * 100) / 100.0 },
            platformBreakdown = breakdown,
            platformRevenueAvailable = availability.available,
            platformRevenueUnavailableReason = availability.reason,
            platformRevenueReconnectRequired = availability.reconnectRequired,
        )
    }

    @Cacheable(value = ["revenueTrends"], key = "#userId + '-' + #days")
    fun getRevenueTrends(userId: Long, days: Int): RevenueTrendResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val dailyRevenue = revenueRepository.getDailyRevenue(userId, from, now)
        val availability = revenueAvailability(userId, from, now)

        val points = dailyRevenue.map { dr ->
            RevenueTrendPoint(
                date = dr.date,
                revenueMicro = dr.revenueMicro,
                revenueKrw = dr.revenueMicro / 1_000_000,
                platform = dr.platform,
            )
        }

        return RevenueTrendResponse(
            data = points,
            platformRevenueAvailable = availability.available,
            platformRevenueUnavailableReason = availability.reason,
            platformRevenueReconnectRequired = availability.reconnectRequired,
        )
    }

    fun getCpmRpm(userId: Long, days: Int): CpmRpmResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val rawData = revenueRepository.getCpmRpmByPlatform(userId, from, now)
        val availability = revenueAvailability(userId, from, now)

        val items = rawData.map { raw ->
            // CPM = (수익 / 노출수) * 1000, RPM = (수익 / 조회수) * 1000
            // 수익은 micro 단위이므로 실제 금액으로 변환 (÷ 1,000,000)
            val revenueActual = raw.revenueMicro.toDouble() / 1_000_000
            val unavailable = mutableMapOf<String, String>()

            // 분모가 0 이면 단가가 성립하지 않는다. 예전에는 여기서 0.0 을 넣어
            // "CPM ₩0.00" 을 실측처럼 보여줬다. 분모가 양수면 수익이 0 이어도
            // 그 0 은 실제로 관측된 단가이므로 그대로 남긴다.
            val cpm = if (raw.impressions > 0) {
                perThousand(revenueActual, raw.impressions)
            } else {
                unavailable["cpm"] = CPM_UNAVAILABLE
                null
            }
            val rpm = if (raw.views > 0) {
                perThousand(revenueActual, raw.views)
            } else {
                unavailable["rpm"] = RPM_UNAVAILABLE
                null
            }

            CpmRpmItem(
                platform = raw.platform,
                cpm = cpm,
                rpm = rpm,
                impressions = raw.impressions,
                views = raw.views,
                revenueMicro = raw.revenueMicro,
                unavailableMetrics = unavailable,
            )
        }

        return CpmRpmResponse(
            platforms = items,
            platformRevenueAvailable = availability.available,
            platformRevenueUnavailableReason = availability.reason,
            platformRevenueReconnectRequired = availability.reconnectRequired,
        )
    }

    fun getBrandDealRevenue(userId: Long, days: Int): BrandDealRevenueResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val rawData = revenueRepository.getBrandDealRevenue(userId, from, now)

        val items = rawData.map { raw ->
            BrandDealRevenueItem(
                id = raw.id,
                brandName = raw.brandName,
                dealValue = raw.dealValue,
                dealValueKrw = raw.dealValue / 1_000_000,
                status = raw.status,
                platform = raw.platform,
            )
        }

        val totalRevenue = items.sumOf { it.dealValue }

        return BrandDealRevenueResponse(
            deals = items,
            totalRevenue = totalRevenue,
            totalRevenueKrw = totalRevenue / 1_000_000,
        )
    }

    fun getPlatformRevenue(userId: Long, days: Int): PlatformRevenueResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val totalRevenue = revenueRepository.getTotalRevenue(userId, from, now)
        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, now)
        val availability = revenueAvailability(userId, from, now)

        val items = platformRevenue.map { pr ->
            PlatformRevenueItem(
                platform = pr.platform,
                revenueMicro = pr.totalRevenueMicro,
                revenueKrw = pr.totalRevenueMicro / 1_000_000,
                // 요약과 같은 판정을 쓴다 — 갈라지면 같은 사용자가 화면마다 다른 답을 본다.
                percentage = sharePercent(pr.totalRevenueMicro, totalRevenue),
            )
        }

        return PlatformRevenueResponse(
            platforms = items,
            platformRevenueAvailable = availability.available,
            platformRevenueUnavailableReason = availability.reason,
            platformRevenueReconnectRequired = availability.reconnectRequired,
        )
    }

    /** 1,000 단위 단가를 소수 둘째 자리까지 반올림한다. 호출 전에 `denominator > 0` 을 보장할 것. */
    private fun perThousand(revenueActual: Double, denominator: Long): Double =
        Math.round((revenueActual / denominator) * 1000 * 100) / 100.0

    /**
     * 전체 대비 이 플랫폼의 비중(%). **전체가 0 이면 `null`.**
     *
     * 비율은 분모가 0 이면 정의되지 않는다. 예전에는 `else 0.0` 이라, 수익이 아직 한 푼도
     * 잡히지 않은 상태가 **"비중 0%"** 라는 관측처럼 나갔다 — 유료 AI 프롬프트에도 그
     * 값이 그대로 들어갔다. 성장률([MetricChange.percentChange])과 같은 정책이다.
     *
     * 분모가 양수이면 분자가 0 이어도 그 `0.0` 은 **실측 비중**이므로 그대로 낸다.
     */
    private fun sharePercent(revenueMicro: Long, totalMicro: Long): Double? =
        if (totalMicro > 0) revenueMicro.toDouble() / totalMicro * 100 else null

    companion object {
        /**
         * 단가를 못 낸 이유는 **숫자가 아니라 문장** 이어야 한다. 화면이 이 자리에
         * 숫자를 넣으면 그것이 곧 단가로 읽힌다.
         */
        const val CPM_UNAVAILABLE = "노출수가 집계되지 않아 CPM을 계산할 수 없습니다"
        const val RPM_UNAVAILABLE = "조회수가 집계되지 않아 RPM을 계산할 수 없습니다"
    }
}
