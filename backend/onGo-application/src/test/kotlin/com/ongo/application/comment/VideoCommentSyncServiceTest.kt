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
}
