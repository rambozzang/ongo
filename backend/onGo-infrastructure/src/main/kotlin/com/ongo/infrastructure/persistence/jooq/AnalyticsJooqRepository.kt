package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.ChannelInsightsDaily
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.analytics.DailyAggregate
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.analytics.RevenueMeasurement
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.analytics.TrendData
import com.ongo.domain.video.Video
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_VIEW_DURATION_SECONDS
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENTS_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DATE
import com.ongo.infrastructure.persistence.jooq.Fields.DEMOGRAPHICS_AGE
import com.ongo.infrastructure.persistence.jooq.Fields.DEMOGRAPHICS_COUNTRY
import com.ongo.infrastructure.persistence.jooq.Fields.DEMOGRAPHICS_GENDER
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IMPRESSIONS
import com.ongo.infrastructure.persistence.jooq.Fields.LIKES
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PUBLISHED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REVENUE_CURRENCY
import com.ongo.infrastructure.persistence.jooq.Fields.REVENUE_MICRO
import com.ongo.infrastructure.persistence.jooq.Fields.REVENUE_STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.SHARES
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIBER_GAINED
import com.ongo.infrastructure.persistence.jooq.Fields.TRAFFIC_SOURCE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_UPLOAD_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.WATCH_TIME_SECONDS
import com.ongo.infrastructure.persistence.jooq.Tables.ANALYTICS_DAILY
import com.ongo.infrastructure.persistence.jooq.Tables.CHANNEL_INSIGHTS_DAILY
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_UPLOADS
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEOS
import com.fasterxml.jackson.databind.ObjectMapper
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AnalyticsJooqRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper,
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
            /*
             * 게시한 적이 없으면 **어떤 지표도 물어볼 곳이 없다.** 0 은 "0회였다" 는
             * 관측이 되므로 넣지 않는다. 증감도 마찬가지다 — 비교할 기간 자체가 없다.
             */
            return DashboardKpi(
                totalViews = null,
                totalViewsChange = null,
                totalSubscribers = null,
                totalSubscribersChange = null,
                totalLikes = null,
                totalLikesChange = null,
                totalComments = null,
                creditBalance = 0,
                creditTotal = 0,
            )
        }

        /*
         * **구독 증가는 `subscriber_gained` 를 실제로 조회하는 플랫폼의 행만 더한다.**
         *
         * `YouTubeClient` 만 그 지표를 요청하고(`YouTubeClient.kt:149`) 나머지 12개는
         * `0` 을 하드코딩한다. 그 0 들이 합계에 섞여도 **합계 자체는 바뀌지 않지만**,
         * 수집 플랫폼이 하나도 없을 때의 `0` 이 실측과 구분되지 않는 것이 문제였다.
         *
         * `CASE WHEN ... THEN ... END` 는 조건에 맞지 않는 행에 NULL 을 남기고, `SUM` 은
         * NULL 을 건너뛴다. 그래서 **일치하는 행이 하나도 없으면 합계가 NULL** 이 된다 —
         * 이것이 "측정된 0" 과 "물어볼 곳 없음" 을 가르는 신호다. 별도 쿼리 없이 얻는다.
         */
        fun measuredSum(metric: String, column: org.jooq.Field<Int>, alias: String) = DSL.sum(
            DSL.`when`(
                PLATFORM.cast(String::class.java)
                    .`in`(PlatformMetricAvailability.platformsReporting(metric)),
                column,
            ),
        ).`as`(alias)

        val measuredSubscriberGain =
            measuredSum(PlatformMetricAvailability.SUBSCRIBER_GAINED, SUBSCRIBER_GAINED, "total_subs")

        /*
         * **조회수·좋아요·댓글도 같은 계약을 쓴다.**
         *
         * 여기는 오래 `SUM(views)` / `SUM(likes)` / `SUM(comments_count)` 였다. 하드코딩
         * 0 만 있었다면 합계가 바뀌지 않아 구독 증가처럼 "물어볼 곳 없음" 만 문제였겠지만,
         * 어댑터 중에는 **다른 뜻의 큰 숫자**를 같은 컬럼에 넣는 것들이 있다.
         *
         * - `TumblrClient.kt:141` `views = total_notes` — 좋아요+리블로그+답글 총합
         * - `PinterestClient.kt:158` `likes = SAVE` — 저장 수(좋아요가 아니다)
         * - `PinterestClient.kt:159` `comments = 0` — 조회하지도 않는 자리채움
         *
         * 첫 화면 KPI 라 이 오염이 사용자가 처음 보는 숫자를 바꾼다.
         */
        val measuredViews = measuredSum(PlatformMetricAvailability.VIEWS, VIEWS, "total_views")
        val measuredLikes = measuredSum(PlatformMetricAvailability.LIKES, LIKES, "total_likes")
        val measuredComments =
            measuredSum(PlatformMetricAvailability.COMMENTS, COMMENTS_COUNT, "total_comments")

        // Current period aggregates
        val current = dsl.select(
            measuredViews,
            measuredLikes,
            measuredComments,
            measuredSubscriberGain,
        )
            .from(ANALYTICS_DAILY)
            .join(VIDEO_UPLOADS).on(VIDEO_UPLOAD_ID.eq(DSL.field("video_uploads.id", Long::class.java)))
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(DATE.greaterOrEqual(currentFrom))
            .and(DATE.lessOrEqual(now))
            .fetchOne()

        // **네 지표 모두 `?: 0L` 을 하지 않는다.** NULL 은 "수집 플랫폼의 행이 없다" 는 신호다.
        val currentViews = current?.get("total_views", Long::class.java)
        val currentLikes = current?.get("total_likes", Long::class.java)
        val currentComments = current?.get("total_comments", Long::class.java)
        val currentSubs = current?.get("total_subs", Long::class.java)

        // Previous period aggregates for change %
        val previous = dsl.select(
            measuredViews,
            measuredLikes,
            measuredComments,
            measuredSubscriberGain,
        )
            .from(ANALYTICS_DAILY)
            .join(VIDEO_UPLOADS).on(VIDEO_UPLOAD_ID.eq(DSL.field("video_uploads.id", Long::class.java)))
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(DATE.greaterOrEqual(previousFrom))
            .and(DATE.lessThan(currentFrom))
            .fetchOne()

        val previousViews = previous?.get("total_views", Long::class.java)
        val previousLikes = previous?.get("total_likes", Long::class.java)
        val previousSubs = previous?.get("total_subs", Long::class.java)

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

        /*
         * 증감은 **두 기간 모두 측정된 경우에만** 말할 수 있다. 예전에는 미측정이 `0` 으로
         * 접혀 `0 → 0` 이 "변화 없음" 이라는 관측으로 보였다.
         */
        fun measuredChange(previous: Long?, current: Long?): Double? =
            if (previous != null && current != null) calculateChangePercent(previous, current) else null

        return DashboardKpi(
            totalViews = currentViews,
            totalViewsChange = measuredChange(previousViews, currentViews),
            totalSubscribers = currentSubs,
            totalSubscribersChange = if (currentSubs != null && previousSubs != null) {
                currentSubs - previousSubs
            } else {
                null
            },
            totalLikes = currentLikes,
            totalLikesChange = measuredChange(previousLikes, currentLikes),
            totalComments = currentComments,
            creditBalance = balance + freeRemaining,
            creditTotal = balance + freeRemaining,
        )
    }

    override fun getTrendData(userId: Long, days: Int): List<TrendData> {
        val from = LocalDate.now().minusDays(days.toLong())
        val uploadIds = getUserUploadIds(userId)

        if (uploadIds.isEmpty()) return emptyList()

        val platformField = DSL.field("vu.platform::text", String::class.java)
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
            DSL.field("v.original_filename", String::class.java),
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
                DSL.field("v.file_size_bytes"),
                DSL.field("v.original_filename"), DSL.field("v.thumbnail_urls"),
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
                    originalFilename = record.get("original_filename", String::class.java),
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

        /*
         * **게시 시간 히트맵의 축은 `video_uploads.published_at` 이다.**
         *
         * 예전에는 요일을 `analytics_daily.date`(지표가 집계된 날)에서, 시각을
         * `analytics_daily.created_at`(그 행을 **저장한** 시각)에서 뽑았다. `created_at` 은
         * 게시 시각이 아니라 `AnalyticsSyncScheduler` 가 돈 시각이라, 히트맵은 사실상
         * **동기화 배치가 실행된 시간대**를 그리고 있었다. 스케줄러가 매일 같은 시간에
         * 돌면 모든 조회수가 그 한 칸에 쌓인다.
         *
         * 그 값은 화면에만 쓰이지 않는다. `SuggestScheduleUseCase` 가 유료 AI 프롬프트에
         * "N시=M조회" 로 그대로 넣어, 모델이 없는 근거로 업로드 시간을 추천했다.
         *
         * 게시된 적 없는 업로드(예약·초안)는 `published_at IS NULL` 이라 축이 없다 —
         * 0 시로 접어 넣지 않고 제외한다.
         */
        val dayOfWeek = DSL.field("EXTRACT(DOW FROM {0})", Int::class.java, PUBLISHED_AT).`as`("day_of_week")
        val hour = DSL.field("EXTRACT(HOUR FROM {0})", Int::class.java, PUBLISHED_AT).`as`("hour")
        val viewsSum = DSL.sum(VIEWS).`as`("total_views")

        val results = dsl.select(dayOfWeek, hour, viewsSum)
            .from(ANALYTICS_DAILY)
            .join(VIDEO_UPLOADS).on(VIDEO_UPLOAD_ID.eq(DSL.field("video_uploads.id", Long::class.java)))
            .where(VIDEO_UPLOAD_ID.`in`(uploadIds))
            .and(PUBLISHED_AT.isNotNull)
            /*
             * **조회수를 실제로 보고하는 플랫폼의 행만 더한다.**
             *
             * `TumblrClient.kt:141` 은 `views = total_notes`(좋아요+리블로그+답글 총합)다.
             * 조회수가 아닌 큰 숫자라 섞이면 그 시간대가 통째로 최적 시간으로 뽑힌다.
             * 수집하지 않는 플랫폼의 하드코딩 0 과 달리 합계 자체를 바꾼다.
             */
            .and(
                PLATFORM.cast(String::class.java)
                    .`in`(PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.VIEWS)),
            )
            .groupBy(dayOfWeek, hour)
            .fetch()

        val heatmap = mutableMapOf<String, MutableMap<Int, Long>>()
        val dayNames = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

        for (record in results) {
            /*
             * **축이 없으면 칸도 없다.** 예전에는 `?: 0` 이라 게시 시각을 모르는 행이
             * 전부 **일요일 0 시** 칸에 쌓였다. 그 시간에 올린 적이 없는데도 히트맵이
             * 그 칸을 채우고, 최적 시간으로 뽑히기까지 한다.
             */
            val dow = record.get("day_of_week", Int::class.java) ?: continue
            val h = record.get("hour", Int::class.java) ?: continue
            val dayName = dayNames.getOrNull(dow) ?: continue
            // 합계 0 은 "그 시간에 올렸고 조회가 없었다" 는 관측이므로 그대로 남긴다.
            val views = record.get("total_views", Long::class.java) ?: 0L

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
            .set(IMPRESSIONS, analytics.impressions)
            .set(AVG_VIEW_DURATION_SECONDS, analytics.avgViewDurationSeconds)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(ID.eq(id))
            .fetchOne()!!
            .toAnalyticsDaily()
    }

    /**
     * **수익 컬럼을 쓰지 않는다.** 예전에는 여기서 `revenue_micro` 까지 덮어썼는데,
     * 호출자(AnalyticsSyncScheduler)는 수익을 조회하지 않아 항상 기본값 0 을 넣었다.
     * 즉 6시간마다 실측 수익이 0 으로 지워졌을 것이다. 수익은 [updateRevenue] 만 쓴다.
     */
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
            .set(IMPRESSIONS, analytics.impressions)
            .set(AVG_VIEW_DURATION_SECONDS, analytics.avgViewDurationSeconds)
            .onConflict(VIDEO_UPLOAD_ID, DATE)
            .doUpdate()
            .set(VIEWS, analytics.views)
            .set(LIKES, analytics.likes)
            .set(COMMENTS_COUNT, analytics.commentsCount)
            .set(SHARES, analytics.shares)
            .set(WATCH_TIME_SECONDS, analytics.watchTimeSeconds)
            .set(SUBSCRIBER_GAINED, analytics.subscriberGained)
            .set(IMPRESSIONS, analytics.impressions)
            .set(AVG_VIEW_DURATION_SECONDS, analytics.avgViewDurationSeconds)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(ANALYTICS_DAILY)
            .where(ID.eq(id))
            .fetchOne()!!
            .toAnalyticsDaily()
    }

    /**
     * 이미 있는 행의 수익만 갱신한다. **INSERT 가 없다.**
     *
     * 조건이 둘이다.
     *
     * 1. `WHERE video_upload_id = ? AND date = ?` — 분석 행이 없는 날짜에는 아무것도
     *    만들지 않는다. 예전 upsert 는 조회 기간 30일 전체에 조회수 0, `created_at` =
     *    동기화 시각인 행을 만들었고, `getOptimalPublishTimes` 가 그 시각을 최적 업로드
     *    시간으로 추천했다. 읽는 쪽마다 가짜 행을 거르는 대신 만들지 않는 쪽을 택했다.
     * 2. **새 값이 MEASURED 이거나, 기존이 MEASURED 가 아닐 때만** 덮어쓴다. 권한이 끊겨
     *    PERMISSION_REQUIRED 가 오거나 확정이 지연돼 PENDING 이 와도 이미 확인한 금액은
     *    남는다. 읽고-판단하고-쓰지 않고 DB 가 승패를 정한다.
     *
     * `date` 가 파티션 키라 PostgreSQL 이 해당 월 파티션만 본다.
     */
    override fun updateRevenue(
        videoUploadId: Long,
        date: LocalDate,
        measurement: RevenueMeasurement,
    ): Boolean {
        val status = measurement.status.name
        val amount = measurement.amountMicro ?: 0L
        val currency = measurement.currency

        val updated = dsl.update(ANALYTICS_DAILY)
            .set(REVENUE_MICRO, amount)
            .set(REVENUE_CURRENCY, currency)
            .set(REVENUE_STATUS, status)
            .where(VIDEO_UPLOAD_ID.eq(videoUploadId))
            .and(DATE.eq(date))
            .and(
                DSL.value(status).eq(RevenueStatus.MEASURED.name)
                    .or(REVENUE_STATUS.ne(RevenueStatus.MEASURED.name)),
            )
            .execute()

        return updated > 0
    }

    override fun saveBatch(analytics: List<AnalyticsDaily>) {
        if (analytics.isEmpty()) return

        val insert = dsl.insertInto(
            ANALYTICS_DAILY,
            VIDEO_UPLOAD_ID, DATE, VIEWS, LIKES, COMMENTS_COUNT,
            SHARES, WATCH_TIME_SECONDS, SUBSCRIBER_GAINED, REVENUE_MICRO,
            IMPRESSIONS, AVG_VIEW_DURATION_SECONDS,
        )

        var batch = insert.values(null as Long?, null, null, null, null, null, null, null, null, null, null)
        // Use batch binding
        val batchBind = dsl.batch(
            dsl.insertInto(
                ANALYTICS_DAILY,
                VIDEO_UPLOAD_ID, DATE, VIEWS, LIKES, COMMENTS_COUNT,
                SHARES, WATCH_TIME_SECONDS, SUBSCRIBER_GAINED, REVENUE_MICRO,
                IMPRESSIONS, AVG_VIEW_DURATION_SECONDS,
            ).values(null as Long?, null, null, null, null, null, null, null, null, null, null)
        )

        for (a in analytics) {
            batchBind.bind(
                a.videoUploadId, a.date, a.views, a.likes, a.commentsCount,
                a.shares, a.watchTimeSeconds, a.subscriberGained, a.revenueMicro,
                a.impressions, a.avgViewDurationSeconds,
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
            uploadIdsQuery.and(DSL.field("vu.platform::text", String::class.java).eq(platform.name))
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

    override fun findLatestDateByVideoUploadId(videoUploadId: Long): LocalDate? {
        return dsl.select(DSL.max(DATE))
            .from(ANALYTICS_DAILY)
            .where(VIDEO_UPLOAD_ID.eq(videoUploadId))
            .fetchOne(0, LocalDate::class.java)
    }

    private fun getUserUploadIds(userId: Long): List<Long> =
        dsl.select(DSL.field("vu.id", Long::class.java))
            .from(DSL.table("video_uploads").`as`("vu"))
            .join(DSL.table("videos").`as`("v"))
            .on(DSL.field("v.id", Long::class.java).eq(DSL.field("vu.video_id", Long::class.java)))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            /*
             * 분석은 외부 플랫폼에 게시된 결과만을 근거로 해야 한다. 초안·처리 중·
             * 게시 여부 확인 불가(UNCONFIRMED) 행에 분석 행이 남아 있더라도 그것을
             * 대시보드·추세·상위 콘텐츠에 섞으면 아직 게시되지 않은 콘텐츠가 성과로
             * 보인다. 게시 확정의 정본은 업로드 상태이며, 히트맵처럼 게시 시각이 필요한
             * 소비자는 별도로 published_at IS NOT NULL 을 추가한다.
             */
            .and(DSL.field("vu.status::text", String::class.java).eq(UploadStatus.PUBLISHED.name))
            .fetch()
            .map { it.get(0, Long::class.java) }

    /**
     * 증감률 판정은 [MetricChange] 하나에 둔다. 여기에 다시 구현하면 "이전 기간이 0" 인
     * 경우의 처리가 갈라져, 화면마다 다른 숫자를 말하게 된다.
     */
    private fun calculateChangePercent(previous: Long, current: Long): Double? =
        MetricChange.percentChange(previous, current)

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
        revenueCurrency = get(REVENUE_CURRENCY)?.trim()?.ifBlank { null },
        revenueStatus = get(REVENUE_STATUS)
            ?.let { raw -> runCatching { RevenueStatus.valueOf(raw.trim()) }.getOrNull() }
            ?: RevenueStatus.UNSUPPORTED,
        impressions = get(IMPRESSIONS) ?: 0,
        avgViewDurationSeconds = get(AVG_VIEW_DURATION_SECONDS) ?: 0,
        createdAt = localDateTime(CREATED_AT),
    )

    override fun upsertChannelInsights(insights: ChannelInsightsDaily) {
        val trafficJsonb = JSONB.jsonb(objectMapper.writeValueAsString(insights.trafficSource))
        val ageJsonb = JSONB.jsonb(objectMapper.writeValueAsString(insights.demographicsAge))
        val genderJsonb = JSONB.jsonb(objectMapper.writeValueAsString(insights.demographicsGender))
        val countryJsonb = JSONB.jsonb(objectMapper.writeValueAsString(insights.demographicsCountry))

        val trafficField = DSL.field("traffic_source", JSONB::class.java)
        val ageField = DSL.field("demographics_age", JSONB::class.java)
        val genderField = DSL.field("demographics_gender", JSONB::class.java)
        val countryField = DSL.field("demographics_country", JSONB::class.java)

        dsl.insertInto(CHANNEL_INSIGHTS_DAILY)
            .set(USER_ID, insights.userId)
            .set(PLATFORM, insights.platform.name)
            .set(DATE, insights.date)
            .set(trafficField, trafficJsonb)
            .set(ageField, ageJsonb)
            .set(genderField, genderJsonb)
            .set(countryField, countryJsonb)
            .onConflict(USER_ID, PLATFORM, DATE)
            .doUpdate()
            .set(trafficField, trafficJsonb)
            .set(ageField, ageJsonb)
            .set(genderField, genderJsonb)
            .set(countryField, countryJsonb)
            .execute()
    }

    override fun findChannelInsights(
        userId: Long, platform: com.ongo.common.enums.Platform?, startDate: LocalDate, endDate: LocalDate
    ): List<ChannelInsightsDaily> {
        var condition = USER_ID.eq(userId)
            .and(DATE.greaterOrEqual(startDate))
            .and(DATE.lessOrEqual(endDate))
        if (platform != null) {
            condition = condition.and(PLATFORM.eq(platform.name))
        }
        return dsl.select().from(CHANNEL_INSIGHTS_DAILY)
            .where(condition)
            .orderBy(DATE.asc())
            .fetch()
            .map { it.toChannelInsights() }
    }

    override fun findCrossPlatformMetrics(userId: Long, days: Int): List<CrossPlatformRaw> {
        val from = LocalDate.now().minusDays(days.toLong())

        val videoIdField = DSL.field("v.id", Long::class.java)
        val videoTitleField = DSL.field("v.title", String::class.java)
        val platformField = DSL.field("vu.platform::text", String::class.java)
        val vuIdField = DSL.field("vu.id", Long::class.java)

        return dsl.select(
            videoIdField,
            videoTitleField,
            platformField,
            vuIdField,
            DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views"),
            DSL.sum(DSL.field("ad.likes", Int::class.java)).`as`("total_likes"),
            DSL.sum(DSL.field("ad.comments_count", Int::class.java)).`as`("total_comments"),
            DSL.sum(DSL.field("ad.shares", Int::class.java)).`as`("total_shares"),
            DSL.sum(DSL.field("ad.watch_time_seconds", Long::class.java)).`as`("total_watch_time"),
            DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue"),
            DSL.sum(DSL.field("ad.impressions", Int::class.java)).`as`("total_impressions"),
            DSL.avg(DSL.field("ad.avg_view_duration_seconds", Int::class.java)).`as`("avg_duration"),
        )
            .from(DSL.table("videos").`as`("v"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("vu.video_id", Long::class.java).eq(videoIdField))
            .join(DSL.table("analytics_daily").`as`("ad"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(vuIdField))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .groupBy(videoIdField, videoTitleField, platformField, vuIdField)
            .orderBy(videoIdField.asc(), platformField.asc())
            .fetch()
            .map { record ->
                CrossPlatformRaw(
                    videoId = record.get("id", Long::class.java),
                    videoTitle = record.get("title", String::class.java),
                    platform = record.get(platformField) ?: "UNKNOWN",
                    videoUploadId = record.get(vuIdField),
                    views = record.get("total_views", Long::class.java) ?: 0L,
                    likes = record.get("total_likes", Long::class.java) ?: 0L,
                    comments = record.get("total_comments", Long::class.java) ?: 0L,
                    shares = record.get("total_shares", Long::class.java) ?: 0L,
                    watchTimeSeconds = record.get("total_watch_time", Long::class.java) ?: 0L,
                    revenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                    impressions = record.get("total_impressions", Long::class.java) ?: 0L,
                    avgViewDurationSeconds = record.get("avg_duration", Long::class.java) ?: 0L,
                )
            }
    }

    override fun getDailyAggregates(userId: Long, from: LocalDate, to: LocalDate): List<DailyAggregate> {
        val uploadIds = getUserUploadIds(userId)
        if (uploadIds.isEmpty()) return emptyList()

        val dateField = DSL.field("ad.date", LocalDate::class.java)

        return dsl.select(
            dateField,
            DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views"),
            DSL.sum(DSL.field("ad.likes", Int::class.java)).`as`("total_likes"),
            DSL.sum(DSL.field("ad.comments_count", Int::class.java)).`as`("total_comments"),
            DSL.sum(DSL.field("ad.shares", Int::class.java)).`as`("total_shares"),
            DSL.sum(DSL.field("ad.watch_time_seconds", Long::class.java)).`as`("total_watch_time"),
            DSL.sum(DSL.field("ad.subscriber_gained", Int::class.java)).`as`("total_subs"),
            DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue"),
        )
            .from(DSL.table("analytics_daily").`as`("ad"))
            .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
            .and(dateField.greaterOrEqual(from))
            .and(dateField.lessOrEqual(to))
            .groupBy(dateField)
            .orderBy(dateField.asc())
            .fetch()
            .map { record ->
                DailyAggregate(
                    date = record.get("date", LocalDate::class.java),
                    views = record.get("total_views", Long::class.java) ?: 0L,
                    likes = record.get("total_likes", Long::class.java) ?: 0L,
                    comments = record.get("total_comments", Long::class.java) ?: 0L,
                    shares = record.get("total_shares", Long::class.java) ?: 0L,
                    watchTimeSeconds = record.get("total_watch_time", Long::class.java) ?: 0L,
                    subscriberGained = record.get("total_subs", Long::class.java) ?: 0L,
                    revenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                )
            }
    }

    override fun findCrossPlatformDetailMetrics(userId: Long, days: Int): List<CrossPlatformDetailRaw> {
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        val videoIdField = DSL.field("v.id", Long::class.java)
        val videoTitleField = DSL.field("v.title", String::class.java)
        val thumbnailField = DSL.field("v.thumbnail_urls")
        /*
         * **게시 시각은 `video_uploads.published_at` 이다.**
         *
         * 예전에는 `v.created_at` — 우리 DB 에 영상 **레코드가 만들어진 시각** — 을 읽어
         * `CrossPlatformDetailRaw.publishedAt` 에 넣었다. 업로드 준비 시각과 실제 게시
         * 시각은 다르고, 예약 게시를 쓰면 며칠씩 벌어진다.
         *
         * 최적 게시시간 추천이 이 값을 요일·시각으로 쓰므로 원천이 틀리면 추천 전체가
         * 틀린다. 아직 게시되지 않은 업로드는 `NULL` 이며, 소비자는 그런 행을 표본에서
         * 빼야 한다 — 0 이나 정오로 채우면 안 된다.
         */
        val publishedAtField = DSL.field("vu.published_at", java.time.LocalDateTime::class.java)
        val platformField = DSL.field("vu.platform::text", String::class.java)
        val vuIdField = DSL.field("vu.id", Long::class.java)

        return dsl.select(
            videoIdField,
            videoTitleField,
            thumbnailField,
            publishedAtField,
            platformField,
            vuIdField,
            DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views"),
            DSL.sum(DSL.field("ad.likes", Int::class.java)).`as`("total_likes"),
            DSL.sum(DSL.field("ad.comments_count", Int::class.java)).`as`("total_comments"),
            DSL.sum(DSL.field("ad.shares", Int::class.java)).`as`("total_shares"),
            DSL.sum(DSL.field("ad.watch_time_seconds", Long::class.java)).`as`("total_watch_time"),
            DSL.sum(DSL.field("ad.revenue_micro", Long::class.java)).`as`("total_revenue"),
            DSL.sum(DSL.field("ad.impressions", Int::class.java)).`as`("total_impressions"),
            DSL.avg(DSL.field("ad.avg_view_duration_seconds", Int::class.java)).`as`("avg_duration"),
        )
            .from(DSL.table("videos").`as`("v"))
            .join(DSL.table("video_uploads").`as`("vu"))
            .on(DSL.field("vu.video_id", Long::class.java).eq(videoIdField))
            .join(DSL.table("analytics_daily").`as`("ad"))
            .on(DSL.field("ad.video_upload_id", Long::class.java).eq(vuIdField))
            .where(DSL.field("v.user_id", Long::class.java).eq(userId))
            .and(DSL.field("ad.date", LocalDate::class.java).greaterOrEqual(from))
            .and(DSL.field("ad.date", LocalDate::class.java).lessOrEqual(to))
            .groupBy(videoIdField, videoTitleField, thumbnailField, publishedAtField, platformField, vuIdField)
            .orderBy(videoIdField.asc(), platformField.asc())
            .fetch()
            .map { record ->
                @Suppress("UNCHECKED_CAST")
                val thumbRaw = record.get("thumbnail_urls")
                val thumbnailUrls: List<String> = when (thumbRaw) {
                    is Array<*> -> (thumbRaw as Array<String>).toList()
                    else -> emptyList()
                }

                CrossPlatformDetailRaw(
                    videoId = record.get("id", Long::class.java),
                    videoTitle = record.get("title", String::class.java),
                    thumbnailUrls = thumbnailUrls,
                    publishedAt = record.get(publishedAtField),
                    platform = record.get(platformField) ?: "UNKNOWN",
                    videoUploadId = record.get(vuIdField),
                    views = record.get("total_views", Long::class.java) ?: 0L,
                    likes = record.get("total_likes", Long::class.java) ?: 0L,
                    comments = record.get("total_comments", Long::class.java) ?: 0L,
                    shares = record.get("total_shares", Long::class.java) ?: 0L,
                    watchTimeSeconds = record.get("total_watch_time", Long::class.java) ?: 0L,
                    revenueMicro = record.get("total_revenue", Long::class.java) ?: 0L,
                    impressions = record.get("total_impressions", Long::class.java) ?: 0L,
                    avgViewDurationSeconds = record.get("avg_duration", Long::class.java) ?: 0L,
                )
            }
    }

    private fun Record.toChannelInsights(): ChannelInsightsDaily {
        fun <V> parseJsonMap(fieldName: String, valueType: Class<V>): Map<String, V> {
            val rawValue = get(fieldName) ?: return emptyMap()
            val raw = when (rawValue) {
                is JSONB -> rawValue.data()
                else -> rawValue.toString()
            }
            return try {
                objectMapper.readValue(raw, objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, valueType))
            } catch (_: Exception) { emptyMap() }
        }
        return ChannelInsightsDaily(
            id = get(ID),
            userId = get(USER_ID),
            platform = com.ongo.common.enums.Platform.valueOf(get(PLATFORM)),
            date = localDate(DATE)!!,
            trafficSource = parseJsonMap("traffic_source", Long::class.javaObjectType),
            demographicsAge = parseJsonMap("demographics_age", Double::class.javaObjectType),
            demographicsGender = parseJsonMap("demographics_gender", Double::class.javaObjectType),
            demographicsCountry = parseJsonMap("demographics_country", Long::class.javaObjectType),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
