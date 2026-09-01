package com.ongo.application.comment

import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlatformApiException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentCapabilities
import com.ongo.domain.comment.CommentRepository
import com.ongo.domain.comment.PlatformCommentPort
import com.ongo.domain.comment.PostReplyResult
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    /**
     * 답글은 **외부 전송이 성공해야만** 로컬을 답변 완료로 바꾼다.
     *
     * 예전에는 세 갈래가 조용히 성공으로 끝났다 — 플랫폼 식별자 없음, 답글 미지원,
     * 연결 채널 없음. 셋 다 외부에 아무것도 보내지 않은 채 `isReplied = true` 를 저장하고
     * 200 을 돌려줬다. 화면의 capability 게이트를 지나지 않는 API 직접 호출에서는
     * "답글을 달았다" 는 기록만 남고 시청자는 아무 답글도 받지 못한다.
     */
    @Nested
    inner class ReplyToComment {
        private val userId = 7L

        private fun stubUser() {
            every { users.findById(userId) } returns User(
                id = userId,
                email = "creator@example.com",
                name = "Creator",
                provider = AuthProvider.GOOGLE,
                providerId = "creator-1",
                planType = PlanType.PRO,
            )
        }

        private fun stubComment(
            platformName: String? = "YOUTUBE",
            platformCommentId: String? = "c-1",
        ): Comment {
            val comment = Comment(
                id = 11L,
                userId = userId,
                platform = platformName,
                platformCommentId = platformCommentId,
                authorName = "viewer",
                content = "댓글",
            )
            every { comments.findById(11L) } returns comment
            return comment
        }

        private fun stubChannel() {
            every { channels.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns Channel(
                id = 1L,
                userId = userId,
                platform = Platform.YOUTUBE,
                platformChannelId = "UC-1",
                channelName = "채널",
                accessToken = EncryptedToken("enc"),
            )
            every { tokens.decrypt(EncryptedToken("enc")) } returns PlainToken("token")
        }

        private fun stubCapability(canReply: Boolean) {
            every { platform.getCommentCapabilities(Platform.YOUTUBE) } returns
                CommentCapabilities(canListComments = true, canReply = canReply, canHide = true)
        }

        @Test
        fun `답글 미지원 플랫폼은 외부 호출도 로컬 저장도 하지 않는다`() {
            stubUser()
            stubComment()
            stubCapability(canReply = false)

            assertFailsWith<BusinessException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { comments.update(any()) }
        }

        @Test
        fun `연결된 채널이 없으면 저장하지 않는다`() {
            stubUser()
            stubComment()
            stubCapability(canReply = true)
            every { channels.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns null

            assertFailsWith<NotFoundException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { comments.update(any()) }
        }

        @Test
        fun `플랫폼 식별자가 없으면 저장하지 않는다`() {
            stubUser()
            stubComment(platformName = null)

            assertFailsWith<BusinessException> { useCase.replyToComment(userId, 11L, "답글") }

            stubComment(platformCommentId = null)
            assertFailsWith<BusinessException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { comments.update(any()) }
        }

        /** 공백 문자열도 식별자가 아니다 — null 만 막으면 그대로 전송된다. */
        @Test
        fun `플랫폼 식별자가 공백이면 저장하지 않는다`() {
            stubUser()
            stubCapability(canReply = true)
            stubChannel()

            for (blank in listOf("", " ", "\t", "\n")) {
                stubComment(platformCommentId = blank)

                val error = assertFailsWith<BusinessException> {
                    useCase.replyToComment(userId, 11L, "답글")
                }
                assertEquals("COMMENT_PLATFORM_ID_MISSING", error.code)
            }

            verify(exactly = 0) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { comments.update(any()) }
        }

        /** 플랫폼 문자열이 공백이면 지원 플랫폼으로 해석되지 않는다. */
        @Test
        fun `플랫폼 문자열이 공백이면 저장하지 않는다`() {
            stubUser()
            stubComment(platformName = "  ")

            assertFailsWith<BusinessException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { comments.update(any()) }
        }

        @Test
        fun `알 수 없는 플랫폼 문자열은 명확한 오류로 끝난다`() {
            stubUser()
            stubComment(platformName = "MYSPACE")

            assertFailsWith<BusinessException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { comments.update(any()) }
        }

        @Test
        fun `플랫폼 전송이 실패하면 저장하지 않는다`() {
            stubUser()
            stubComment()
            stubCapability(canReply = true)
            stubChannel()
            every { platform.postReply(any(), any(), any(), any(), any()) } returns
                PostReplyResult(platformCommentId = "", success = false, errorMessage = "쿼터 초과")

            assertFailsWith<PlatformApiException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { comments.update(any()) }
        }

        /** 성공이라면서 식별자가 비어 있으면 신뢰하지 않는다 — 나중에 찾을 수 없다. */
        @Test
        fun `성공인데 답글 식별자가 비면 저장하지 않는다`() {
            stubUser()
            stubComment()
            stubCapability(canReply = true)
            stubChannel()
            every { platform.postReply(any(), any(), any(), any(), any()) } returns
                PostReplyResult(platformCommentId = "  ", success = true)

            assertFailsWith<PlatformApiException> { useCase.replyToComment(userId, 11L, "답글") }

            verify(exactly = 0) { comments.update(any()) }
        }

        @Test
        fun `실제 전송에 성공하면 한 번만 저장한다`() {
            stubUser()
            val comment = stubComment()
            stubCapability(canReply = true)
            stubChannel()
            every { platform.postReply(any(), any(), any(), any(), any()) } returns
                PostReplyResult(platformCommentId = "reply-9", success = true)
            val saved = slot<Comment>()
            every { comments.update(capture(saved)) } answers { firstArg() }

            useCase.replyToComment(userId, 11L, "답글")

            verify(exactly = 1) { platform.postReply(any(), any(), any(), any(), any()) }
            verify(exactly = 1) { comments.update(any()) }
            assertEquals(true, saved.captured.isReplied)
            assertEquals("답글", saved.captured.replyContent)
            assertEquals("reply-9", saved.captured.platformReplyId)
            assertEquals(comment.id, saved.captured.id)
        }
    }
}
