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
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.channel.ChannelStatus
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.YearMonth
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
    private val streamWriterFactories = listOf(
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
        useCase = StreamPublishUseCase(
            videoRepository = videoRepository,
            videoUploadRepository = videoUploadRepository,
            videoPlatformMetaRepository = videoPlatformMetaRepository,
            subscriptionRepository = subscriptionRepository,
            channelRepository = channelRepository,
            tokenEncryptionPort = tokenEncryptionPort,
            eventPublisher = eventPublisher,
            streamWriterFactories = streamWriterFactories,
            scheduleRepository = scheduleRepository,
            storageService = storageService,
        )

        // TransactionSynchronizationManager 정적 메서드 mock — Spring 컨텍스트 없이 실행
        mockkStatic(TransactionSynchronizationManager::class)
        every { TransactionSynchronizationManager.isActualTransactionActive() } returns true
        every { TransactionSynchronizationManager.registerSynchronization(any()) } just Runs
        every { tokenEncryptionPort.decrypt(any()) } answers { PlainToken(firstArg<EncryptedToken>().value) }
        every { storageService.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/video.mp4"
        every { storageService.deleteFile(any()) } just Runs
        every { videoRepository.update(any()) } answers { firstArg() }
    }

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
        every { file.inputStream } returns ByteArrayInputStream(ByteArray(0))
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

    private fun buildSavedUpload(id: Long = 200L, videoId: Long = 100L, platform: Platform = Platform.YOUTUBE) =
        VideoUpload(id = id, videoId = videoId, platform = platform, status = UploadStatus.UPLOADING)

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
