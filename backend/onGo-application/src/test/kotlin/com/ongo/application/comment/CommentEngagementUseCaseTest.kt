package com.ongo.application.comment

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentCapabilities
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class CommentEngagementUseCaseTest {
    private val comments = mockk<CommentRepository>(relaxed = true)
    private val platform = mockk<PlatformCommentPort>(relaxed = true)
    private val channels = mockk<ChannelRepository>(relaxed = true)
    private val tokens = mockk<TokenEncryptionPort>(relaxed = true)
    private val users = mockk<UserRepository>(relaxed = true)
    private val useCase = CommentEngagementUseCase(comments, platform, channels, tokens, users)

    @Test
    fun `hide-only providers do not receive an unsupported delete request`() {
        val userId = 7L
        val comment = Comment(
            id = 11L,
            userId = userId,
            platform = "THREADS",
            platformCommentId = "reply-1",
            authorName = "creator",
            content = "댓글",
        )
        every { users.findById(userId) } returns User(
            id = userId,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "creator-1",
            planType = PlanType.PRO,
        )
        every { comments.findById(11L) } returns comment
        every { platform.getCommentCapabilities(Platform.THREADS) } returns
            CommentCapabilities(canListComments = true, canReply = true, canHide = true)

        useCase.deleteComment(userId, 11L)

        verify(exactly = 0) { platform.deleteComment(any(), any(), any()) }
        verify { comments.delete(11L) }
    }
}
