package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.FeedItemResult
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformFeedPortResult
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 피드 **페이지 이동 계약**을 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * pageToken = if (page > 0) page.toString() else null
 * ```
 *
 * 플랫폼 목록 API 의 페이지 토큰은 **불투명한 continuation token** 이다
 * (YouTube `nextPageToken`, Instagram `cursors.after`, Twitter `meta.next_token`,
 * TikTok `cursor`). 숫자 `"1"` 을 그 자리에 넣으면
 *
 * - YouTube 는 유효하지 않은 토큰으로 보고 오류를 냈고 **그 플랫폼 영상이 통째로 사라졌다**,
 * - TikTok 은 `1` 을 커서(타임스탬프)로 읽어 엉뚱한 구간을 돌려줬다.
 *
 * 게다가 플랫폼이 준 `nextPageToken` 은 버려져 **다음 페이지로 갈 방법 자체가 없었다.**
 *
 * 이제 응답이 채널별 다음 토큰을 실어 보내고, 호출자가 그것을 그대로 돌려준다.
 */
class VideoFeedPaginationTest {

    private val channelRepository = mockk<ChannelRepository>()
    private val platformClientPort = mockk<PlatformClientPort>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true)

    private val useCase = VideoFeedUseCase(
        channelRepository = channelRepository,
        platformClientPort = platformClientPort,
        videoUploadRepository = videoUploadRepository,
        tokenEncryptionPort = tokenEncryptionPort,
    )

    private val userId = 7L

    private fun channel(id: Long, platform: Platform) = Channel(
        id = id,
        userId = userId,
        platform = platform,
        platformChannelId = "ch-$id",
        channelName = "채널 $id",
        accessToken = EncryptedToken("token-$id"),
    )

    private fun item(id: String) = FeedItemResult(
        platformVideoId = id,
        title = "영상 $id",
        publishedAt = "2026-08-01T00:00:00Z",
    )

    private fun givenChannels(vararg channels: Channel) {
        every { channelRepository.findByUserId(userId) } returns channels.toList()
    }

    // ── 첫 페이지 ────────────────────────────────────────────────────────────

    /** **첫 페이지에는 커서를 보내지 않는다.** */
    @Test
    @DisplayName("page 0 은 플랫폼에 토큰을 보내지 않는다")
    fun firstPageSendsNoToken() {
        givenChannels(channel(1L, Platform.YOUTUBE))
        val sentToken = slot<String?>()
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), captureNullable(sentToken))
        } returns PlatformFeedPortResult(items = listOf(item("a")))

        useCase.getFeed(userId, null, 0, 20, "recent")

        assertNull(sentToken.captured, "첫 페이지인데 토큰을 보냈다")
    }

    /** **이 케이스가 숫자를 불투명 토큰 자리에 넣던 자리다.** */
    @Test
    @DisplayName("숫자 페이지 이동은 지원하지 않는다고 분명히 알린다")
    fun numericPageIsRejected() {
        givenChannels(channel(1L, Platform.YOUTUBE))

        val error = assertThrows<BusinessException> {
            useCase.getFeed(userId, null, 1, 20, "recent")
        }

        assertEquals("FEED_PAGE_UNSUPPORTED", error.code)
        // 대안을 알려야 호출자가 고칠 수 있다.
        assertTrue("nextPageTokens" in error.message, error.message)
    }

    // ── 다음 페이지 토큰 보존 ────────────────────────────────────────────────

    /** **플랫폼이 준 토큰을 버리지 않는다.** */
    @Test
    @DisplayName("플랫폼이 준 다음 토큰을 채널별로 응답에 싣는다")
    fun nextTokenIsReturnedPerChannel() {
        givenChannels(channel(1L, Platform.YOUTUBE))
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } returns PlatformFeedPortResult(items = listOf(item("a")), nextPageToken = "OPAQUE_TOKEN")

        val response = useCase.getFeed(userId, null, 0, 20, "recent")

        assertEquals(mapOf(1L to "OPAQUE_TOKEN"), response.nextPageTokens)
    }

    /** 마지막 페이지는 토큰이 없다 — 빈 맵이 "더 없음" 이다. */
    @Test
    @DisplayName("다음 토큰이 없으면 응답의 토큰 맵도 비어 있다")
    fun noNextTokenYieldsEmptyMap() {
        givenChannels(channel(1L, Platform.YOUTUBE))
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } returns PlatformFeedPortResult(items = listOf(item("a")), nextPageToken = null)

        assertTrue(useCase.getFeed(userId, null, 0, 20, "recent").nextPageTokens.isEmpty())
    }

    /** **받은 커서는 그 채널에만 그대로 전달된다.** */
    @Test
    @DisplayName("전달한 커서를 해당 채널의 플랫폼 호출에 그대로 넘긴다")
    fun providedCursorIsForwarded() {
        givenChannels(channel(1L, Platform.YOUTUBE))
        val sentToken = slot<String?>()
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), captureNullable(sentToken))
        } returns PlatformFeedPortResult(items = listOf(item("b")))

        useCase.getFeed(userId, null, 0, 20, "recent", mapOf(1L to "OPAQUE_TOKEN"))

        assertEquals("OPAQUE_TOKEN", sentToken.captured)
    }

    // ── 다중 채널·다중 플랫폼 ────────────────────────────────────────────────

    /**
     * **채널마다 커서가 독립적이다.**
     *
     * 한 채널의 토큰을 다른 채널에 보내면 플랫폼이 거절하거나 남의 구간을 돌려준다.
     */
    @Test
    @DisplayName("채널별 커서를 서로 섞지 않는다")
    fun cursorsAreNotMixedBetweenChannels() {
        givenChannels(channel(1L, Platform.YOUTUBE), channel(2L, Platform.TIKTOK))
        val byChannel = mutableMapOf<String, String?>()
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } answers {
            val channelId = arg<String?>(2)
            byChannel[channelId ?: ""] = arg<String?>(4)
            PlatformFeedPortResult(items = emptyList())
        }

        useCase.getFeed(userId, null, 0, 20, "recent", mapOf(1L to "YT_TOKEN", 2L to "TT_TOKEN"))

        assertEquals("YT_TOKEN", byChannel["ch-1"])
        assertEquals("TT_TOKEN", byChannel["ch-2"])
    }

    /** 커서를 준 채널만 이어보고, 주지 않은 채널은 첫 페이지부터 본다. */
    @Test
    @DisplayName("커서가 없는 채널은 첫 페이지부터 조회한다")
    fun channelWithoutCursorStartsFromFirstPage() {
        givenChannels(channel(1L, Platform.YOUTUBE), channel(2L, Platform.TIKTOK))
        val byChannel = mutableMapOf<String, String?>()
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } answers {
            byChannel[arg<String?>(2) ?: ""] = arg<String?>(4)
            PlatformFeedPortResult(items = emptyList())
        }

        useCase.getFeed(userId, null, 0, 20, "recent", mapOf(1L to "YT_TOKEN"))

        assertEquals("YT_TOKEN", byChannel["ch-1"])
        assertNull(byChannel["ch-2"], "커서를 주지 않은 채널에 토큰을 보냈다")
    }

    /** 여러 채널의 토큰이 각각 응답에 담긴다. */
    @Test
    @DisplayName("여러 채널의 다음 토큰을 모두 싣는다")
    fun multipleChannelTokensAreReturned() {
        givenChannels(channel(1L, Platform.YOUTUBE), channel(2L, Platform.TIKTOK))
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } answers {
            val channelId = arg<String?>(2)
            PlatformFeedPortResult(
                items = emptyList(),
                nextPageToken = if (channelId == "ch-1") "YT_NEXT" else "TT_NEXT",
            )
        }

        val response = useCase.getFeed(userId, null, 0, 20, "recent")

        assertEquals(mapOf(1L to "YT_NEXT", 2L to "TT_NEXT"), response.nextPageTokens)
    }

    // ── 플랫폼 오류 ──────────────────────────────────────────────────────────

    /**
     * **한 채널이 실패해도 나머지 채널의 이어보기는 살아 있어야 한다.**
     *
     * 실패한 채널은 토큰을 낼 수 없으므로 맵에서 빠진다 — 다음 요청에서 그 채널은
     * 첫 페이지부터 다시 시도된다.
     */
    @Test
    @DisplayName("한 플랫폼이 실패해도 다른 채널의 토큰은 보존한다")
    fun oneFailingPlatformKeepsOtherTokens() {
        givenChannels(channel(1L, Platform.YOUTUBE), channel(2L, Platform.TIKTOK))
        every {
            platformClientPort.listVideos(any(), any(), any(), any(), any())
        } answers {
            if (arg<String?>(2) == "ch-1") throw RuntimeException("YouTube 장애")
            PlatformFeedPortResult(items = listOf(item("t")), nextPageToken = "TT_NEXT")
        }

        val response = useCase.getFeed(userId, null, 0, 20, "recent")

        assertEquals(mapOf(2L to "TT_NEXT"), response.nextPageTokens)
        assertTrue(response.errors?.contains("YOUTUBE") == true, "실패를 알리지 않았다")
        assertEquals(1, response.items.size, "살아 있는 채널의 항목까지 잃었다")
    }
}
