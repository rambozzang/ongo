package com.ongo.infrastructure.persistence.jooq

import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.revenue.BrandDealRevenueRaw
import com.ongo.domain.revenue.CpmRpmRaw
import com.ongo.domain.revenue.DailyRevenue
import com.ongo.domain.revenue.PlatformRevenue
import com.ongo.domain.revenue.PlatformRevenueStatusCount
import com.ongo.domain.revenue.RevenueRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class RevenueJooqRepository(
    private val dsl: DSLContext,
) : RevenueRepository {

    private companion object {
        /**
         * 금액 합산은 **실제로 측정된 행만** 더한다.
         *
         * 조건이 둘인 이유가 각각 다르다.
         *
         * - 상태: `revenue_micro` 는 `NOT NULL DEFAULT 0` 이라 아직 확정되지 않은 날짜도
         *   0 을 들고 있다. 그것까지 더하면 "측정된 0 원"처럼 보인다.
         * - 플랫폼: [RevenueAvailability] 의 가용성 판정이 수익 수집 플랫폼만 보므로
         *   합산도 같은 범위를 봐야 한다. 한쪽만 걸면 금액은 더해지는데 화면은
         *   "수집하지 않습니다" 라고 말하는 모순이 난다.
         *
         * 두 조건 모두 [PlatformMetricAvailability] 한 곳에서 나온다. 새 플랫폼이 수익을
         * 지원하게 되면 그 계약만 고치면 판정과 합산이 함께 따라온다.
         */
        val MEASURED_ONLY = DSL.field("ad.revenue_status", String::class.java)
            .eq(RevenueStatus.MEASURED.name)
            .and(
                DSL.field("vu.platform::text", String::class.java)
                    .`in`(PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.REVENUE_MICRO)),
            )
    }

    override fun getDailyRevenue(userId: Long, from: LocalDate, to: LocalDate): List<DailyRevenue> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val dateField = DSL.field("ad.date", LocalDate::class.java)
        val platformField = DSL.field("vu.platform::text", String::class.java)
        val revenueSum = DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue")

        return dsl.select(dateField, platformField, revenueSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(dateField.greaterOrEqual(from))
            .and(dateField.lessOrEqual(to))
            .and(MEASURED_ONLY)
            .groupBy(dateField, platformField)
            .orderBy(dateField.asc())
            .fetch()
            .map { record ->
                DailyRevenue(
                    date = record.get("date", LocalDate::class.java),
                    revenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                    platform = record.get(platformField),
                )
            }
    }

    override fun getPlatformRevenue(userId: Long, from: LocalDate, to: LocalDate): List<PlatformRevenue> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val platformField = DSL.field("vu.platform::text", String::class.java)
        val revenueSum = DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue")

        return dsl.select(platformField, revenueSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            .and(MEASURED_ONLY)
            .groupBy(platformField)
            .fetch()
            .map { record ->
                PlatformRevenue(
                    platform = record.get(platformField) ?: "UNKNOWN",
                    totalRevenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                )
            }
    }

    /**
     * 다른 합산과 **같은 조인 모양**을 쓴다. 예전에는 여기만 `analytics_daily` 단독
     * 조회라 [MEASURED_ONLY] 를 공유하지 못했고, 그래서 총합만 조건이 갈라질 여지가 있었다.
     */
    override fun getTotalRevenue(userId: Long, from: LocalDate, to: LocalDate): Long {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return 0L

        return dsl.select(DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total"))
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            .and(MEASURED_ONLY)
            .fetchOne()
            ?.get("total", Long::class.java) ?: 0L
    }

    /**
     * 플랫폼 × 상태별 행 수. 금액 조회가 `MEASURED` 만 더하므로, "0 원"의 원인은
     * 이 집계로만 설명할 수 있다.
     */
    override fun getRevenueStatusCounts(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<PlatformRevenueStatusCount> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val platformField = DSL.field("vu.platform::text", String::class.java)
        val statusField = DSL.field("ad.revenue_status", String::class.java)
        val rowCount = DSL.count().`as`("row_count")

        return dsl.select(platformField, statusField, rowCount)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            .groupBy(platformField, statusField)
            .fetch()
            .map { record ->
                PlatformRevenueStatusCount(
                    platform = record.get(platformField) ?: "UNKNOWN",
                    status = record.get(statusField) ?: RevenueStatus.UNSUPPORTED.name,
                    rows = (record.get("row_count", Int::class.java) ?: 0).toLong(),
                )
            }
    }

    override fun getPaymentTotal(userId: Long, from: LocalDate, to: LocalDate): Long {
        return dsl.select(DSL.sum(Fields.AMOUNT).`as`("total"))
            .from(Tables.PAYMENTS)
            .where(Fields.USER_ID.eq(userId))
            .and(DSL.field("created_at", java.time.LocalDateTime::class.java)
                .greaterOrEqual(from.atStartOfDay()))
            .and(DSL.field("created_at", java.time.LocalDateTime::class.java)
                .lessThan(to.plusDays(1).atStartOfDay()))
            .fetchOne()
            ?.get("total", Long::class.java) ?: 0L
    }

    override fun getCpmRpmByPlatform(userId: Long, from: LocalDate, to: LocalDate): List<CpmRpmRaw> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val platformField = DSL.field("vu.platform::text", String::class.java)
        val impressionsSum = DSL.sum(DSL.field("ad.impressions", Int::class.java)).`as`("total_impressions")
        val viewsSum = DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views")
        val revenueSum = DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue")

        return dsl.select(platformField, impressionsSum, viewsSum, revenueSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            // 분자(수익)와 분모(노출·조회)를 같은 행으로 맞춘다. 측정되지 않은 날의
            // 노출을 분모에 넣으면 CPM 이 실제보다 낮게 나온다.
            .and(MEASURED_ONLY)
            .groupBy(platformField)
            .fetch()
            .map { record ->
                CpmRpmRaw(
                    platform = record.get(platformField) ?: "UNKNOWN",
                    impressions = record.get("total_impressions", Long::class.java) ?: 0L,
                    views = record.get("total_views", Long::class.java) ?: 0L,
                    revenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                )
            }
    }

    override fun getBrandDealRevenue(userId: Long, from: LocalDate, to: LocalDate): List<BrandDealRevenueRaw> {
        return dsl.select(
            Fields.ID,
            Fields.BRAND_NAME,
            Fields.DEAL_VALUE,
            Fields.STATUS,
            Fields.PLATFORM,
        )
            .from(Tables.BRAND_DEALS)
            .where(Fields.USER_ID.eq(userId))
            .and(Fields.CREATED_AT.greaterOrEqual(from.atStartOfDay()))
            .and(Fields.CREATED_AT.lessThan(to.plusDays(1).atStartOfDay()))
            .orderBy(Fields.CREATED_AT.desc())
            .fetch()
            .map { record ->
                BrandDealRevenueRaw(
                    id = record.get(Fields.ID),
                    brandName = record.get(Fields.BRAND_NAME) ?: "",
                    dealValue = record.get(Fields.DEAL_VALUE) ?: 0L,
                    status = record.get(Fields.STATUS) ?: "UNKNOWN",
                    platform = record.get(Fields.PLATFORM),
                )
            }
    }

    private fun getUserUploadIds(userId: Long): List<Long> =
        dsl.select(DSL.field("vu.id", Long::class.java))
            .from(DSL.table("video_uploads").`as`("vu"))
            .join(DSL.table("videos").`as`("v"))
            .on(DSL.field("v.id", Long::class.java).eq(DSL.field("vu.video_id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .fetch()
            .map { it.get(0, Long::class.java) }
}
