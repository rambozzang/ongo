package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.analytics.AnalyticsRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * 첫 화면 KPI 가 **실제 스키마에서** 어떤 값을 내는지 고정한다.
 *
 * ## 왜 실제 DB 인가
 *
 * 이 수정의 핵심은 `CASE WHEN platform IN (...)` 이 만드는 **NULL 신호**다. Mock
 * 커넥션은 SQL 문자열만 볼 수 있어 "Tumblr 행이 실제로 빠졌는가", "일치하는 행이 없을 때
 * 합계가 NULL 인가" 를 증명하지 못한다. SQL 모양은
 * [DashboardKpiMetricQueryContractTest] 가 Docker 없이도 지킨다.
 *
 * ## 무엇이 거짓이었나
 *
 * `SUM(views)`·`SUM(likes)`·`SUM(comments_count)` 에 플랫폼 필터가 없었다. Tumblr 의
 * `total_notes`(노트 총합)가 조회수로, Pinterest 의 `SAVE`(저장)가 좋아요로 들어가
 * **사용자가 처음 보는 숫자**를 바꿨다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class DashboardKpiMeasurementIT {

    @Autowired lateinit var analyticsRepository: AnalyticsRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test"); withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }

        private const val DAYS = 7
    }

    private var userId = 0L
    private var videoId = 0L

    /** 현재 기간(최근 7일) 안. */
    private val inCurrent: LocalDate = LocalDate.now().minusDays(2)

    /** 이전 기간(그 앞 7일) 안. */
    private val inPrevious: LocalDate = LocalDate.now().minusDays(9)

    @BeforeEach
    fun setup() {
        dsl.execute("DELETE FROM analytics_daily")
        dsl.execute("DELETE FROM video_uploads")
        dsl.execute("DELETE FROM videos")
        dsl.execute("DELETE FROM users")

        userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id)
            VALUES ('kpi@test.local', 'KPI', 'GOOGLE', 'kpi-1')
            RETURNING id
            """.trimIndent(),
        )!!.get(0, Long::class.java)

        videoId = dsl.fetchOne(
            "INSERT INTO videos (user_id, title) VALUES (?, ?) RETURNING id",
            userId,
            "영상",
        )!!.get(0, Long::class.java)
    }

    private fun upload(platform: String): Long =
        dsl.fetchOne(
            """
            INSERT INTO video_uploads (video_id, platform, status)
            VALUES (?, CAST(? AS platform_type), CAST('PUBLISHED' AS upload_status))
            RETURNING id
            """.trimIndent(),
            videoId,
            platform,
        )!!.get(0, Long::class.java)

    private fun analytics(
        uploadId: Long,
        date: LocalDate,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        subscriberGained: Int = 0,
    ) {
        dsl.execute(
            """
            INSERT INTO analytics_daily
                (video_upload_id, date, views, likes, comments_count, subscriber_gained)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            uploadId,
            date,
            views,
            likes,
            comments,
            subscriberGained,
        )
    }

    private fun kpi() = analyticsRepository.getDashboardKpi(userId, DAYS)

    // ══ 1) 혼합 오염 ════════════════════════════════════════════════════════

    /** **이 케이스가 노트 총합 90 만을 첫 화면 조회수로 올리던 자리다.** */
    @Test
    @DisplayName("Tumblr 노트 총합은 조회수 KPI 에 섞이지 않는다")
    fun tumblrNotesDoNotEnterViews() {
        analytics(upload("YOUTUBE"), inCurrent, views = 400)
        analytics(upload("TUMBLR"), inCurrent, views = 900_000)

        assertEquals(400L, kpi().totalViews, "노트 총합이 조회수에 섞였다")
    }

    /** Pinterest 의 `SAVE` 는 좋아요가 아니다. */
    @Test
    @DisplayName("Pinterest 저장 수는 좋아요 KPI 에 섞이지 않는다")
    fun pinterestSavesDoNotEnterLikes() {
        analytics(upload("YOUTUBE"), inCurrent, likes = 30)
        analytics(upload("PINTEREST"), inCurrent, likes = 5_000)

        assertEquals(30L, kpi().totalLikes, "저장 수가 좋아요에 섞였다")
    }

    /** Pinterest 는 댓글을 조회하지도 않고 `0` 을 채운다. */
    @Test
    @DisplayName("댓글도 수집 플랫폼 행만 더한다")
    fun commentsSumOnlyReportingRows() {
        analytics(upload("YOUTUBE"), inCurrent, comments = 12)
        analytics(upload("PINTEREST"), inCurrent, comments = 0)

        assertEquals(12L, kpi().totalComments)
    }

    /** 조회수를 주는 플랫폼끼리는 정상 합산된다 — 과도한 차단 회귀 방지. */
    @Test
    @DisplayName("수집하는 플랫폼끼리는 정상적으로 합산한다")
    fun reportingPlatformsAreSummedTogether() {
        analytics(upload("YOUTUBE"), inCurrent, views = 300)
        analytics(upload("TIKTOK"), inCurrent, views = 200)

        assertEquals(500L, kpi().totalViews)
    }

    // ══ 2) unsupported-only ═════════════════════════════════════════════════

    /** 조회수를 주는 플랫폼이 없으면 합계 0 은 "0 회" 가 아니다. */
    @Test
    @DisplayName("조회수 미수집 플랫폼만 있으면 null 이다")
    fun unsupportedOnlyViewsAreNull() {
        analytics(upload("TUMBLR"), inCurrent, views = 900_000)

        val kpi = kpi()

        assertNull(kpi.totalViews, "재지 않은 조회수를 숫자로 내보냈다")
        assertNull(kpi.totalViewsChange, "잴 수 없는 지표의 증감을 만들었다")
    }

    @Test
    @DisplayName("좋아요 미수집 플랫폼만 있으면 null 이다")
    fun unsupportedOnlyLikesAreNull() {
        analytics(upload("PINTEREST"), inCurrent, likes = 5_000, views = 100)

        val kpi = kpi()

        assertNull(kpi.totalLikes, "저장 수를 좋아요로 내보냈다")
        // 같은 행의 조회수는 Pinterest 도 보고한다 — 함께 죽으면 안 된다.
        assertEquals(100L, kpi.totalViews, "수집하는 지표까지 함께 끊었다")
    }

    /** 게시한 적이 없으면 어떤 지표도 물어볼 곳이 없다. */
    @Test
    @DisplayName("업로드가 없으면 네 지표 모두 null 이다")
    fun noUploadsProduceNulls() {
        val kpi = kpi()

        assertNull(kpi.totalViews)
        assertNull(kpi.totalLikes)
        assertNull(kpi.totalComments)
        assertNull(kpi.totalSubscribers)
    }

    @Test
    @DisplayName("게시되지 않은 업로드의 분석 행은 KPI 에 섞이지 않는다")
    fun unpublishedUploadAnalyticsAreExcluded() {
        val draftUploadId = dsl.fetchOne(
            """
            INSERT INTO video_uploads (video_id, platform, status)
            VALUES (?, CAST(? AS platform_type), CAST('DRAFT' AS upload_status))
            RETURNING id
            """.trimIndent(),
            videoId,
            "YOUTUBE",
        )!!.get(0, Long::class.java)
        analytics(draftUploadId, inCurrent, views = 99_999)

        val kpi = kpi()

        assertNull(kpi.totalViews, "게시되지 않은 분석 행을 조회수 KPI 에 포함했다")
    }

    // ══ 3) 실측 0 ═══════════════════════════════════════════════════════════

    /** **행이 있고 합이 0 이면 그 0 은 관측이다.** 과도한 null 처리 회귀를 막는다. */
    @Test
    @DisplayName("수집 플랫폼의 측정된 0 은 0 으로 남는다")
    fun measuredZeroStaysZero() {
        analytics(upload("YOUTUBE"), inCurrent, views = 0, likes = 0, comments = 0)

        val kpi = kpi()

        assertEquals(0L, kpi.totalViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(0L, kpi.totalLikes)
        assertEquals(0L, kpi.totalComments)
    }

    /** 오염 행이 함께 있어도 실측 0 은 살아남아야 한다. */
    @Test
    @DisplayName("혼합 상태의 실측 0 도 0 으로 남는다")
    fun measuredZeroSurvivesMixedPlatforms() {
        analytics(upload("YOUTUBE"), inCurrent, views = 0)
        analytics(upload("TUMBLR"), inCurrent, views = 900_000)

        assertEquals(0L, kpi().totalViews, "실측 0 을 미측정으로 감췄다")
    }

    // ══ 4) 현재/이전 기간 측정 가용성 ═══════════════════════════════════════

    /** 두 기간 모두 측정됐을 때만 증감이 성립한다. */
    @Test
    @DisplayName("두 기간이 모두 측정되면 증감률을 계산한다")
    fun changeIsComputedWhenBothPeriodsAreMeasured() {
        val youtube = upload("YOUTUBE")
        analytics(youtube, inPrevious, views = 100, likes = 10)
        analytics(youtube, inCurrent, views = 150, likes = 20)

        val kpi = kpi()

        assertEquals(150L, kpi.totalViews)
        assertEquals(50.0, kpi.totalViewsChange)
        assertEquals(100.0, kpi.totalLikesChange)
    }

    /** **이전 기간이 미측정이면 증감을 만들지 않는다.** */
    @Test
    @DisplayName("이전 기간에 측정 행이 없으면 증감이 null 이다")
    fun noPreviousMeasurementProducesNullChange() {
        analytics(upload("YOUTUBE"), inCurrent, views = 150)

        val kpi = kpi()

        assertEquals(150L, kpi.totalViews, "현재 측정값까지 버렸다")
        assertNull(kpi.totalViewsChange, "비교할 이전 관측이 없는데 증감을 만들었다")
    }

    /**
     * 이전 기간 행이 **오염된 플랫폼뿐**이면 그 기간은 측정되지 않은 것이다.
     * 필터가 없던 시절에는 노트 총합이 기준선이 되어 "▼99%" 같은 폭락이 나왔다.
     */
    @Test
    @DisplayName("이전 기간이 미수집 플랫폼뿐이면 증감이 null 이다")
    fun unsupportedPreviousPeriodProducesNullChange() {
        analytics(upload("TUMBLR"), inPrevious, views = 900_000)
        analytics(upload("YOUTUBE"), inCurrent, views = 150)

        val kpi = kpi()

        assertEquals(150L, kpi.totalViews)
        assertNull(kpi.totalViewsChange, "노트 총합을 기준선으로 삼아 폭락을 만들었다")
    }

    /** 현재 기간이 미측정이면 값도 증감도 없다. */
    @Test
    @DisplayName("현재 기간에 측정 행이 없으면 값과 증감이 모두 null 이다")
    fun noCurrentMeasurementProducesNulls() {
        analytics(upload("YOUTUBE"), inPrevious, views = 100)

        val kpi = kpi()

        assertNull(kpi.totalViews, "이번 기간을 재지 않았는데 0 을 내보냈다")
        assertNull(kpi.totalViewsChange)
    }

    /** **측정된 두 기간이 같으면 0% 는 사실이다.** */
    @Test
    @DisplayName("측정된 변화 없음은 0% 로 남는다")
    fun measuredNoChangeStaysZeroPercent() {
        val youtube = upload("YOUTUBE")
        analytics(youtube, inPrevious, views = 500)
        analytics(youtube, inCurrent, views = 500)

        assertEquals(0.0, kpi().totalViewsChange, "실측 0% 를 비교 불가로 감췄다")
    }

    // ══ 5) 선행 작업 회귀 방지 ══════════════════════════════════════════════

    /** 구독 증가 필터는 이미 걸려 있었다 — 되돌리지 않았는지 확인한다. */
    @Test
    @DisplayName("구독 증가 계약을 회귀시키지 않는다")
    fun subscriberContractIsNotRegressed() {
        analytics(upload("TIKTOK"), inCurrent, views = 100, subscriberGained = 0)

        assertNull(kpi().totalSubscribers, "구독 증가 필터가 사라졌다")
    }

    @Test
    @DisplayName("YouTube 행이 있으면 구독 증가 실측 0 을 보존한다")
    fun subscriberMeasuredZeroIsPreserved() {
        analytics(upload("YOUTUBE"), inCurrent, subscriberGained = 0)

        assertEquals(0L, kpi().totalSubscribers)
    }
}
