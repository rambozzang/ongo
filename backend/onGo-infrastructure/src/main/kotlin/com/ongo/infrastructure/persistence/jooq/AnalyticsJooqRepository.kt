package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.analytics.TrendData
import com.ongo.domain.video.Video
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENTS_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DATE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LIKES
import com.ongo.infrastructure.persistence.jooq.Fields.REVENUE_MICRO
import com.ongo.infrastructure.persistence.jooq.Fields.SHARES
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIBER_GAINED
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_UPLOAD_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.WATCH_TIME_SECONDS
import com.ongo.infrastructure.persistence.jooq.Tables.ANALYTICS_DAILY
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_UPLOADS
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AnalyticsJooqRepository(
    private val dsl: DSLContext,
) : AnalyticsRepository {

    override fun findByVideoUploadIdAndDateRange(
        videoUploadId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<AnalyticsDaily> =
        dsl.select()
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.eq(videoUploadId))
            .and(DATE.greaterOrEqual(from))
            .and(DATE.lessOrEqual(to))
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toAnalyticsDaily() }

    override fun findByVideoUploadIdsAndDateRange(
        videoUploadIds: List<Long>,
        from: LocalDate,
        to: LocalDate,
    ): Map<Long, List<AnalyticsDaily>> {
        if (videoUploadIds.isEmpty()) return emptyMap()
        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(videoUploadIds))
            .and(DATE.greaterOrEqual(from))
            .and(DATE.lessOrEqual(to))
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toAnalyticsDaily() }
            .groupBy { it.videoUploadId }
    }

    override fun getDashboardKpi(userId: Long, days: Int): DashboardKpi {
        val now = LocalDate.now()
        val currentFrom = now.minusDays(days.toLong())
        val previousFrom = currentFrom.minusDays(days.toLong())

        // Get user's video upload IDs
        val uploadIds = getUserUploadIds(userId)

        if (uploadIds.isEmpty()) {
            return DashboardKpi(
                totalViews = 0,
                totalViewsChange = 0.0,
                totalSubscribers = 0,
                totalSubscribersChange = 0,
                totalLikes = 0,
                totalLikesChange = 0.0,
                creditBalance = 0,
                creditTotal = 0,
            )
        }

        // Current period aggregates
        val current = dsl.select(
            DSL.sum(VIEWS).`as`("total_views"),
            DSL.sum(LIKES).`as`("total_likes"),
            DSL.sum(SUBSCRIBER_GAINED).`as`("total_subs"),
        )
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(DATE.greaterOrEqual(currentFrom))
            .and(DATE.lessOrEqual(now))
            .fetchOne()

        val currentViews = current?.get("total_views", Long::class.java) ?: 0L
        val currentLikes = current?.get("total_likes", Long::class.java) ?: 0L
        val currentSubs = current?.get("total_subs", Long::class.java) ?: 0L

        // Previous period aggregates for change %
        val previous = dsl.select(
            DSL.sum(VIEWS).`as`("total_views"),
            DSL.sum(LIKES).`as`("total_likes"),
            DSL.sum(SUBSCRIBER_GAINED).`as`("total_subs"),
        )
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(DATE.greaterOrEqual(previousFrom))
            .and(DATE.lessThan(currentFrom))
            .fetchOne()

        val previousViews = previous?.get("total_views", Long::class.java) ?: 0L
        val previousLikes = previous?.get("total_likes", Long::class.java) ?: 0L
        val previousSubs = previous?.get("total_subs", Long::class.java) ?: 0L

        // Credit info
        val creditRecord = dsl.select(
            DSL.field("balance", Int::class.java),
            DSL.field("free_remaining", Int::class.java),
        )
            .from(Tables.AI_CREDITS)
            .where(Fields.USER_ID.eq(userId))
            .fetchOne()

        val balance = creditRecord?.get("balance", Int::class.java) ?: 0
        val freeRemaining = creditRecord?.get("free_remaining", Int::class.java) ?: 0

        return DashboardKpi(
            totalViews = currentViews,
            totalViewsChange = calculateChangePercent(previousViews, currentViews),
            totalSubscribers = currentSubs,
            totalSubscribersChange = currentSubs - previousSubs,
            totalLikes = currentLikes,
            totalLikesChange = calculateChangePercent(previousLikes, currentLikes),
            creditBalance = balance + freeRemaining,
            creditTotal = balance + freeRemaining,
        )
    }

    override fun getTrendData(userId: Long, days: Int): List<TrendData> {
        val from = LocalDate.now().minusDays(days.toLong())
        val uploadIds = getUserUploadIds(userId)

        if (uploadIds.isEmpty()) return emptyList()

        val platformField = DSL.field("vu.platform", String::class.java)
        val dateField = DSL.field("ad.date", LocalDate::class.java)
        val viewsSum = DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views")
        val subscribersSum = DSL.sum(DSL.field("ad.subscriber_gained", Int::class.java)).`as`("total_subscribers")

        return dsl.select(dateField, platformField, viewsSum, subscribersSum)
            .from(DSL.table("analytics_daily").`as`("ad"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(dateField.greaterOrEqual(from))
            .groupBy(dateField, platformField)
            .orderBy(dateField.asc())
            .fetch()
            .map { record ->
                val platformStr = record.get(platformField)
                TrendData(
                    date = record.get("date", LocalDate::class.java),
                    platform = platformStr?.let { Platform.valueOf(it) },
                    views = record.get("total_views", Long::class.java) ?: 0L,
                    subscribers = record.get("total_subscribers", Long::class.java) ?: 0L,
                )
            }
    }

    override fun getTopVideos(userId: Long, days: Int, limit: Int): List<Video> {
        val from = LocalDate.now().minusDays(days.toLong())

        val videoIdField = DSL.field("v.id", Long::class.java)
        val viewsSum = DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views")

        return dsl.select(
            videoIdField,
            DSL.field("v.user_id", Long::class.java),
            DSL.field("v.title", String::class.java),
            DSL.field("v.description", String::class.java),
            DSL.field("v.tags"),
            DSL.field("v.category", String::class.java),
            DSL.field("v.file_url", String::class.java),
            DSL.field("v.file_size_bytes", Long::class.java),
            DSL.field("v.duration_seconds", Int::class.java),
            DSL.field("v.resolution", String::class.java),
            DSL.field("v.original_filename", String::class.java),
            DSL.field("v.content_hash", String::class.java),
            DSL.field("v.thumbnail_urls"),
            DSL.field("v.status", String::class.java),
            DSL.field("v.created_at", java.time.LocalDateTime::class.java),
            DSL.field("v.updated_at", java.time.LocalDateTime::class.java),
            viewsSum,
        )
            .from(DSL.table("videos").`as`("v"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("vu.video_id", Long::class.java).eq(videoIdField))
            .join(DSL.table("analytics_daily").`as`("ad"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .groupBy(videoIdField, DSL.field("v.user_id"), DSL.field("v.title"), DSL.field("v.description"),
                DSL.field("v.tags"), DSL.field("v.category"), DSL.field("v.file_url"),
                DSL.field("v.file_size_bytes"), DSL.field("v.duration_seconds"), DSL.field("v.resolution"),
                DSL.field("v.original_filename"), DSL.field("v.content_hash"), DSL.field("v.thumbnail_urls"),
                DSL.field("v.status"), DSL.field("v.created_at"), DSL.field("v.updated_at"))
            .orderBy(viewsSum.desc())
            .limit(limit)
            .fetch()
            .map { record ->
                @Suppress("UNCHECKED_CAST")
                val tagsRaw = record.get("tags")
                val tags: List<String> = when (tagsRaw) {
                    is Array<*> -> (tagsRaw as Array<String>).toList()
                    else -> emptyList()
                }

                Video(
                    id = record.get("id", Long::class.java),
                    userId = record.get("user_id", Long::class.java),
                    title = record.get("title", String::class.java),
                    description = record.get("description", String::class.java),
                    tags = tags,
                    category = record.get("category", String::class.java),
                    fileUrl = record.get("file_url", String::class.java),
                    fileSizeBytes = record.get("file_size_bytes", Long::class.java),
                    durationSeconds = record.get("duration_seconds", Int::class.java),
                    resolution = record.get("resolution", String::class.java),
                    originalFilename = record.get("original_filename", String::class.java),
                    contentHash = record.get("content_hash", String::class.java),
                    thumbnailUrls = emptyList(),
                    status = UploadStatus.valueOf(record.get("status", String::class.java)),
                    createdAt = record.get("created_at", java.time.LocalDateTime::class.java),
                    updatedAt = record.get("updated_at", java.time.LocalDateTime::class.java),
                )
            }
    }

    override fun getHeatmapData(userId: Long): Map<String, Map<Int, Long>> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyMap()

        val dayOfWeek = DSL.field("EXTRACT(DOW FROM {0})", Int::class.java, DATE).`as`("day_of_week")
        val hour = DSL.field("EXTRACT(HOUR FROM {0})", Int::class.java, CREATED_AT).`as`("hour")
        val viewsSum = DSL.sum(VIEWS).`as`("total_views")

        val results = dsl.select(dayOfWeek, hour, viewsSum)
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .groupBy(dayOfWeek, hour)
            .fetch()

        val heatmap = mutableMapOf<String, MutableMap<Int, Long>>()
        val dayNames = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        for (record in results) {
            val dow = record.get("day_of_week", Int::class.java) ?: 0
            val h = record.get("hour", Int::class.java) ?: 0
            val views = record.get("total_views", Long::class.java) ?: 0L
            val dayName = dayNames[dow]

            heatmap.getOrPut(dayName) { mutableMapOf() }[h] = views
        }

        return heatmap
    }

    override fun save(analytics: AnalyticsDaily): AnalyticsDaily {
        val id = dsl.insertInto(ANALYTICS_DAILY)
            .set(VIDEO_UPLOAD_ID, analytics.videoUploadId)
            .set(DATE, analytics.date)
            .set(VIEWS, analytics.views)
            .set(LIKES, analytics.likes)
            .set(COMMENTS_COUNT, analytics.commentsCount)
            .set(SHARES, analytics.shares)
            .set(WATCH_TIME_SECONDS, analytics.watchTimeSeconds)
            .set(SUBSCRIBER_GAINED, analytics.subscriberGained)
            .set(REVENUE_MICRO, analytics.revenueMicro)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(ID.eq(id))
            .fetchOne()!!
            .toAnalyticsDaily()
    }

    override fun upsert(analytics: AnalyticsDaily): AnalyticsDaily {
        val id = dsl.insertInto(ANALYTICS_DAILY)
            .set(VIDEO_UPLOAD_ID, analytics.videoUploadId)
            .set(DATE, analytics.date)
            .set(VIEWS, analytics.views)
            .set(LIKES, analytics.likes)
            .set(COMMENTS_COUNT, analytics.commentsCount)
            .set(SHARES, analytics.shares)
            .set(WATCH_TIME_SECONDS, analytics.watchTimeSeconds)
            .set(SUBSCRIBER_GAINED, analytics.subscriberGained)
            .set(REVENUE_MICRO, analytics.revenueMicro)
            .onConflict(VIDEO_UPLOAD_ID, DATE)
            .doUpdate()
            .set(VIEWS, analytics.views)
            .set(LIKES, analytics.likes)
            .set(COMMENTS_COUNT, analytics.commentsCount)
            .set(SHARES, analytics.shares)
            .set(WATCH_TIME_SECONDS, analytics.watchTimeSeconds)
            .set(SUBSCRIBER_GAINED, analytics.subscriberGained)
            .set(REVENUE_MICRO, analytics.revenueMicro)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(ID.eq(id))
            .fetchOne()!!
            .toAnalyticsDaily()
    }

    override fun saveBatch(analytics: List<AnalyticsDaily>) {
        if (analytics.isEmpty()) return

        val insert = dsl.insertInto(
            ANALYTICS_DAILY,
            VIDEO_UPLOAD_ID, DATE, VIEWS, LIKES, COMMENTS_COUNT,
            SHARES, WATCH_TIME_SECONDS, SUBSCRIBER_GAINED, REVENUE_MICRO,
        )

        var batch = insert.values(null as Long?, null, null, null, null, null, null, null, null)
        // Use batch binding
        val batchBind = dsl.batch(
            dsl.insertInto(
                ANALYTICS_DAILY,
                VIDEO_UPLOAD_ID, DATE, VIEWS, LIKES, COMMENTS_COUNT,
                SHARES, WATCH_TIME_SECONDS, SUBSCRIBER_GAINED, REVENUE_MICRO,
            ).values(null as Long?, null, null, null, null, null, null, null, null)
        )

        for (a in analytics) {
            batchBind.bind(
                a.videoUploadId, a.date, a.views, a.likes, a.commentsCount,
                a.shares, a.watchTimeSeconds, a.subscriberGained, a.revenueMicro,
            )
        }

        batchBind.execute()
    }

    override fun findDailyAnalyticsByChannelIds(
        userId: Long,
        platform: com.ongo.common.enums.Platform?,
    ): List<AnalyticsDaily> {
        val uploadIdsQuery = dsl.select(DSL.field("vu.id", Long::class.java))
            .from(DSL.table("video_uploads").`as`("vu"))
            .join(DSL.table("videos").`as`("v"))
            .on(DSL.field("v.id", Long::class.java).eq(DSL.field("vu.video_id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))

        if (platform != null) {
            uploadIdsQuery.and(DSL.field("vu.platform", String::class.java).eq(platform.name))
        }

        val uploadIds = uploadIdsQuery.fetch().map { it.get(0, Long::class.java) }
        if (uploadIds.isEmpty()) return emptyList()

        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toAnalyticsDaily() }
    }

    override fun findByVideoUploadIds(uploadIds: List<Long>): List<AnalyticsDaily> {
        if (uploadIds.isEmpty()) return emptyList()
        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toAnalyticsDaily() }
    }

    override fun findAllByUserId(userId: Long): List<AnalyticsDaily> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()
        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toAnalyticsDaily() }
    }

    private fun getUserUploadIds(userId: Long): List<Long> =
        dsl.select(DSL.field("vu.id", Long::class.java))
            .from(DSL.table("video_uploads").`as`("vu"))
            .join(DSL.table("videos").`as`("v"))
            .on(DSL.field("v.id", Long::class.java).eq(DSL.field("vu.video_id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .fetch()
            .map { it.get(0, Long::class.java) }

    private fun calculateChangePercent(previous: Long, current: Long): Double =
        if (previous == 0L) {
            if (current > 0) 100.0 else 0.0
        } else {
            ((current - previous).toDouble() / previous.toDouble()) * 100.0
        }

    private fun Record.toAnalyticsDaily(): AnalyticsDaily = AnalyticsDaily(
        id = get(ID),
        videoUploadId = get(VIDEO_UPLOAD_ID),
        date = localDate(DATE)!!,
        views = get(VIEWS),
        likes = get(LIKES),
        commentsCount = get(COMMENTS_COUNT),
        shares = get(SHARES),
        watchTimeSeconds = get(WATCH_TIME_SECONDS),
        subscriberGained = get(SUBSCRIBER_GAINED),
        revenueMicro = get(REVENUE_MICRO),
        createdAt = localDateTime(CREATED_AT),
    )
}
