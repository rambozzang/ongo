package com.ongo.application.admin

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdminUseCaseTest {
    private val uploads = mockk<VideoUploadRepository>()
    private val useCase = AdminUseCase(
        userRepository = mockk<UserRepository>(relaxed = true),
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
        videoRepository = mockk<VideoRepository>(relaxed = true),
        videoUploadRepository = uploads,
        channelRepository = mockk<ChannelRepository>(relaxed = true),
        storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true),
    )

    @Test
    fun `publish queue summary exposes leases retries and unconfirmed work`() {
        val now = LocalDateTime.now()
        every { uploads.findPendingUploads() } returns listOf(
            VideoUpload(
                id = 1L,
                videoId = 10L,
                platform = Platform.YOUTUBE,
                status = UploadStatus.PROCESSING,
                attemptCount = 2,
                leaseUntil = now.plusMinutes(5),
                createdAt = now.minusMinutes(4),
            ),
            VideoUpload(
                id = 3L,
                videoId = 10L,
                platform = Platform.INSTAGRAM,
                status = UploadStatus.UPLOADING,
                attemptCount = 1,
                createdAt = now.minusMinutes(3),
            ),
            VideoUpload(
                id = 2L,
                videoId = 10L,
                platform = Platform.TIKTOK,
                status = UploadStatus.UNCONFIRMED,
                attemptCount = 3,
                nextRetryAt = now.minusMinutes(1),
                lastError = "외부 응답이 끊겼습니다.",
                createdAt = now.minusMinutes(2),
            ),
        )

        val summary = useCase.getPublishQueue()

        assertEquals(3, summary.totalPending)
        assertEquals(1, summary.statusCounts[UploadStatus.PROCESSING.name])
        assertEquals(1, summary.activeLeases)
        assertEquals(1, summary.dueRetries)
        assertEquals(1, summary.unconfirmed)
        assertEquals(3, summary.items.size)
        assertTrue(summary.items.any { it.lastError == "외부 응답이 끊겼습니다." })
    }
}
