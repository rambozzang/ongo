package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenue.DailyRevenue
import com.ongo.domain.revenue.PlatformRevenue
import com.ongo.domain.revenue.RevenueRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class RevenueJooqRepository(
    private val dsl: DSLContext,
) : RevenueRepository {

    override fun getDailyRevenue(userId: Long, from: LocalDate, to: LocalDate): List<DailyRevenue> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val dateField = DSL.field("ad.date", LocalDate::class.java)
        val platformField = DSL.field("vu.platform", String::class.java)
        val revenueSum = DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue")

        return dsl.select(dateField, platformField, revenueSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(dateField.greaterOrEqual(from))
            .and(dateField.lessOrEqual(to))
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

        val platformField = DSL.field("vu.platform", String::class.java)
        val revenueSum = DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue")

        return dsl.select(platformField, revenueSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            .groupBy(platformField)
            .fetch()
            .map { record ->
                PlatformRevenue(
                    platform = record.get(platformField) ?: "UNKNOWN",
                    totalRevenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                )
            }
    }

    override fun getTotalRevenue(userId: Long, from: LocalDate, to: LocalDate): Long {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return 0L

        return dsl.select(DSL.sum(Fields.REVENUE_MICRO).`as`("total"))
            .from(Tables.ANALYTICS_DAILY)
            .where(Fields.VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(Fields.DATE.greaterOrEqual(from))
            .and(Fields.DATE.lessOrEqual(to))
            .fetchOne()
            ?.get("total", Long::class.java) ?: 0L
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

    private fun getUserUploadIds(userId: Long): List<Long> =
        dsl.select(DSL.field("vu.id", Long::class.java))
            .from(DSL.table("video_uploads").`as`("vu"))
            .join(DSL.table("videos").`as`("v"))
            .on(DSL.field("v.id", Long::class.java).eq(DSL.field("vu.video_id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .fetch()
            .map { it.get(0, Long::class.java) }
}
