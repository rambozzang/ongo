package com.ongo.application.comment

import com.ongo.application.ai.AnalyzeSentimentUseCase
import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.FetchedComment
import com.ongo.domain.comment.FetchedCommentList
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VideoCommentSyncServiceTest {
    private val commentRepository = mockk<CommentRepository>(relaxed = true)
    private val platformCommentPort = mockk<PlatformCommentPort>()
    private val channelRepository = mockk<ChannelRepository>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true)
    private val sentiment = mockk<AnalyzeSentimentUseCase>()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val websocket = mockk<WebSocketNotificationService>(relaxed = true)
    private val settings = mockk<UserSettingsRepository>(relaxed = true)
    private val service = VideoCommentSyncService(
        commentRepository,
        platformCommentPort,
        channelRepository,
        tokenEncryptionPort,
        sentiment,
        notifications,
        websocket,
        settings,
    )

    @Test
    fun `sentiment provider failure is stored as unanalyzed not neutral`() {
        every { commentRepository.findLatestSyncedAtByVideoIdAndPlatform(9L, "YOUTUBE") } returns null
        every { platformCommentPort.fetchComments(any(), any(), any(), any(), any(), any()) } returns
            FetchedCommentList(
                comments = listOf(
                    FetchedComment(
                        platformCommentId = "comment-1",
                        authorName = "작성자",
                        content = "댓글 내용",
                    ),
                ),
            )
        every { commentRepository.findByPlatformAndPlatformCommentIdsIn("YOUTUBE", listOf("comment-1")) } returns emptyList()
        every { sentiment.analyzeBatch(7L, listOf("댓글 내용")) } throws IllegalStateException("provider down")

        val saved = slot<List<Comment>>()
        every { commentRepository.upsertBatch(capture(saved)) } returns 1

        service.syncVideoComments(
            userId = 7L,
            videoId = 9L,
            platform = Platform.YOUTUBE,
            platformVideoId = "video-1",
            accessToken = PlainToken("token"),
        )

        assertEquals(Comment.SENTIMENT_UNANALYZED, saved.captured.single().sentiment)
    }

    /**
     * 실시간 댓글 알림이 **읽을 수 있는 문구와 이동 경로를 싣는지** 고정한다.
     *
     * 프런트는 `payload.title` 이 없으면 `message.type` 으로 폴백하고 본문을 비운다
     * (`frontend/src/composables/useWebSocket.ts` 의 `handleMessage`). 숫자만 보내면
     * 토스트가 원문 enum 인 **"COMMENT"** 로 뜬다.
     *
     * `referenceType`/`referenceId` 도 같은 이유다. 프런트는 그 두 이름으로 대상을
     * 찾는데 `videoId` 만 보내면 알림에서 영상으로 이동할 수 없다 — 이름이 다르다.
     */
    @Test
    fun `실시간 댓글 알림은 저장된 문구와 영상 참조를 함께 싣는다`() {
        every { commentRepository.findLatestSyncedAtByVideoIdAndPlatform(9L, "YOUTUBE") } returns null
        every { platformCommentPort.fetchComments(any(), any(), any(), any(), any(), any()) } returns
            FetchedCommentList(
                comments = listOf(
                    FetchedComment(
                        platformCommentId = "comment-1",
                        authorName = "작성자",
                        content = "댓글 내용",
                    ),
                ),
            )
        every { commentRepository.findByPlatformAndPlatformCommentIdsIn("YOUTUBE", listOf("comment-1")) } returns emptyList()
        every { sentiment.analyzeBatch(any(), any()) } throws IllegalStateException("provider down")
        every { commentRepository.upsertBatch(any()) } returns 1
        // 실시간 알림은 이 설정일 때만 나간다.
        every { settings.findByUserId(7L) } returns
            mockk(relaxed = true) { every { notificationComment } returns "realtime" }

        val notification = slot<com.ongo.domain.notification.Notification>()
        every { notifications.save(capture(notification)) } answers { firstArg() }

        service.syncVideoComments(
            userId = 7L,
            videoId = 9L,
            platform = Platform.YOUTUBE,
            platformVideoId = "video-1",
            accessToken = PlainToken("token"),
        )

        val payload = slot<Any>()
        io.mockk.verify { websocket.sendToUser(7L, "COMMENT", capture(payload)) }
        val sent = payload.captured as Map<*, *>

        // 저장한 문구와 보낸 문구가 같은 출처여야 한다 — 문구를 여기 복사해 두지 않는다.
        assertEquals(notification.captured.title, sent["title"], "토스트에 enum 이름이 노출된다")
        assertEquals(notification.captured.message, sent["message"], "토스트 본문이 비어 있다")
        // 이동 경로. `videoId` 만으로는 프런트가 못 찾는다.
        assertEquals("video", sent["referenceType"])
        assertEquals(9L, sent["referenceId"])
    }
}
