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
import java.time.ZoneId
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.domain.workspace.Workspace
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
    private val workspaces = mockk<WorkspaceRepository>(relaxed = true)
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
        workspaces,
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
        assertEquals("youtube", result.single().identifier)
    }

    @Test
    fun `find slot은 해당 계정의 플랫폼별 예약 시각을 건너뛰고 UTC ISO 날짜를 반환한다`() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = LocalDateTime.now(zone).plusMinutes(5).withSecond(0).withNano(0)
        val firstQuarter = now.plusMinutes(((15 - now.minute % 15) % 15).toLong())
        every { channels.findById(7) } returns channel
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(
                id = 70,
                videoId = 11,
                userId = 1,
                scheduledAt = firstQuarter,
                platforms = mapOf(
                    "YOUTUBE#7" to mapOf("scheduledAt" to firstQuarter.toString()),
                    "INSTAGRAM#99" to mapOf("scheduledAt" to firstQuarter.minusMinutes(15).toString()),
                ),
            ),
        )

        val result = useCase.findAvailableSlot(1, "7")
        val returned = java.time.Instant.parse(result.date).atZone(zone).toLocalDateTime()

        assertEquals(0, returned.minute % 15)
        assertEquals(0, returned.second)
        assertEquals(0, returned.nano)
        assertEquals(true, !returned.isBefore(firstQuarter.plusMinutes(15)))
    }

    @Test
    fun `integrations group은 접근 가능한 workspace에 속한 채널만 반환한다`() {
        val workspace = Workspace(id = 22, ownerId = 1, name = "브랜드", slug = "brand")
        every { workspaces.findAccessibleByUserId(1) } returns listOf(workspace)
        every { channels.findByUserIdAndWorkspaceId(1, 22) } returns listOf(channel.copy(workspaceId = 22))
        every { workspaces.findById(22) } returns workspace

        val result = useCase.integrations(1, "brand")

        assertEquals("22", result.single().customer?.id)
        assertEquals("브랜드", result.single().customer?.name)
        verify { channels.findByUserIdAndWorkspaceId(1, 22) }
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
            scheduledAt = java.time.Instant.parse("2026-08-05T10:00:00Z")
                .atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime(),
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
        assertEquals(
            post.scheduledAt!!.atZone(ZoneId.of("Asia/Seoul")).toInstant().toString(),
            result.single().date,
        )
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
    fun `posts customer는 접근 가능한 workspace 게시물만 조회한다`() {
        val workspace = Workspace(id = 22, ownerId = 1, name = "브랜드", slug = "brand")
        val post = PublicApiPost(
            id = 45,
            userId = 1,
            workspaceId = 22,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.DRAFT,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.DRAFT,
            payloadJson = "{\"posts\":[]}",
        )
        every { workspaces.findAccessibleByUserId(1) } returns listOf(workspace)
        every { posts.findByUserIdAndWorkspaceId(1, 22, 20) } returns listOf(post)
        every { uploads.findByVideoId(11) } returns emptyList()

        val result = useCase.list(1, 20, customer = "brand")

        assertEquals(listOf("45"), result.map { it.id })
        verify { posts.findByUserIdAndWorkspaceId(1, 22, 20) }
    }

    @Test
    fun `Postiz UTC 날짜는 서버 시간으로 조회하고 UTC로 다시 반환한다`() {
        val post = PublicApiPost(
            id = 44,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = java.time.Instant.parse("2026-08-05T10:00:00Z")
                .atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime(),
            payloadJson = "{\"posts\":[]}",
        )
        val start = java.time.Instant.parse("2026-08-01T00:00:00Z")
            .atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        val end = java.time.Instant.parse("2026-08-31T23:59:59Z")
            .atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime()
        every { posts.findByUserIdAndDateRange(1, start, end, 20) } returns listOf(post)
        every { uploads.findByVideoId(11) } returns emptyList()

        val result = useCase.list(1, 20, "2026-08-01T00:00:00Z", "2026-08-31T23:59:59Z")

        assertEquals(listOf("44"), result.map { it.id })
        assertEquals("2026-08-05T10:00:00Z", result.single().date)
    }

    @Test
    fun `Postiz draft는 게시 대상 없이도 생성할 수 있다`() {
        val draftVideo = Video(id = 11, userId = 1, title = "공개 API 초안")
        every { uploadVideo.createVideo(1, any(), any(), any()) } returns draftVideo
        every { posts.save(any()) } answers { firstArg<PublicApiPost>().copy(id = 45) }
        every { uploads.findByVideoId(11) } returns emptyList()

        val result = useCase.create(
            1,
            CreatePublicPostRequest(type = "draft"),
        )

        assertEquals("45", result.id)
        assertEquals("draft", result.type)
        assertEquals("draft", result.status)
        verify(exactly = 1) { uploadVideo.createVideo(1, "공개 API 초안", any(), any()) }
        verify(exactly = 0) { publishVideo.publishVideo(any(), any(), any()) }
    }

    @Test
    fun `Idempotency-Key 재전송은 기존 공개 API 게시를 반환하고 영상을 다시 만들지 않는다`() {
        val request = CreatePublicPostRequest(type = "draft")
        val draftVideo = Video(id = 12, userId = 1, title = "공개 API 초안")
        var persisted: PublicApiPost? = null
        every { posts.findByUserIdAndIdempotencyKey(1, "retry-key") } answers { persisted }
        every { uploadVideo.createVideo(1, any(), any(), any()) } returns draftVideo
        every { posts.save(any()) } answers {
            persisted = firstArg<PublicApiPost>().copy(id = 46)
            persisted!!
        }
        every { uploads.findByVideoId(12) } returns emptyList()

        val first = useCase.create(1, request, "retry-key")
        val second = useCase.create(1, request, "retry-key")

        assertEquals(first.id, second.id)
        verify(exactly = 1) { uploadVideo.createVideo(1, any(), any(), any()) }
        verify(exactly = 1) { posts.save(any()) }
    }

    @Test
    fun `공개 게시의 media URL도 내부망 주소를 차단한다`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.create(
                1,
                CreatePublicPostRequest(
                    type = "now",
                    posts = listOf(
                        PublicPostItem(
                            integration = PublicIntegrationRef("7"),
                            value = listOf(PublicPostValue(video = jacksonObjectMapper().readTree("\"http://127.0.0.1/video.mp4\""))),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `missing content는 provider tool이 없으면 빈 목록을 반환한다`() {
        every { posts.findByIdAndUserId(42, 1) } returns PublicApiPost(
            id = 42,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.DRAFT,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.DRAFT,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"},\"value\":[]}]}",
        )

        val result = useCase.missingContent(1, 42)

        assertEquals(emptyList(), result)
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
                        settings = jacksonObjectMapper().readTree(
                            """{"__type":"youtube","title":"설정 제목","description":"설정 설명","type":"private","tags":[{"value":"configured"}]}"""
                        ),
                    ),
                ),
            ),
        )

        assertEquals("21", response.id)
        assertEquals("processing", response.status)
        assertEquals(null, response.error)
        verify(exactly = 1) {
            publishVideo.publishVideo(1, 11, match {
                it.single().title == "설정 제목" &&
                    it.single().description == "설정 설명" &&
                    it.single().tags == listOf("configured") &&
                    it.single().visibility == com.ongo.common.enums.Visibility.PRIVATE
            })
        }
    }

    @Test
    fun `같은 영상을 공유한 다른 공개 게시물의 업로드 결과는 상태 집계에서 제외한다`() {
        val post = PublicApiPost(
            id = 47,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.NOW,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.PROCESSING,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"}}]}",
        )
        every { posts.findByIdAndUserId(47, 1) } returns post
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(
                id = 96,
                videoId = 11,
                platform = Platform.YOUTUBE,
                channelId = 7,
                status = UploadStatus.PUBLISHED,
                platformVideoId = "published-7",
                platformUrl = "https://youtube.com/watch?v=published-7",
            ),
            VideoUpload(
                id = 97,
                videoId = 11,
                platform = Platform.INSTAGRAM,
                channelId = 8,
                status = UploadStatus.FAILED,
                errorMessage = "other post failed",
            ),
        )
        every { channels.findById(7) } returns channel

        val response = useCase.get(1, 47)

        assertEquals("published", response.status)
        assertEquals("PUBLISHED", response.posts.single().status)
        assertEquals("https://youtube.com/watch?v=published-7", response.posts.single().platformUrl)
    }

    @Test
    fun `한 번의 공개 게시 요청에서 같은 integration을 중복 지정할 수 없다`() {
        every { videos.findById(11) } returns Video(id = 11, userId = 1, title = "원본", fileUrl = "https://cdn/video.mp4")

        assertFailsWith<IllegalArgumentException> {
            useCase.create(
                1,
                CreatePublicPostRequest(
                    videoId = 11,
                    posts = listOf(
                        PublicPostItem(PublicIntegrationRef("7")),
                        PublicPostItem(PublicIntegrationRef("7")),
                    ),
                ),
            )
        }
        verify(exactly = 0) { posts.save(any()) }
    }

    @Test
    fun `공개 API는 플랫폼별 제한보다 짧게 제목을 임의 변경하지 않는다`() {
        val title = "x".repeat(150)
        every { videos.findById(11) } returns Video(id = 11, userId = 1, title = "원본", fileUrl = "https://cdn/video.mp4")
        every { channels.findById(7) } returns channel
        every { posts.save(any()) } answers { firstArg<PublicApiPost>().copy(id = 46) }
        every { posts.update(any()) } answers { firstArg() }
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 94, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING),
        )
        every { publishVideo.publishVideo(any(), any(), any()) } returns
            PublishResult(11, listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)))

        useCase.create(
            1,
            CreatePublicPostRequest(
                videoId = 11,
                posts = listOf(
                    PublicPostItem(
                        integration = PublicIntegrationRef("7"),
                        value = listOf(PublicPostValue(title = title)),
                    ),
                ),
            ),
        )

        verify(exactly = 1) {
            publishVideo.publishVideo(1, 11, match { it.single().title == title })
        }
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
        every { uploads.cancelScheduledUploadsByIds(emptySet(), any()) } returns 0
        every { uploads.findByVideoId(11) } returns emptyList()
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(id = 40, videoId = 11, userId = 1, scheduledAt = scheduled.scheduledAt!!),
        )
        every { schedules.update(any()) } answers { firstArg() }

        val response = useCase.changeStatus(1, 30, ChangePublicPostStatusRequest("draft"))

        assertEquals("draft", response.status)
        verify(exactly = 1) { uploads.cancelScheduledUploadsByIds(emptySet(), any()) }
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
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"}}]}",
        )
        every { posts.findByIdAndUserId(31, 1) } returns scheduled
        every { posts.update(any()) } answers { firstArg() }
        every { channels.findById(7) } returns channel
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 92, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING, scheduledAt = scheduled.scheduledAt),
        )
        every { uploads.cancelScheduledUploadsByIds(setOf(92), any()) } returns 1
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(
                id = 41,
                videoId = 11,
                userId = 1,
                scheduledAt = scheduled.scheduledAt!!,
                platforms = mapOf("YOUTUBE#7" to emptyMap<String, String>()),
            ),
        )
        every { schedules.update(any()) } answers { firstArg() }

        useCase.deleteGroup(1, "31")

        verify(exactly = 1) { uploads.cancelScheduledUploadsByIds(setOf(92), any()) }
        verify(exactly = 1) { schedules.update(match { it.status == ScheduleStatus.CANCELLED }) }
        verify(exactly = 1) { posts.update(match { it.status == com.ongo.domain.publicapi.PublicApiPostStatus.CANCELLED }) }
    }

    @Test
    fun `같은 영상을 공유한 다른 예약은 공개 API 게시물 삭제로 취소하지 않는다`() {
        val scheduledAt = LocalDateTime.now().plusHours(2)
        val scheduled = PublicApiPost(
            id = 33,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = scheduledAt,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"}}]}",
        )
        every { posts.findByIdAndUserId(33, 1) } returns scheduled
        every { posts.update(any()) } answers { firstArg() }
        every { channels.findById(7) } returns channel
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(id = 101, videoId = 11, platform = Platform.YOUTUBE, channelId = 7, status = UploadStatus.UPLOADING, scheduledAt = scheduledAt),
            VideoUpload(id = 102, videoId = 11, platform = Platform.YOUTUBE, channelId = 8, status = UploadStatus.UPLOADING, scheduledAt = scheduledAt),
        )
        every { uploads.cancelScheduledUploadsByIds(setOf(101), any()) } returns 1
        every { schedules.findByUserId(1) } returns listOf(
            Schedule(id = 51, videoId = 11, userId = 1, scheduledAt = scheduledAt, platforms = mapOf("YOUTUBE#7" to emptyMap<String, String>())),
            Schedule(id = 52, videoId = 11, userId = 1, scheduledAt = scheduledAt, platforms = mapOf("YOUTUBE#8" to emptyMap<String, String>())),
        )
        every { schedules.update(any()) } answers { firstArg() }

        useCase.delete(1, 33)

        verify(exactly = 1) { uploads.cancelScheduledUploadsByIds(setOf(101), any()) }
        verify(exactly = 1) { schedules.update(match { it.id == 51L && it.status == ScheduleStatus.CANCELLED }) }
        verify(exactly = 0) { schedules.update(match { it.id == 52L }) }
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

    @Test
    fun `같은 영상을 공유한 다른 게시물의 완료 상태는 현재 게시물 삭제를 막지 않는다`() {
        val current = PublicApiPost(
            id = 36,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.NOW,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.PROCESSING,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"}}]}",
        )
        every { posts.findByIdAndUserId(36, 1) } returns current
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(
                id = 96,
                videoId = 11,
                platform = Platform.YOUTUBE,
                channelId = 8,
                status = UploadStatus.PUBLISHED,
                platformUrl = "https://youtu.be/other-post",
            ),
        )
        every { posts.update(any()) } answers { firstArg() }

        useCase.delete(1, 36)

        verify(exactly = 1) {
            posts.update(match { it.id == 36L && it.status == com.ongo.domain.publicapi.PublicApiPostStatus.CANCELLED })
        }
    }

    @Test
    fun `즉시 게시가 아직 업로드 중이면 게시물만 취소하지 않는다`() {
        val publishing = PublicApiPost(
            id = 34,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.NOW,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.PROCESSING,
            payloadJson = "{}",
        )
        every { posts.findByIdAndUserId(34, 1) } returns publishing
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(
                id = 94,
                videoId = 11,
                platform = Platform.YOUTUBE,
                channelId = 7,
                status = UploadStatus.UPLOADING,
            ),
        )

        assertFailsWith<com.ongo.common.exception.BusinessException> { useCase.delete(1, 34) }
        verify(exactly = 0) { posts.update(any()) }
    }

    @Test
    fun `예약 취소가 lease 경합으로 일부만 반영되면 게시물 취소를 거부한다`() {
        val scheduledAt = LocalDateTime.now().plusHours(2)
        val scheduled = PublicApiPost(
            id = 35,
            userId = 1,
            videoId = 11,
            type = com.ongo.domain.publicapi.PublicApiPostType.SCHEDULE,
            status = com.ongo.domain.publicapi.PublicApiPostStatus.SCHEDULED,
            scheduledAt = scheduledAt,
            payloadJson = "{\"posts\":[{\"integration\":{\"id\":\"7\"}}]}",
        )
        every { posts.findByIdAndUserId(35, 1) } returns scheduled
        every { posts.update(any()) } answers { firstArg() }
        every { channels.findById(7) } returns channel
        every { uploads.findByVideoId(11) } returns listOf(
            VideoUpload(
                id = 95,
                videoId = 11,
                platform = Platform.YOUTUBE,
                channelId = 7,
                status = UploadStatus.UPLOADING,
                scheduledAt = scheduledAt,
            ),
        )
        every { uploads.cancelScheduledUploadsByIds(setOf(95), any()) } returns 0
        every { schedules.findByUserId(1) } returns emptyList()

        assertFailsWith<com.ongo.common.exception.BusinessException> { useCase.delete(1, 35) }
        verify(exactly = 0) { posts.update(any()) }
        verify(exactly = 0) { schedules.update(any()) }
    }
}
