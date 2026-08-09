package com.ongo.application.channel

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class ChannelUseCaseTest {

    private val channels = mockk<ChannelRepository>()
    private val users = mockk<UserRepository>()
    private val oauth = mockk<PlatformOAuth2Port>()
    private val clients = mockk<PlatformClientPort>()
    private val encryption = mockk<TokenEncryptionPort>()
    private val videoUploads = mockk<VideoUploadRepository>()
    private val workspaces = mockk<WorkspaceRepository>()
    private val useCase = ChannelUseCase(channels, users, oauth, clients, encryption, videoUploads, workspaces)

    @Test
    fun `활성 lease가 있는 예약 게시가 남아 있으면 채널 해제를 거부한다`() {
        val channel = Channel(
            id = 9L,
            userId = 7L,
            platform = Platform.YOUTUBE,
            platformChannelId = "channel-9",
            channelName = "channel",
            accessToken = mockk(),
        )
        every { channels.findById(9L) } returns channel
        every { videoUploads.findByUserId(7L) } returns listOf(
            VideoUpload(
                id = 11L,
                videoId = 22L,
                channelId = 9L,
                platform = Platform.YOUTUBE,
                status = UploadStatus.UPLOADING,
                scheduledAt = LocalDateTime.now().plusMinutes(5),
                leaseUntil = LocalDateTime.now().plusMinutes(1),
            ),
        )
        every { videoUploads.cancelScheduledUploadsByChannelId(9L, any()) } returns 0

        assertFailsWith<BusinessException> {
            useCase.disconnectChannel(7L, 9L)
        }

        verify(exactly = 0) { encryption.decrypt(any()) }
        verify(exactly = 0) { clients.revokeToken(any(), any()) }
        verify(exactly = 0) { channels.delete(any()) }
    }
}
