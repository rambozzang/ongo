package com.ongo.application.metarewrite

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.result.MetaRewriteResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 메타데이터 리라이트 **유료 프롬프트**에 재지 않은 숫자가 들어가지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `getDailyAggregates` 는 `analytics_daily` 를 **날짜로만** 묶는다. 플랫폼이 없으니
 *
 * - `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합) → **총 조회수**
 * - `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭) → **참여율 분자**
 *
 * 로 들어갔다. 하드코딩 0 과 달리 **다른 뜻의 큰 숫자**라, 모델은 그것을 성과로 읽고
 * "조회수 90 만의 고성과 영상" 을 전제로 제목을 다시 쓴다. 유료 호출이라 대가까지 치른다.
 *
 * 수집하는 플랫폼이 하나도 없으면 그 자리에는 숫자가 아니라 **문장**이 들어가야 한다.
 * 반대로 **측정된 0 은 관측**이므로 그대로 보낸다.
 */
class MetaRewritePromptMeasurementTest {

    private val videoRepository = mockk<VideoRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()

    /**
     * **relaxed mock 으로 두면 안 된다.** `withCredits` 는 블록을 실행해 결과를 돌려주는
     * 함수인데, relaxed mock 은 블록을 부르지 않는다. 그러면 AI 도 호출되지 않아
     * 아래 검증이 아무것도 검사하지 않으면서 통과한다.
     */
    private val creditService = mockk<CreditService>()

    private val useCase = MetaRewriteUseCase(
        videoRepository = videoRepository,
        analyticsRepository = analyticsRepository,
        videoUploadRepository = videoUploadRepository,
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = mockk<AiRateLimiter>(relaxed = true),
    )

    private val userId = 7L
    private val videoId = 3L

    private fun row(views: Int, likes: Int = 0, comments: Int = 0) = AnalyticsDaily(
        videoUploadId = 11L,
        date = LocalDate.now().minusDays(2),
        views = views,
        likes = likes,
        commentsCount = comments,
    )

    /** 유스케이스를 실제로 돌리고 AI 에게 간 user 프롬프트를 돌려준다. */
    private fun renderedPrompt(uploadPlatform: Platform?, rows: List<AnalyticsDaily>): String {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "원본 제목",
            description = "원본 설명",
            tags = listOf("태그"),
        )

        val uploads: List<VideoUpload> = uploadPlatform
            ?.let { listOf(VideoUpload(id = 11L, videoId = videoId, platform = it, channelId = 1L)) }
            ?: emptyList()
        every { videoUploadRepository.findByUserId(userId) } returns uploads
        every { videoUploadRepository.findByVideoId(videoId) } returns uploads
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            rows.groupBy { it.videoUploadId }

        every {
            creditService.withCredits(userId, AiFeature.META_REWRITE, any<() -> Any>())
        } answers { thirdArg<() -> Any>().invoke() }

