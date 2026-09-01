package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.analytics.AnalyticsRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.time.LocalDateTime

/**
 * 게시 시간 히트맵이 **실제 스키마에서** 어떤 값을 내는지 고정한다.
 *
 * ## 왜 실제 DB 인가
 *
 * 이 수정의 핵심은 `WHERE` 조건과 조인이다. Mock 커넥션은 SQL 문자열만 볼 수 있어
 * "Tumblr 행이 실제로 빠졌는가" 를 증명하지 못한다 — 필터가 없어도 mock 은 같은 행을
 * 돌려주기 때문이다. 그래서 값 계약은 Postgres 에 대고 검증한다.
 * SQL 모양 자체는 [HeatmapQueryContractTest] 가 Docker 없이도 지킨다.
 *
 * ## 무엇이 거짓이었나
 *
 * 축이 `analytics_daily.date`(집계일)와 `analytics_daily.created_at`(행 저장 시각)이었다.
 * `created_at` 은 `AnalyticsSyncScheduler` 가 돈 시각이라, 히트맵은 **동기화 배치
 * 시간대**를 그렸다. 그 값은 `SuggestScheduleUseCase` 의 유료 AI 프롬프트에도 들어간다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class HeatmapPublishedAtIT {

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

        /** 2026-08-05 는 **수요일**(EXTRACT(DOW) = 3). */
        private val PUBLISHED_WEDNESDAY_14H: LocalDateTime = LocalDateTime.of(2026, 8, 5, 14, 30)

        /** 집계 행이 저장된 날/시각. 게시 시각과 일부러 다르게 둔다. */
        private val METRIC_DATE: LocalDate = LocalDate.of(2026, 8, 20)
        private val ROW_WRITTEN_AT: LocalDateTime = LocalDateTime.of(2026, 8, 20, 3, 0)

        private const val WEDNESDAY = "WED"
        private const val SUNDAY = "SUN"
        private const val PUBLISH_HOUR = 14
        private const val SYNC_HOUR = 3
    }

    private var userId = 0L
    private var videoId = 0L

    @BeforeEach
    fun setup() {
        dsl.execute("DELETE FROM analytics_daily")
        dsl.execute("DELETE FROM video_uploads")
        dsl.execute("DELETE FROM videos")
        dsl.execute("DELETE FROM users")

        userId = dsl.fetchOne(
            """
            INSERT INTO users (email, name, provider, provider_id)
            VALUES ('heatmap@test.local', '히트맵', 'GOOGLE', 'heatmap-1')
            RETURNING id
            """.trimIndent(),
        )!!.get(0, Long::class.java)

        videoId = dsl.fetchOne(
            "INSERT INTO videos (user_id, title) VALUES (?, ?) RETURNING id",
            userId,
            "영상",
        )!!.get(0, Long::class.java)
    }

    /** @return 생성된 `video_uploads.id` */
    private fun upload(platform: String, publishedAt: LocalDateTime?): Long =
        dsl.fetchOne(
            """
            INSERT INTO video_uploads (video_id, platform, status, published_at)
            VALUES (?, CAST(? AS platform_type), CAST('PUBLISHED' AS upload_status), ?)
            RETURNING id
            """.trimIndent(),
            videoId,
            platform,
            publishedAt,
        )!!.get(0, Long::class.java)

    private fun analytics(uploadId: Long, views: Int, date: LocalDate = METRIC_DATE) {
        dsl.execute(
            "INSERT INTO analytics_daily (video_upload_id, date, views, created_at) VALUES (?, ?, ?, ?)",
            uploadId,
            date,
            views,
            ROW_WRITTEN_AT,
        )
    }

    private fun heatmap() = analyticsRepository.getHeatmapData(userId)

    // ══ 1) published_at 과 created_at 시간 불일치 ═══════════════════════════

    /**
     * **이 케이스가 동기화 배치 시각을 최적 업로드 시간으로 그리던 자리다.**
     *
     * 게시는 수요일 14 시, 집계 행 저장은 목요일 3 시다. 예전 축(`date` + `created_at`)
     * 이면 목요일 3 시 칸에 들어갔다.
     */
    @Test
    @DisplayName("게시 시각 칸에 넣고 행 저장 시각 칸에는 넣지 않는다")
    fun usesPublishedAtNotCreatedAt() {
        analytics(upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H), views = 1_000)

        val heatmap = heatmap()

        assertEquals(1_000L, heatmap[WEDNESDAY]?.get(PUBLISH_HOUR), "게시 시각 칸이 비었다: $heatmap")
        assertNull(heatmap[WEDNESDAY]?.get(SYNC_HOUR), "동기화 저장 시각 칸에 값이 들어갔다: $heatmap")
        // 집계일(2026-08-20)은 목요일이다. 그 요일이 나오면 축이 되돌아간 것이다.
        assertNull(heatmap["THU"], "집계일 요일로 그렸다: $heatmap")
        assertEquals(setOf(WEDNESDAY), heatmap.keys, "게시 요일 외의 칸을 만들었다: $heatmap")
    }

    // ══ 2) Tumblr 혼합 오염 ═════════════════════════════════════════════════

    /**
     * `TumblrClient.kt:141` 은 `views = total_notes`(좋아요+리블로그+답글 총합)다.
     * 섞이면 그 시간대가 통째로 최적 시간으로 뽑힌다.
     */
    @Test
    @DisplayName("Tumblr 노트 총합은 조회수에 섞이지 않는다")
    fun tumblrNotesAreExcluded() {
        analytics(upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H), views = 400)
        analytics(upload("TUMBLR", PUBLISHED_WEDNESDAY_14H), views = 900_000)

        assertEquals(
            400L,
            heatmap()[WEDNESDAY]?.get(PUBLISH_HOUR),
            "노트 총합이 조회수에 섞였다",
        )
    }

    /** 조회수를 주지 않는 플랫폼만 있으면 그릴 것이 없다 — 0 을 만들지 않는다. */
    @Test
    @DisplayName("조회수 미수집 플랫폼만 있으면 빈 데이터다")
    fun unsupportedOnlyProducesEmptyData() {
        analytics(upload("TUMBLR", PUBLISHED_WEDNESDAY_14H), views = 900_000)

        assertTrue(heatmap().isEmpty(), "재지 않은 칸을 만들었다: ${heatmap()}")
    }

    // ══ 3) published_at 이 null ═════════════════════════════════════════════

    /**
     * 예약·초안 업로드는 게시 시각이 없다. 예전 매핑은 `?: 0` 이라 **일요일 0 시** 칸에
     * 몰아넣었다 — 그 시간에 올린 적이 없는데 히트맵이 그 칸을 채운다.
     */
    @Test
    @DisplayName("게시되지 않은 업로드는 일요일 0시로 접히지 않는다")
    fun unpublishedUploadDoesNotFoldIntoSundayMidnight() {
        analytics(upload("YOUTUBE", null), views = 5_000)

        val heatmap = heatmap()

        assertTrue(heatmap.isEmpty(), "게시된 적 없는 업로드를 그렸다: $heatmap")
        assertNull(heatmap[SUNDAY]?.get(0), "일요일 0시 칸으로 접혔다: $heatmap")
    }

    /** 게시된 업로드와 섞여 있어도 미게시분만 빠져야 한다. */
    @Test
    @DisplayName("미게시 업로드만 빼고 게시분은 남긴다")
    fun onlyUnpublishedIsDropped() {
        analytics(upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H), views = 700)
        analytics(upload("TIKTOK", null), views = 5_000)

        val heatmap = heatmap()

        assertEquals(700L, heatmap[WEDNESDAY]?.get(PUBLISH_HOUR))
        assertNull(heatmap[SUNDAY], "미게시분이 일요일 칸을 만들었다: $heatmap")
    }

    // ══ 4) 실측 0 ═══════════════════════════════════════════════════════════

    /**
     * **행이 있고 합이 0 이면 그 0 은 관측이다.** "그 시간에 올렸는데 조회가 없었다" 는
     * 사실이므로 칸을 지우면 안 된다.
     */
    @Test
    @DisplayName("측정된 0 조회수는 0 칸으로 남는다")
    fun measuredZeroStaysAsAZeroCell() {
        analytics(upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H), views = 0)

        val heatmap = heatmap()

        assertEquals(0L, heatmap[WEDNESDAY]?.get(PUBLISH_HOUR), "실측 0 칸을 지웠다: $heatmap")
        assertTrue(heatmap.containsKey(WEDNESDAY))
    }

    /** 같은 요일·시각의 여러 게시물은 합산된다(기존 동작). */
    @Test
    @DisplayName("같은 칸의 게시물은 합산한다")
    fun sameCellIsSummed() {
        analytics(upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H), views = 300)
        analytics(upload("TIKTOK", PUBLISHED_WEDNESDAY_14H.withMinute(59)), views = 200)

        assertEquals(500L, heatmap()[WEDNESDAY]?.get(PUBLISH_HOUR))
    }

    /** 집계 행이 하나도 없으면 빈 데이터다 — 업로드만으로 칸을 만들지 않는다. */
    @Test
    @DisplayName("집계 행이 없으면 빈 데이터다")
    fun noAnalyticsRowsProduceEmptyData() {
        upload("YOUTUBE", PUBLISHED_WEDNESDAY_14H)

        assertTrue(heatmap().isEmpty(), "수집 전 상태를 0 칸으로 그렸다: ${heatmap()}")
    }
}
