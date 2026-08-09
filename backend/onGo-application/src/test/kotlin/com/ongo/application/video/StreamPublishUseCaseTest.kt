package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import org.springframework.context.ApplicationEventPublisher
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformTokenRefreshResult
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamPublishUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val videoPlatformMetaRepository = mockk<VideoPlatformMetaRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val storageService = mockk<StorageService>()
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val defaultStreamWriterFactories = listOf(
        stubFactory(Platform.YOUTUBE),
        stubFactory(Platform.TIKTOK),
    )
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)

    private lateinit var useCase: StreamPublishUseCase

    private val userId = 1L
    private val fileSize = 10_000_000L // 10MB

    private fun stubFactory(targetPlatform: Platform) = object : PlatformStreamWriterFactory {
        override val platform = targetPlatform
        override fun createWriter(): PlatformStreamWriter = error("테스트에서는 비동기 writer를 실행하지 않습니다")
    }

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = createUseCase(defaultStreamWriterFactories)

        // TransactionSynchronizationManager 정적 메서드 mock — Spring 컨텍스트 없이 실행
        mockkStatic(TransactionSynchronizationManager::class)
        every { TransactionSynchronizationManager.isActualTransactionActive() } returns true
        every { TransactionSynchronizationManager.registerSynchronization(any()) } just Runs
        every { tokenEncryptionPort.decrypt(any()) } answers { PlainToken(firstArg<EncryptedToken>().value) }
        every { storageService.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/video.mp4"
        every { storageService.deleteFile(any()) } just Runs
        every { videoRepository.update(any()) } answers { firstArg() }
    }

    private fun createUseCase(
        factories: List<PlatformStreamWriterFactory>,
        platformClientPort: PlatformClientPort? = null,
    ) = StreamPublishUseCase(
            videoRepository = videoRepository,
            videoUploadRepository = videoUploadRepository,
            videoPlatformMetaRepository = videoPlatformMetaRepository,
            subscriptionRepository = subscriptionRepository,
            channelRepository = channelRepository,
            tokenEncryptionPort = tokenEncryptionPort,
            eventPublisher = eventPublisher,
            streamWriterFactories = factories,
            scheduleRepository = scheduleRepository,
            storageService = storageService,
            userWriteGuard = userWriteGuard,
            platformClientPort = platformClientPort,
        )

    @AfterEach
    fun tearDown() {
        unmockkStatic(TransactionSynchronizationManager::class)
    }

    // ─────────────────────────────────────────────
    // 공통 픽스처 헬퍼
    // ─────────────────────────────────────────────

    private fun buildFile(name: String = "test.mp4"): MultipartFile {
        val file = mockk<MultipartFile>()
        every { file.size } returns fileSize
        every { file.originalFilename } returns name
        every { file.contentType } returns "video/mp4"
        every { file.transferTo(any<java.io.File>()) } just Runs
        every { file.inputStream } returns ByteArrayInputStream(
            byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70),
        )
        return file
    }

    private fun buildRequest(
        platforms: List<PlatformPublishRequest> = listOf(buildPlatformRequest()),
    ) = StreamPublishRequest(
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag1"),
        category = "Entertainment",
        thumbnailUrl = null,
        platforms = platforms,
    )

    private fun buildPlatformRequest(
        platform: Platform = Platform.YOUTUBE,
        scheduledAt: LocalDateTime? = null,
    ) = PlatformPublishRequest(
        platform = platform,
        title = null,
        description = null,
        tags = null,
        visibility = Visibility.PUBLIC,
        scheduledAt = scheduledAt,
    )

    private fun buildActiveChannel(platform: Platform = Platform.YOUTUBE) = Channel(
        id = 10L,
        userId = userId,
        platform = platform,
        platformChannelId = "UC_test_channel",
        channelName = "테스트 채널",
        accessToken = EncryptedToken("access-token-value"),
        status = ChannelStatus.ACTIVE,
    )

    private fun buildSavedVideo(id: Long = 100L) = Video(
        id = id,
        userId = userId,
        title = "테스트 영상",
        status = UploadStatus.UPLOADING,
    )

    private fun buildSavedUpload(
        id: Long = 200L,
        videoId: Long = 100L,
        platform: Platform = Platform.YOUTUBE,
        channelId: Long? = null,
    ) = VideoUpload(id = id, videoId = videoId, platform = platform, channelId = channelId, status = UploadStatus.UPLOADING)

    private fun buildSavedMeta(id: Long = 300L, uploadId: Long = 200L) =
        VideoPlatformMeta(id = id, videoUploadId = uploadId, title = "테스트 영상")

    private fun stubSubscription(planType: PlanType = PlanType.PRO) {
        every { subscriptionRepository.findByUserId(userId) } returns
            Subscription(id = 1L, userId = userId, planType = planType)
    }

    private fun stubMonthlyCount(count: Long) {
        every { videoRepository.countByUserIdAndMonth(userId, any<YearMonth>()) } returns count
    }

    // ─────────────────────────────────────────────
    // 테스트 케이스
    // ─────────────────────────────────────────────

    // 1. 플랜 한도 초과 시 PlanLimitExceededException 발생
    @Test
    fun `플랜 한도 초과 시 PlanLimitExceededException 발생`() {
        // Given: FREE 플랜(월 5회 한도)에서 이미 5회 업로드
        stubSubscription(PlanType.FREE)
        stubMonthlyCount(5L) // FREE.monthlyUploads == 5

        val file = buildFile()
        val request = buildRequest()

        // When & Then
        val ex = assertFailsWith<PlanLimitExceededException> {
            useCase.initiate(userId, file, request)
        }

        assertEquals("월간 업로드", ex.feature)
        assertEquals(PlanType.FREE.monthlyUploads, ex.limit)

        // 플랜 한도 초과 시 저장 로직은 호출되지 않아야 함
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    // 2. 채널 미연동 시 IllegalStateException 발생
    @Test
    fun `채널 미연동 시 IllegalStateException 발생`() {
        // Given: PRO 플랜, 업로드 횟수 여유 있음
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)
        every { videoRepository.save(any()) } returns buildSavedVideo()

        // 채널이 연동되지 않음
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns null

        val file = buildFile()
        val request = buildRequest(platforms = listOf(buildPlatformRequest(Platform.YOUTUBE)))

        // When & Then
        val ex = assertFailsWith<IllegalStateException> {
            useCase.initiate(userId, file, request)
        }

        assertTrue(ex.message!!.contains("YOUTUBE") && ex.message!!.contains("연동"))
    }

    // 3. 토큰 만료 시 IllegalStateException 발생
    @Test
    fun `토큰 만료 시 IllegalStateException 발생`() {
        // Given: 채널 status가 EXPIRED
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)
        every { videoRepository.save(any()) } returns buildSavedVideo()

        val expiredChannel = buildActiveChannel().copy(status = ChannelStatus.EXPIRED)
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns expiredChannel

        val file = buildFile()
        val request = buildRequest(platforms = listOf(buildPlatformRequest(Platform.YOUTUBE)))

        // When & Then
        val ex = assertFailsWith<IllegalStateException> {
            useCase.initiate(userId, file, request)
        }

        assertTrue(ex.message!!.contains("만료"))
    }

    // 3b. tokenExpiresAt이 과거인 경우에도 IllegalStateException 발생
    @Test
    fun `tokenExpiresAt이 과거인 경우 IllegalStateException 발생`() {
        // Given: tokenExpiresAt이 과거 시각
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)
        every { videoRepository.save(any()) } returns buildSavedVideo()

        val expiredChannel = buildActiveChannel().copy(
            status = ChannelStatus.ACTIVE,
            tokenExpiresAt = LocalDateTime.now().minusDays(1),
        )
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns expiredChannel

        val file = buildFile()
        val request = buildRequest(platforms = listOf(buildPlatformRequest(Platform.YOUTUBE)))

        // When & Then
        val ex = assertFailsWith<IllegalStateException> {
            useCase.initiate(userId, file, request)
        }

        assertTrue(ex.message!!.contains("만료"))
    }

    // 4. 정상 업로드 시 Video + VideoUpload + VideoPlatformMeta 저장 호출 확인
    @Test
    fun `정상 업로드 시 Video, VideoUpload, VideoPlatformMeta 생성`() {
        // Given
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val savedVideo = buildSavedVideo(id = 100L)
        val savedUpload = buildSavedUpload(id = 200L, videoId = 100L)
        val savedMeta = buildSavedMeta(id = 300L, uploadId = 200L)

        every { videoRepository.save(any()) } returns savedVideo
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns buildActiveChannel()
        every { videoUploadRepository.save(any()) } returns savedUpload
        every { videoPlatformMetaRepository.save(any()) } returns savedMeta

        val file = buildFile()
        val request = buildRequest(platforms = listOf(buildPlatformRequest(Platform.YOUTUBE)))

        // When
        val response = useCase.initiate(userId, file, request)

        // Then: 반환된 videoId 확인
        assertEquals(100L, response.videoId)

        // Video 저장 1회 — UPLOADING 상태로
        verify(exactly = 1) {
            videoRepository.save(match { it.status == UploadStatus.UPLOADING && it.userId == userId })
        }

        // VideoUpload 저장 1회
        verify(exactly = 1) {
            videoUploadRepository.save(match {
                it.videoId == 100L && it.platform == Platform.YOUTUBE && it.status == UploadStatus.UPLOADING
            })
        }

        // VideoPlatformMeta 저장 1회
        verify(exactly = 1) {
            videoPlatformMetaRepository.save(match { it.videoUploadId == 200L })
        }
    }

    @Test
    fun `커밋 후 실제 스트리밍 경로가 복호화 토큰과 플랫폼 결과를 저장한다`() {
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val savedVideo = buildSavedVideo(id = 100L)
        val savedUpload = buildSavedUpload(id = 200L, videoId = 100L)
        val savedMeta = buildSavedMeta(id = 300L, uploadId = 200L)
        val uploads = java.util.concurrent.ConcurrentHashMap<Long, VideoUpload>()
        uploads[200L] = savedUpload
        val videos = java.util.concurrent.ConcurrentHashMap<Long, Video>()
        videos[100L] = savedVideo

        every { videoRepository.save(any()) } returns savedVideo
        every { videoRepository.findById(100L) } answers { videos[100L] }
        every { videoRepository.update(any()) } answers {
            val updated = firstArg<Video>()
            videos[updated.id!!] = updated
            updated
        }
        val expiredAccessToken = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            Charsets.UTF_8,
        )
        val platformClientPort = mockk<PlatformClientPort>()
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns
            buildActiveChannel().copy(refreshToken = EncryptedToken("refresh-token"))
        every { channelRepository.findById(10L) } returns buildActiveChannel().copy(refreshToken = EncryptedToken("refresh-token"))
        every { channelRepository.update(any()) } answers { firstArg() }
        every { tokenEncryptionPort.encrypt(any()) } answers { EncryptedToken(firstArg<PlainToken>().value) }
        every { platformClientPort.refreshToken(Platform.YOUTUBE, "refresh-token") } returns PlatformTokenRefreshResult(
            accessToken = "refreshed-access-token",
            refreshToken = null,
            expiresIn = 3600,
        )
        every { videoUploadRepository.save(any()) } returns savedUpload
        every { videoUploadRepository.findById(200L) } answers { uploads[200L] }
        every { videoUploadRepository.findByVideoId(100L) } answers { uploads.values.toList() }
        every { videoUploadRepository.claim(eq(200L), any(), any(), any()) } answers {
            val claimed = uploads[200L]!!.copy(
                leaseOwner = secondArg(),
                leaseUntil = LocalDateTime.now().plusMinutes(30),
            )
            uploads[200L] = claimed
            claimed
        }
        every { videoUploadRepository.updateOwned(any(), any()) } answers {
            val updated = firstArg<VideoUpload>()
            uploads[updated.id!!] = updated
            true
        }
        every { videoPlatformMetaRepository.save(any()) } returns savedMeta

        val seenToken = slot<PlainToken>()
        val writer = mockk<PlatformStreamWriter>(relaxed = true)
        every { writer.initSession(any(), capture(seenToken), any(), any(), any()) } answers {
            if (secondArg<PlainToken>().value == "access-token-value") throw expiredAccessToken
            "session-1"
        }
        every { writer.complete() } returns PlatformUploadResult(
            success = true,
            platformVideoId = "youtube-1",
            platformUrl = "https://youtube.test/watch/youtube-1",
            published = true,
        )
        val factory = object : PlatformStreamWriterFactory {
            override val platform = Platform.YOUTUBE
            override fun createWriter() = writer
        }
        useCase = createUseCase(listOf(factory), platformClientPort)

        val synchronization = slot<TransactionSynchronization>()
        every { TransactionSynchronizationManager.registerSynchronization(capture(synchronization)) } just Runs

        val uploadFile = buildFile()
        every { uploadFile.transferTo(any<java.io.File>()) } answers {
            Files.write(firstArg<java.io.File>().toPath(), byteArrayOf(1, 2, 3))
        }
        useCase.initiate(userId, uploadFile, buildRequest())
        synchronization.captured.afterCommit()

        waitUntil {
            uploads[200L]?.status == UploadStatus.PUBLISHED &&
                videos[100L]?.status == UploadStatus.PUBLISHED
        }

        assertEquals("refreshed-access-token", seenToken.captured.value)
        assertEquals(UploadStatus.PUBLISHED, uploads[200L]?.status)
        assertEquals("youtube-1", uploads[200L]?.platformVideoId)
        assertEquals("https://youtube.test/watch/youtube-1", uploads[200L]?.platformUrl)
        assertEquals(UploadStatus.PUBLISHED, videos[100L]?.status)
        verify(atLeast = 1) { writer.writeChunk(any(), 0L, fileSize) }
        verify(exactly = 1) { writer.complete() }
        verify { eventPublisher.publishEvent(match<UploadCompletedEvent> { it.success && it.platformUrl == "https://youtube.test/watch/youtube-1" }) }
    }

    @Test
    fun `이미 다른 작업자가 lease를 보유하면 세션 초기화 실패가 남의 행을 덮어쓰지 않는다`() {
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val savedVideo = buildSavedVideo(id = 100L)
        val savedUpload = buildSavedUpload(id = 200L, videoId = 100L)
        val savedMeta = buildSavedMeta(id = 300L, uploadId = 200L)
        every { videoRepository.save(any()) } returns savedVideo
        every { videoRepository.findById(100L) } returns savedVideo
        every { videoUploadRepository.save(any()) } returns savedUpload
        every { videoUploadRepository.findByVideoId(100L) } returns listOf(savedUpload)
        every { videoPlatformMetaRepository.save(any()) } returns savedMeta
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns buildActiveChannel()
        val claimCalled = CountDownLatch(1)
        every { videoUploadRepository.claim(eq(200L), any(), any(), any()) } answers {
            claimCalled.countDown()
            null
        }

        val writer = mockk<PlatformStreamWriter>(relaxed = true)
        every { writer.initSession(any(), any(), any(), any(), any()) } returns "session-ignored"
        val factory = object : PlatformStreamWriterFactory {
            override val platform = Platform.YOUTUBE
            override fun createWriter() = writer
        }
        useCase = createUseCase(listOf(factory))

        val synchronization = slot<TransactionSynchronization>()
        every { TransactionSynchronizationManager.registerSynchronization(capture(synchronization)) } just Runs

        useCase.initiate(userId, buildFile(), buildRequest())
        synchronization.captured.afterCommit()

        assertTrue(claimCalled.await(5, TimeUnit.SECONDS), "스트리밍 worker가 lease를 시도하지 않았습니다.")

        verify(exactly = 0) { videoUploadRepository.update(match { it.id == 200L }) }
        verify(exactly = 0) { videoUploadRepository.updateOwned(any(), any()) }
        verify(exactly = 0) { writer.initSession(any(), any(), any(), any(), any()) }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(condition(), "비동기 스트리밍 결과가 제한 시간 내에 반영되지 않았습니다.")
    }

    // 5. 예약 게시 시 Schedule 레코드 생성
    @Test
    fun `예약 게시 시 Schedule 레코드 생성`() {
        // Given
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val scheduledAt = LocalDateTime.now().plusDays(1)
        val savedVideo = buildSavedVideo(id = 100L)
        val savedUpload = buildSavedUpload(id = 200L, videoId = 100L)
        val savedMeta = buildSavedMeta(id = 300L, uploadId = 200L)

        every { videoRepository.save(any()) } returns savedVideo
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns buildActiveChannel()
        every { videoUploadRepository.save(any()) } returns savedUpload
        every { videoPlatformMetaRepository.save(any()) } returns savedMeta
        every { scheduleRepository.save(any()) } answers { firstArg() }

        val file = buildFile()
        val request = buildRequest(
            platforms = listOf(buildPlatformRequest(Platform.YOUTUBE, scheduledAt = scheduledAt)),
        )

        // When
        useCase.initiate(userId, file, request)

        // Then: Schedule 저장 1회 — durable queue 대기 상태로
        verify(exactly = 1) {
            scheduleRepository.save(match { schedule ->
                schedule.videoId == 100L &&
                    schedule.userId == userId &&
                    schedule.status == ScheduleStatus.SCHEDULED &&
                    schedule.platforms.containsKey("YOUTUBE")
            })
        }
    }

    @Test
    fun `레거시 예약 복구 시 플랫폼별 예약 시각을 durable queue 이벤트에 보존한다`() {
        val defaultScheduledAt = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        val youtubeScheduledAt = defaultScheduledAt.withHour(9)
        val instagramScheduledAt = defaultScheduledAt.withHour(13).withMinute(30)
        val video = buildSavedVideo(id = 100L).copy(
            title = "예약 영상",
            description = "예약 설명",
            tags = listOf("예약"),
            fileUrl = "https://storage.test/scheduled-video.mp4",
            fileSizeBytes = fileSize,
        )
        val savedUploads = mapOf(
            Platform.YOUTUBE to buildSavedUpload(id = 201L, videoId = 100L, platform = Platform.YOUTUBE),
            Platform.INSTAGRAM to buildSavedUpload(id = 202L, videoId = 100L, platform = Platform.INSTAGRAM),
        )
        val synchronization = slot<TransactionSynchronization>()

        every { videoRepository.findById(100L) } returns video
        every { videoUploadRepository.findByVideoIdAndPlatform(100L, any()) } returns null
        every { videoUploadRepository.save(any()) } answers {
            val requested = firstArg<VideoUpload>()
            savedUploads.getValue(requested.platform)
        }
        every { videoPlatformMetaRepository.save(any()) } answers {
            val requested = firstArg<VideoPlatformMeta>()
            requested.copy(id = requested.videoUploadId + 100L)
        }
        every { TransactionSynchronizationManager.registerSynchronization(capture(synchronization)) } just Runs

        val schedule = Schedule(
            id = 501L,
            videoId = 100L,
            userId = userId,
            scheduledAt = defaultScheduledAt,
            platforms = mapOf(
                Platform.YOUTUBE.name to mapOf("scheduledAt" to youtubeScheduledAt.toString()),
                Platform.INSTAGRAM.name to mapOf("scheduledAt" to instagramScheduledAt.toString()),
            ),
        )

        useCase.executeScheduledUpload(schedule)
        synchronization.captured.afterCommit()

        verify(exactly = 1) {
            eventPublisher.publishEvent(match<VideoPublishEvent> { event ->
                event.platformConfigs.associate { it.platform to it.scheduledAt } == mapOf(
                    Platform.YOUTUBE to null,
                    Platform.INSTAGRAM to null,
                )
            })
        }
        verify(exactly = 1) {
            videoUploadRepository.save(match {
                it.platform == Platform.YOUTUBE && it.scheduledAt == youtubeScheduledAt
            })
        }
        verify(exactly = 1) {
            videoUploadRepository.save(match {
                it.platform == Platform.INSTAGRAM && it.scheduledAt == instagramScheduledAt
            })
        }
    }

    @Test
    fun `반복 예약의 동일 플랫폼 두 계정을 각각의 durable upload row로 복구한다`() {
        val firstChannel = buildActiveChannel().copy(id = 11L, channelName = "첫 채널")
        val secondChannel = buildActiveChannel().copy(id = 12L, channelName = "둘째 채널")
        val defaultScheduledAt = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0)
        val firstAt = defaultScheduledAt.withHour(9)
        val secondAt = defaultScheduledAt.withHour(13)
        val video = buildSavedVideo(id = 100L).copy(
            fileUrl = "https://storage.test/recurring-video.mp4",
            fileSizeBytes = fileSize,
        )
        val firstUpload = buildSavedUpload(id = 211L, videoId = 100L, channelId = 11L)
        val secondUpload = buildSavedUpload(id = 212L, videoId = 100L, channelId = 12L)
        val synchronization = slot<TransactionSynchronization>()

        every { videoRepository.findById(100L) } returns video
        every { channelRepository.findById(11L) } returns firstChannel
        every { channelRepository.findById(12L) } returns secondChannel
        every { videoUploadRepository.findByVideoIdAndChannelId(100L, 11L) } returns null
        every { videoUploadRepository.findByVideoIdAndChannelId(100L, 12L) } returns null
        every { videoUploadRepository.save(match { it.channelId == 11L }) } returns firstUpload
        every { videoUploadRepository.save(match { it.channelId == 12L }) } returns secondUpload
        every { videoPlatformMetaRepository.save(any()) } answers {
            val requested = firstArg<VideoPlatformMeta>()
            requested.copy(id = requested.videoUploadId + 100L)
        }
        every { TransactionSynchronizationManager.registerSynchronization(capture(synchronization)) } just Runs

        useCase.executeScheduledUpload(
            Schedule(
                id = 601L,
                videoId = 100L,
                userId = userId,
                scheduledAt = defaultScheduledAt,
                platforms = mapOf(
                    "YOUTUBE#11" to mapOf("scheduledAt" to firstAt.toString()),
                    "YOUTUBE#12" to mapOf("scheduledAt" to secondAt.toString()),
                ),
            ),
        )
        synchronization.captured.afterCommit()

        verify(exactly = 1) {
            videoUploadRepository.save(match {
                it.platform == Platform.YOUTUBE && it.channelId == 11L && it.scheduledAt == firstAt
            })
        }
        verify(exactly = 1) {
            videoUploadRepository.save(match {
                it.platform == Platform.YOUTUBE && it.channelId == 12L && it.scheduledAt == secondAt
            })
        }
        verify(exactly = 1) {
            eventPublisher.publishEvent(match<VideoPublishEvent> { event ->
                event.platformConfigs.map { it.channelId to it.scheduledAt } == listOf(11L to null, 12L to null)
            })
        }
    }

    @Test
    fun `직접 스트리밍은 플랫폼이 예약을 지원하지 않으면 저장 전에 거부한다`() {
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val request = buildRequest(
            platforms = listOf(
                buildPlatformRequest(
                    platform = Platform.TIKTOK,
                    scheduledAt = LocalDateTime.now().plusDays(1),
                ),
            ),
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.initiate(userId, buildFile(), request)
        }

        assertTrue(ex.message!!.contains("예약 게시를 지원하지 않습니다"))
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    // 6. 예약 없는 즉시 게시 시 Schedule 미생성
    @Test
    fun `즉시 게시 시 Schedule 레코드 생성하지 않음`() {
        // Given: scheduledAt = null (즉시 게시)
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        every { videoRepository.save(any()) } returns buildSavedVideo(id = 100L)
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns buildActiveChannel()
        every { videoUploadRepository.save(any()) } returns buildSavedUpload(id = 200L, videoId = 100L)
        every { videoPlatformMetaRepository.save(any()) } returns buildSavedMeta(id = 300L, uploadId = 200L)

        val file = buildFile()
        val request = buildRequest(platforms = listOf(buildPlatformRequest(Platform.YOUTUBE, scheduledAt = null)))

        // When
        useCase.initiate(userId, file, request)

        // Then: Schedule 저장 호출 없음
        verify(exactly = 0) { scheduleRepository.save(any()) }
    }

    // 7. 멀티 플랫폼 업로드 시 각 플랫폼별 VideoUpload 생성
    @Test
    fun `멀티 플랫폼 업로드 시 각 플랫폼별 VideoUpload 생성`() {
        // Given: YOUTUBE + TIKTOK 2개 플랫폼
        stubSubscription(PlanType.PRO)
        stubMonthlyCount(0L)

        val savedVideo = buildSavedVideo(id = 100L)
        every { videoRepository.save(any()) } returns savedVideo

        every { channelRepository.findByUserIdAndPlatform(userId, Platform.YOUTUBE) } returns
            buildActiveChannel(Platform.YOUTUBE)
        every { channelRepository.findByUserIdAndPlatform(userId, Platform.TIKTOK) } returns
            buildActiveChannel(Platform.TIKTOK)

        every { videoUploadRepository.save(match { it.platform == Platform.YOUTUBE }) } returns
            buildSavedUpload(id = 201L, videoId = 100L, platform = Platform.YOUTUBE)
        every { videoUploadRepository.save(match { it.platform == Platform.TIKTOK }) } returns
            buildSavedUpload(id = 202L, videoId = 100L, platform = Platform.TIKTOK)

        every { videoPlatformMetaRepository.save(match { it.videoUploadId == 201L }) } returns
            buildSavedMeta(id = 301L, uploadId = 201L)
        every { videoPlatformMetaRepository.save(match { it.videoUploadId == 202L }) } returns
            buildSavedMeta(id = 302L, uploadId = 202L)

        val file = buildFile()
        val request = buildRequest(
            platforms = listOf(
                buildPlatformRequest(Platform.YOUTUBE),
                buildPlatformRequest(Platform.TIKTOK),
            ),
        )

        // When
        val response = useCase.initiate(userId, file, request)

        // Then
        assertEquals(100L, response.videoId)

        // 각 플랫폼별 VideoUpload 저장 1회씩 — 총 2회
        verify(exactly = 1) { videoUploadRepository.save(match { it.platform == Platform.YOUTUBE }) }
        verify(exactly = 1) { videoUploadRepository.save(match { it.platform == Platform.TIKTOK }) }

        // VideoPlatformMeta도 각 upload에 대해 1회씩 — 총 2회
        verify(exactly = 1) { videoPlatformMetaRepository.save(match { it.videoUploadId == 201L }) }
        verify(exactly = 1) { videoPlatformMetaRepository.save(match { it.videoUploadId == 202L }) }
    }
}