        val userPrompt = slot<String>()
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(userId) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(userPrompt)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.entity(MetaRewriteResult::class.java) } returns MetaRewriteResult(
            suggestedTitle = "새 제목",
            suggestedDescription = "새 설명",
            suggestedTags = emptyList(),
            reasoning = "이유",
            expectedImpactPercent = 0,
        )

        useCase.rewriteMeta(userId, videoId)
        return userPrompt.captured
    }

    // ── 조회수 ──────────────────────────────────────────────────────────────

    /** **이 케이스가 노트 총합 90 만을 "총 조회수" 로 보내던 자리다.** */
    @Test
    @DisplayName("Tumblr 노트 총합을 총 조회수로 보내지 않는다")
    fun tumblrNotesNeverBecomeViews() {
        val prompt = renderedPrompt(Platform.TUMBLR, listOf(row(views = 900_000)))

        assertFalse("900000" in prompt, "노트 총합이 조회수로 들어갔다:\n$prompt")
        assertTrue(
            "총 조회수: ${MetaRewriteUseCase.NOT_COLLECTED}" in prompt,
            "미수집을 알리지 않았다:\n$prompt",
        )
        assertFalse("총 조회수: 0" in prompt, "0 을 관측처럼 보냈다:\n$prompt")
    }

    /** **행이 있고 합이 0 이면 그 0 은 실측이다.** */
    @Test
    @DisplayName("측정된 0 조회수는 숫자로 보존한다")
    fun measuredZeroViewsStayANumber() {
        val prompt = renderedPrompt(Platform.YOUTUBE, listOf(row(views = 0)))

        assertTrue("총 조회수: 0" in prompt, "실측 0 을 문장으로 감췄다:\n$prompt")
    }

    @Test
    @DisplayName("측정된 조회수는 그대로 보낸다")
    fun measuredViewsReachThePrompt() {
        val prompt = renderedPrompt(Platform.YOUTUBE, listOf(row(views = 1_200), row(views = 800)))

        assertTrue("총 조회수: 2000" in prompt, "측정값이 훼손됐다:\n$prompt")
        assertFalse(MetaRewriteUseCase.NOT_COLLECTED in prompt)
    }

    /** 게시 기록이 아예 없으면 물어볼 곳도 없다. */
    @Test
    @DisplayName("게시 기록이 없으면 조회수를 만들지 않는다")
    fun noUploadsProduceNoViews() {
        val prompt = renderedPrompt(uploadPlatform = null, rows = emptyList())

        assertTrue("총 조회수: ${MetaRewriteUseCase.NOT_COLLECTED}" in prompt)
    }

    // ── 참여율 ──────────────────────────────────────────────────────────────

    /**
     * Pinterest 는 `SAVE` 를 좋아요로 매핑하고 댓글은 `0` 으로 하드코딩한다
     * (`PinterestClient.kt:158/159`). 참여율의 분자로 쓸 수 없다.
     */
    @Test
    @DisplayName("Pinterest 저장 수로 참여율을 만들지 않는다")
    fun pinterestSavesNeverBecomeEngagement() {
        val prompt = renderedPrompt(
            Platform.PINTEREST,
            listOf(row(views = 1_000, likes = 500, comments = 0)),
        )

        assertTrue(
            "참여율(좋아요+댓글/조회수): ${MetaRewriteUseCase.NOT_COLLECTED}" in prompt,
            "저장 수를 참여율로 계산했다:\n$prompt",
        )
        assertFalse("50.00%" in prompt, "저장 수 기반 참여율이 새어 나갔다:\n$prompt")
    }

    @Test
    @DisplayName("측정된 참여율은 단위와 함께 그대로 보낸다")
    fun measuredEngagementReachesThePrompt() {
        val prompt = renderedPrompt(
            Platform.YOUTUBE,
            listOf(row(views = 1_000, likes = 80, comments = 20)),
        )

        // (80 + 20) / 1,000 = 10.00%
        assertTrue("참여율(좋아요+댓글/조회수): 10.00%" in prompt, "참여율이 훼손됐다:\n$prompt")
    }

    /** 조회수가 0 이면 비율의 분모가 없다. `0.00%` 는 "참여가 없었다" 라는 거짓 관측이다. */
    @Test
    @DisplayName("조회수가 0이면 참여율을 만들지 않는다")
    fun zeroViewsProduceNoEngagementRate() {
        val prompt = renderedPrompt(Platform.YOUTUBE, listOf(row(views = 0, likes = 0)))

        assertFalse("0.00%" in prompt, "분모 없는 비율을 만들었다:\n$prompt")
        assertTrue("참여율(좋아요+댓글/조회수): ${MetaRewriteUseCase.NOT_COLLECTED}" in prompt)
    }

    /** 조회수가 있고 참여가 실제로 0 이면 그 0% 는 관측이다. */
    @Test
    @DisplayName("측정된 0% 참여율은 보존한다")
    fun measuredZeroEngagementIsPreserved() {
        val prompt = renderedPrompt(Platform.YOUTUBE, listOf(row(views = 1_000, likes = 0)))

        assertTrue("참여율(좋아요+댓글/조회수): 0.00%" in prompt, "실측 0% 를 감췄다:\n$prompt")
    }

    // ── 문구 계약 ────────────────────────────────────────────────────────────

    /**
     * 단위(`%`)는 **값이 들고 있어야 한다.** 템플릿에 `{engagementRate}%` 로 붙어 있으면
     * 미측정일 때 `측정 불가(수집하는 플랫폼 없음)%` 라는 문장이 만들어진다.
     */
    @Test
    @DisplayName("미측정 문구 뒤에 퍼센트 기호가 붙지 않는다")
    fun notCollectedTextCarriesNoUnit() {
        val prompt = renderedPrompt(Platform.TUMBLR, listOf(row(views = 900_000)))

        assertFalse("${MetaRewriteUseCase.NOT_COLLECTED}%" in prompt, "미측정 문구에 % 가 붙었다:\n$prompt")
    }

    @Test
    @DisplayName("미측정 문구에 숫자가 들어가지 않는다")
    fun notCollectedTextIsASentence() {
        val text = MetaRewriteUseCase.NOT_COLLECTED

        assertTrue(text.isNotBlank())
        assertFalse(Regex("[0-9]").containsMatchIn(text), "미측정 문구에 숫자가 있다: $text")
    }
}
