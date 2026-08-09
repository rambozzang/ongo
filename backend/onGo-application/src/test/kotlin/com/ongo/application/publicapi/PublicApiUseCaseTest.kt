package com.ongo.application.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformUploadStatus
import com.ongo.application.video.PublishResult
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.publicapi.PublicApiPost
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.schedule.Schedule
import com.ongo.common.enums.ScheduleStatus
import java.time.LocalDateTime
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PublicApiUseCaseTest {

    private val channels = mockk<ChannelRepository>()
    private val posts = mockk<PublicApiPostRepository>()
    private val videos = mockk<VideoRepository>()
    private val uploads = mockk<VideoUploadRepository>()
    private val schedules = mockk<ScheduleRepository>()
    private val uploadVideo = mockk<UploadVideoUseCase>()
    private val publishVideo = mockk<PublishVideoUseCase>()
    private val tokenEncryption = mockk<TokenEncryptionPort>()
    private val integrationTools = mockk<PlatformIntegrationToolPort>()
    private val useCase = PublicApiUseCase(
        channels,
        posts,
        videos,
        uploads,
        schedules,
        uploadVideo,
        publishVideo,
        jacksonObjectMapper(),
        tokenEncryption,
        integrationTools,
    )

    private val channel = Channel(
        id = 7,
        userId = 1,
        platform = Platform.YOUTUBE,
        platformChannelId = "yt-7",
        channelName = "내 채널",
        accessToken = EncryptedToken("encrypted"),
        status = ChannelStatus.ACTIVE,
    )

    @Test
    fun `integrations는 소유한 연결 계정만 Postiz 형태로 반환한다`() {
        every { channels.findByUserId(1) } returns listOf(channel)

        val result = useCase.integrations(1)

        assertEquals("7", result.single().id)
        assertEquals("youtube", result.single().provider)
        assertEquals("yt-7", result.single().identifier)
    }

    @Test
    fun `integration settings는 소유한 채널의 실제 게시 capability를 반환한다`() {
        every { channels.findById(7) } returns channel
        every { integrationTools.definitions(Platform.YOUTUBE) } returns emptyList()

        val result = useCase.integrationSettings(1, "7")

        assertEquals("youtube", result.provider)
        assertEquals(100, result.title.maxLength)
        assertEquals(5_000, result.description.maxLength)
        assertEquals(500, result.tags.maxCount)
        assertEquals(true, result.scheduling)
        assertEquals(100, result.output.maxLength)
        assertEquals(emptyList(), result.output.tools)
    }

    @Test
    fun `integration trigger는 discovery에 등록된 tool만 복호화 토큰으로 실행한다`() {
        every { channels.findById(7) } returns channel
        every { integrationTools.definitions(Platform.YOUTUBE) } returns listOf(
            PlatformToolDefinition("getChannelInfo", "profile"),
        )
        every { tokenEncryption.decrypt(EncryptedToken("encrypted")) } returns PlainToken("plain-token")
        every {
            integrationTools.invoke(
                Platform.YOUTUBE,
                PlainToken("plain-token"),
                "yt-7",
                "getChannelInfo",
                emptyMap(),
            )
        } returns mapOf("channelName" to "내 채널")

        val result = useCase.triggerIntegrationTool(
            1,
            "7",
            PublicIntegrationToolRequest("getChannelInfo"),
        )

        assertEquals("내 채널", result.output.path("channelName").asText())
        verify(exactly = 1) { tokenEncryption.decrypt(EncryptedToken("encrypted")) }
    }

    @Test
    fun `integration trigger는 다른 사용자의 integration을 노출하지 않는다`() {
        every { channels.findById(7) } returns channel.copy(userId = 99)

        assertFailsWith<com.ongo.common.exception.NotFoundException> {
            useCase.triggerIntegrationTool(1, "7", PublicIntegrationToolRequest("getChannelInfo"))
        }
    }

    @Test
    fun `posts 날짜 범위가 지정되면 repository 범위 조회를 사용한다`() {
        val post = PublicApiPost(
            id = 41,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = LocalDateTime.parse("2026-08-05T10:00:00"),
            payloadJson = "{\"posts\":[]}",
        )
        every {
            posts.findByUserIdAndDateRange(
                1,
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-31T23:59:59"),
                20,
            )
        } returns listOf(post)
        every { uploads.findByVideoId(11) } returns emptyList()

        val result = useCase.list(1, 20, "2026-08-01T00:00:00", "2026-08-31T23:59:59")

        assertEquals(listOf("41"), result.map { it.id })
        verify(exactly = 1) {
            posts.findByUserIdAndDateRange(
                1,
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-31T23:59:59"),
                20,
            )
        }
    }

    @Test
    fun `missing content는 다른 사용자의 게시물을 조회하지 않고 대상별 누락을 계산한다`() {
        every { posts.findByIdAndUserId(42, 1) } returns PublicApiPost(
            id = 42,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.DRAFT,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.DRAFT,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"},\"value\":[]}]}",
        )

        val result = useCase.missingContent(1, 42)

        assertEquals(listOf("content"), result.single().missing)
        assertEquals("7", result.single().integration)
    }

    @Test
    fun `release id 연결은 새 업로드 없이 durable 상태 조회를 깨운다`() {
        val post = PublicApiPost(
            id = 43,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.NOW,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.UNCONFIRMED,
            payloadJson = "{\"posts\":[]}",
        )
        val upload = VideoUpload(
            id = 91,
            videoId = 11,
            platform = Platform.YOUTUBE,
            channelId = 7,
            status = UploadStatus.UNCONFIRMED,
        )
        every { posts.findByIdAndUserId(43, 1) } returns post
        every { channels.findById(7) } returns channel
        every { uploads.findByVideoId(11) } returnsMany listOf(
            listOf(upload),
            listOf(upload.copy(platformVideoId = "yt-video-1", pollToken = "yt-video-1", status = UploadStatus.PROCESSING)),
        )
        every { uploads.update(any()) } answers { firstArg() }

        val result = useCase.connectReleaseId(1, 43, PublicReleaseIdRequest("yt-video-1", "7"))

        assertEquals("processing", result.status)
        verify(exactly = 1) {
            uploads.update(match {
                it.platformVideoId == "yt-video-1" &&
                    it.pollToken == "yt-video-1" &&
                    it.status == UploadStatus.PROCESSING
            })
        }
    }

    @Test
    fun `now 게시가 내부 durable publisher를 호출하고 post id를 반환한다`() {
        every { videos.findById(11) } returns Video(id = 11, userId = 1, title = "원본", fileUrl = "https://cdn/video.mp4")
        every { channels.findById(7) } returns channel
        every { posts.save(any()) } answers { firstArg<PublicApiPost>().copy(id = 21) }
        every { posts.update(any()) } answers { firstArg() }
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 91, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING),
        )
        every { publishVideo.publishVideo(any(), any(), any()) } returns
            PublishResult(11, listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)))

        val response = useCase.create(
            1,
            CreatePublicPostRequest(
                type = "now",
                videoId = 11,
                posts = listOf(
                    PublicPostItem(
                        integration = PublicIntegrationRef("7"),
                        value = listOf(PublicPostValue(title = "제목", content = "설명", tags = listOf("#one"))),
                    ),
                ),
            ),
        )

        assertEquals("21", response.id)
        assertEquals("processing", response.status)
        assertEquals(null, response.error)
        verify(exactly = 1) { publishVideo.publishVideo(1, 11, any()) }
    }

    @Test
    fun `다른 사용자의 videoId는 공개 API에서도 거부한다`() {
        every { videos.findById(11) } returns Video(id = 11, userId = 999, title = "남의 영상", fileUrl = "https://cdn/video.mp4")

        assertFailsWith<com.ongo.common.exception.ForbiddenException> {
            useCase.create(
                1,
                CreatePublicPostRequest(
                    videoId = 11,
                    posts = listOf(PublicPostItem(PublicIntegrationRef("7"))),
                ),
            )
        }
        verify(exactly = 0) { posts.save(any()) }
    }

    @Test
    fun `schedule 상태를 draft로 바꾸면 durable upload와 schedule 행을 함께 취소한다`() {
        val scheduled = PublicApiPost(
            id = 30,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = LocalDateTime.now().plusHours(2),
            payloadJson = "{}",
        )
        every { posts.findByIdAndUserId(30, 1) } returns scheduled
        every { posts.update(any()) } answers { firstArg() }
        every { uploads.cancelScheduledUploads(11, any()) } returns 1
        every { uploads.findByVideoId(11) } returns emptyList()
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(id = 40, videoId = 11, userId = 1, scheduledAt = scheduled.scheduledAt!!),
        )
        every { schedules.update(any()) } answers { firstArg() }

        val response = useCase.changeStatus(1, 30, ChangePublicPostStatusRequest("draft"))

        assertEquals("draft", response.status)
        verify(exactly = 1) { uploads.cancelScheduledUploads(11, any()) }
        verify(exactly = 1) { schedules.update(match { it.status == ScheduleStatus.CANCELLED }) }
    }

    @Test
    fun `예약 게시물 삭제는 durable upload와 schedule을 취소하고 기록을 보존한다`() {
        val scheduled = PublicApiPost(
            id = 31,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = LocalDateTime.now().plusHours(2),
            payloadJson = "{}",
        )
        every { posts.findByIdAndUserId(31, 1) } returns scheduled
        every { posts.update(any()) } answers { firstArg() }
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 92, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING, scheduledAt = scheduled.scheduledAt),
        )
        every { uploads.cancelScheduledUploads(11, any()) } returns 1
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(id = 41, videoId = 11, userId = 1, scheduledAt = scheduled.scheduledAt!!),
        )
        every { schedules.update(any()) } answers { firstArg() }

        useCase.deleteGroup(1, "31")

        verify(exactly = 1) { uploads.cancelScheduledUploads(11, any()) }
        verify(exactly = 1) { schedules.update(match { it.status == ScheduleStatus.CANCELLED }) }
        verify(exactly = 1) { posts.update(match { it.status == com.ongo.domain.publicapi.PublicApiPostStatus.CANCELLED }) }
    }

    @Test
    fun `외부 게시가 접수된 게시물 삭제는 중복 방지를 위해 거부한다`() {
        val published = PublicApiPost(
            id = 32,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.NOW,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.PROCESSING,
            payloadJson = "{}",
        )
        every { posts.findByIdAndUserId(32, 1) } returns published
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 93, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.PROCESSING),
        )

        assertFailsWith<com.ongo.common.exception.BusinessException> { useCase.delete(1, 32) }
        verify(exactly = 0) { posts.update(any()) }
    }
}
