package com.ongo.application.ugc.shorts

import com.ongo.application.ai.audio.AudioPreparationException
import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.ai.audio.TranscriptionAudioPort
import com.ongo.application.credit.CreditBalanceInfo
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.dto.CreatePipelineRunRequest
import com.ongo.application.ugc.shorts.dto.HookSelection
import com.ongo.application.ugc.shorts.dto.HookSelectionRequest
import com.ongo.application.ugc.shorts.dto.ScheduleConfirmRequest
import com.ongo.application.video.StorageService
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.RunStageRepository
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.ugc.shorts.ClipPublication
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 파이프라인 유스케이스의 상태 가드와 워크스페이스 격리 검증.
 * 단계 실행 자체는 [ShortsPipelineOrchestratorTest] 가 담당한다.
 */
@ExtendWith(MockKExtension::class)
class ShortsPipelineUseCaseTest {

    @MockK
    lateinit var pipelineRunRepository: PipelineRunRepository

    @MockK
    lateinit var runStageRepository: RunStageRepository

    @MockK
    lateinit var shortsClipRepository: ShortsClipRepository

    @MockK
    lateinit var clipHookRepository: ClipHookRepository

    @MockK
    lateinit var shortsTemplateRepository: ShortsTemplateRepository

    @MockK
    lateinit var videoRepository: VideoRepository

    @MockK
    lateinit var workspaceRepository: WorkspaceRepository

    @MockK
    lateinit var renderSpecBuilder: ShortsRenderSpecBuilder

    @MockK
    lateinit var eventPublisher: ApplicationEventPublisher

    @MockK
    lateinit var audioPort: TranscriptionAudioPort

    @MockK
    lateinit var storageService: StorageService

    @MockK
    lateinit var creditService: CreditService

    // logActivity 는 응답을 돌려주므로 relaxUnitFun 으로는 부족하다.
    @MockK(relaxed = true)
    lateinit var activityLogUseCase: com.ongo.application.activitylog.ActivityLogUseCase

    @MockK
    lateinit var pilotEventRepository: ShortsPilotEventRepository

    @MockK
    lateinit var channelRepository: ChannelRepository

    @MockK
    lateinit var clipPublicationRepository: ClipPublicationRepository

    /** 대상 하나의 게시 결과. 기본은 예약 성공이다. */
    private fun publication(
        clipId: Long,
        platform: String,
        status: ClipPublicationStatus = ClipPublicationStatus.SCHEDULED,
        errorMessage: String? = null,
    ) = ClipPublication(
        id = clipId * 100,
        clipId = clipId,
        platform = platform,
        status = status,
        scheduledAt = Instant.parse("2026-08-14T07:00:00Z"),
        errorMessage = errorMessage,
    )

    /** 예약 대상 검증용 채널. 기본은 요청 사용자 소유의 정상 YouTube 계정이다. */
    private fun activeChannel(
        id: Long = 77L,
        userId: Long = this.userId,
        platform: Platform = Platform.YOUTUBE,
        status: ChannelStatus = ChannelStatus.ACTIVE,
        tokenExpiresAt: LocalDateTime? = null,
    ) = Channel(
        id = id,
        userId = userId,
        workspaceId = workspaceId,
        platform = platform,
        platformChannelId = "pc-$id",
        channelName = "채널 $id",
        accessToken = EncryptedToken("token"),
        status = status,
        tokenExpiresAt = tokenExpiresAt,
    )

    /*
     * @InjectMockKs 는 maxSourceBytes 같은 원시 타입에 0 을 넣는다. 그 상태에서는 크기
     * 상한 테스트가 "모든 영상이 초과"로 항상 통과해 아무것도 증명하지 못한다.
     */
    private lateinit var useCase: ShortsPipelineUseCase

    @BeforeEach
    fun setUpUseCase() {
        useCase = ShortsPipelineUseCase(
            pipelineRunRepository = pipelineRunRepository,
            runStageRepository = runStageRepository,
            shortsClipRepository = shortsClipRepository,
            clipHookRepository = clipHookRepository,
            shortsTemplateRepository = shortsTemplateRepository,
            videoRepository = videoRepository,
            workspaceRepository = workspaceRepository,
            renderSpecBuilder = renderSpecBuilder,
            eventPublisher = eventPublisher,
            maxSourceBytes = MAX_SOURCE_BYTES,
            maxSourceDurationMs = MAX_SOURCE_DURATION_MS,
            audioPort = audioPort,
            storageService = storageService,
            pilotEventRepository = pilotEventRepository,
            creditService = creditService,
            activityLogUseCase = activityLogUseCase,
        channelRepository = channelRepository,
        clipPublicationRepository = clipPublicationRepository,
        )
        /*
         * 대부분의 테스트는 크레딧이 관심사가 아니다. 기본은 충분한 잔액으로 두고,
         * 부족을 보는 테스트만 다시 지정한다. 조회 자체를 하지 않아야 하는 테스트는
         * verify(exactly = 0) 로 확인하므로 이 스텁이 그 검증을 가리지 않는다.
         */
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS))
    }

    private val userId = 1L
    private val workspaceId = 10L
    private val runId = 100L
    private val videoId = 55L

    private fun grantAccess(vararg accessibleIds: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            accessibleIds.map { Workspace(id = it, ownerId = userId, name = "WS $it", slug = "ws-$it") }
    }

    private fun run(
        status: PipelineRunStatus,
        ownerWorkspaceId: Long = workspaceId,
    ) = PipelineRun(
        id = runId,
        workspaceId = ownerWorkspaceId,
        userId = userId,
        sourceVideoId = videoId,
        status = status,
    )

    private fun clip(
        id: Long,
        seq: Int,
        status: ClipStatus = ClipStatus.DRAFT,
        renderedVideoId: Long? = null,
    ) = ShortsClip(
        id = id,
        runId = runId,
        seq = seq,
        startMs = 0,
        endMs = 45_000,
        status = status,
        renderedVideoId = renderedVideoId,
    )

    // ---- 워크스페이스 격리 ----

    @Test
    fun `접근 권한 없는 워크스페이스면 NotFoundException`() {
        grantAccess(999L)

        assertFailsWith<NotFoundException> {
            useCase.getRunDetail(userId, workspaceId, runId)
        }
    }

    @Test
    fun `다른 워크스페이스의 실행에 접근하면 ACCESS_DENIED`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns
            run(PipelineRunStatus.COMPLETED, ownerWorkspaceId = 77L)

        val ex = assertFailsWith<BusinessException> {
            useCase.getRunDetail(userId, workspaceId, runId)
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    @Test
    fun `실행이 없으면 SHORTS_RUN_NOT_FOUND`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns null

        val ex = assertFailsWith<BusinessException> {
            useCase.getRunDetail(userId, workspaceId, runId)
        }
        assertEquals("SHORTS_RUN_NOT_FOUND", ex.code)
    }

    // ---- 실행 생성 ----

    @Test
    fun `원본 영상이 없으면 SHORTS_SOURCE_VIDEO_NOT_FOUND`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns null

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }
        assertEquals("SHORTS_SOURCE_VIDEO_NOT_FOUND", ex.code)
    }

    @Test
    fun `남의 영상으로 실행을 만들면 ACCESS_DENIED`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = 999L, title = "남의 롱폼")

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    /*
     * TRANSCRIBE 는 원본을 통째로 읽어 오디오를 뽑는다. 이 한도를 단계 안에서 터뜨리면
     * 사용자는 실행이 만들어지고 크레딧이 걸린 뒤에야 실패를 본다.
     */
    @Test
    fun `상한을 넘는 원본은 실행 행도 이벤트도 만들기 전에 거절한다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "너무 큰 롱폼",
            fileSizeBytes = MAX_SOURCE_BYTES + 1,
        )

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_SOURCE_VIDEO_TOO_LARGE", ex.code)
        verify(exactly = 0) { pipelineRunRepository.save(any()) }
        verify(exactly = 0) { pipelineRunRepository.saveIdempotently(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    @Test
    fun `상한과 같은 크기의 원본은 통과시킨다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "딱 상한",
            fileSizeBytes = MAX_SOURCE_BYTES,
        )
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 1) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    /* 모른다는 것이 초과했다는 증거는 아니다. 외부 URL 로 만든 행은 크기가 비어 있다. */
    @Test
    fun `크기를 모르는 원본은 거절하지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "크기 미상", fileSizeBytes = null)
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 1) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    // ---- 크레딧 사전 검사 ----

    private fun balance(total: Int) = CreditBalanceInfo(
        totalBalance = total,
        freeRemaining = total,
        freeMonthly = total,
        purchasedBalance = 0,
        freeResetDate = LocalDate.of(2026, 9, 1),
    )

    /**
     * 완주 비용이 애초에 모자란 요청.
     *
     * 지금까지는 단계마다 차감하다 중간에 죽었고, 그때 앞 단계에서 쓴 크레딧은 돌아오지
     * 않았다(실패한 단계분만 환불). 결과물도 없이 크레딧만 사라지는 것이 확정된 요청을
     * 여기서 걷어낸다.
     */
    @Test
    fun `완주 크레딧이 모자라면 실행 행도 이벤트도 만들지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns SHORT_SOURCE_DURATION_MS
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS) - 1)

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_INSUFFICIENT_CREDIT_FOR_RUN", ex.code)
        assertNothingStarted()
        // 선검사는 읽기만 한다. 여기서 차감·예약하면 취소·만료 처리가 새로 필요해진다.
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any()) }
    }

    @Test
    fun `필요한 만큼 딱 있으면 실행을 만든다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns SHORT_SOURCE_DURATION_MS
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS))
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 1) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    /*
     * 하드코딩한 37 로 검사하면 AiFeature 단가가 바뀔 때 조용히 틀린 값으로 막거나 통과시킨다.
     * 필요 크레딧은 오케스트레이터가 실제로 차감하는 것과 같은 매핑에서 나와야 한다.
     *
     * 과금 창 이하 원본으로 비교한다 — 그 구간이 종전 동작과 동치여야 하기 때문이다.
     */
    @Test
    fun `필요 크레딧은 단계별 AiFeature 합계와 같다`() {
        val expected = ShortsPipelineCreditRequirements.FEATURE_BY_STAGE.values.sumOf { it.creditCost }

        assertEquals(expected, ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS))
        // 차감이 없는 단계는 매핑에 없어야 한다 — 있으면 필요액이 부풀어 생성이 막힌다.
        assertEquals(null, ShortsPipelineCreditRequirements.FEATURE_BY_STAGE[PipelineStage.RENDER_SPEC])
        assertEquals(null, ShortsPipelineCreditRequirements.FEATURE_BY_STAGE[PipelineStage.SCHEDULE])
    }

    /*
     * 이미 만들어진 실행을 되돌려주는 것은 새 작업이 아니다. 잔액이 바닥났다고 조회까지
     * 막으면 사용자는 자기 실행을 볼 수 없게 된다.
     */
    @Test
    fun `멱등 키로 기존 실행을 반환할 때는 잔액을 보지 않는다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "key-1") } returns
            run(PipelineRunStatus.RUNNING)
        every { videoRepository.findById(videoId) } returns sourceVideo()

        useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "key-1",
        )

        verify(exactly = 0) { creditService.getBalance(any()) }
        assertNothingStarted()
    }

    /* 크기·길이에서 먼저 걸리면 잔액을 조회할 이유가 없다. */
    @Test
    fun `크기 상한에 걸리면 잔액을 조회하지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo(fileSizeBytes = MAX_SOURCE_BYTES + 1)

        assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        verify(exactly = 0) { creditService.getBalance(any()) }
    }

    // ---- 전환 퍼널 측정 ----

    /**
     * 차단은 그 자체가 세어야 할 사건이고, 그 트랜잭션은 곧 롤백된다.
     *
     * 일반 기록으로 남기면 세려는 사건과 함께 사라져 분모가 영원히 0 이 된다.
     * 그래서 독립 트랜잭션 경로를 써야 한다.
     */
    @Test
    fun `완주 크레딧 부족은 롤백을 견디는 독립 기록으로 남는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns SHORT_SOURCE_DURATION_MS
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS) - 1)

        assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        verify(exactly = 1) {
            activityLogUseCase.logActivityIndependently(
                userId = userId,
                action = ActivityLogActions.SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT,
                entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
                entityId = null,
            )
        }
        // 트랜잭션 결속 기록으로 남기면 롤백에 휩쓸린다.
        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    /* 크기·길이·소유권 거절은 다른 사건이다. 같은 이름으로 섞으면 집계가 무의미해진다. */
    @Test
    fun `크레딧 외의 거절은 차단 사건으로 세지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo(fileSizeBytes = MAX_SOURCE_BYTES + 1)

        assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        verify(exactly = 0) { activityLogUseCase.logActivityIndependently(any(), any(), any(), any()) }
    }

    @Test
    fun `크레딧이 충분한 성공 실행에는 차단 사건을 남기지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns 60_000
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 0) { activityLogUseCase.logActivityIndependently(any(), any(), any(), any()) }
    }

    /* 실행이 실제로 만들어졌을 때만 성공 증거를 남긴다. */
    @Test
    fun `실행 생성 성공은 트랜잭션 결속 기록으로 한 번 남는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns 60_000
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        // 내용·IP·User-Agent 를 담지 않는다. runId 만 남긴다.
        verify(exactly = 1) {
            activityLogUseCase.logActivity(
                userId = userId,
                action = ActivityLogActions.SHORTS_RUN_CREATED,
                entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
                entityId = runId,
            )
        }
    }

    /*
     * 동시 요청 둘이 같은 멱등 키로 들어오면 하나만 행을 만들고 다른 하나는 그 행을
     * 돌려받는다(created=false). 진 쪽이 생성 사건을 남기면 실행 1건이 2건으로 세어진다.
     */
    @Test
    fun `동시 멱등 저장에서 진 요청은 실행 생성 사건을 남기지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns 60_000
        // 조회 시점에는 아직 없다가, 저장 시점에 다른 요청이 먼저 넣은 경쟁 상황.
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "key-race") } returns null
        every { pipelineRunRepository.saveIdempotently(any()) } returns
            PipelineRunRepository.SaveResult(run(PipelineRunStatus.RUNNING), created = false)

        useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "key-race",
        )

        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    /* 멱등 재사용으로 기존 실행을 돌려준 것은 새 사건이 아니다. */
    @Test
    fun `멱등 재사용은 실행 생성 사건으로 세지 않는다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "key-1") } returns
            run(PipelineRunStatus.RUNNING)
        every { videoRepository.findById(videoId) } returns sourceVideo()

        useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "key-1",
        )

        verify(exactly = 0) { activityLogUseCase.logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    // ---- 길이 사전 검사 ----

    /**
     * 크기 상한만으로는 못 막는 경우. 저화질 롱폼은 작고 길다.
     *
     * 이 원본은 1MB 라 크기 검사를 여유롭게 통과하지만 길이는 상한의 두 배다. 통과시키면
     * 조각 수·전사 요금·렌더 큐 대기가 전부 길이에 비례해 늘어난다.
     */
    @Test
    fun `크기는 작아도 상한을 넘게 길면 실행 행도 이벤트도 만들기 전에 거절한다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo(fileSizeBytes = 1_000_000L)
        every { audioPort.probeDurationMs(SOURCE_URL) } returns MAX_SOURCE_DURATION_MS * 2

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_SOURCE_VIDEO_TOO_LONG", ex.code)
        assertNothingStarted()
    }

    @Test
    fun `상한과 같은 길이는 통과시킨다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns MAX_SOURCE_DURATION_MS
        // 상한 길이의 실행은 전사 크레딧이 길이에 비례해 커진다. 잔액도 그만큼 필요하다.
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(MAX_SOURCE_DURATION_MS))
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 1) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    /*
     * 청구 근거는 실행에 고정돼야 한다. 저장하지 않으면 재실행 때 다시 재게 되고,
     * 두 측정이 어긋나면 같은 실행이 두 번 다른 금액을 낸다.
     */
    @Test
    fun `측정한 원본 길이를 실행에 고정 저장한다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns MAX_SOURCE_DURATION_MS
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(MAX_SOURCE_DURATION_MS))
        val saved = slot<PipelineRun>()
        every { pipelineRunRepository.save(capture(saved)) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        assertEquals(MAX_SOURCE_DURATION_MS, saved.captured.sourceDurationMs)
    }

    /*
     * 프로브는 외부 프로세스를 띄우는 일이고, 무엇보다 두 번 재면 두 값이 어긋날 수 있다.
     * 선검사·견적·저장이 **한 측정값**을 공유해야 인용한 금액과 청구한 금액이 같다.
     */
    @Test
    fun `길이 프로브는 생성당 한 번만 부른다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns SHORT_SOURCE_DURATION_MS
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        verify(exactly = 1) { audioPort.probeDurationMs(SOURCE_URL) }
    }

    /*
     * 선검사가 길이를 반영하지 않으면, 37 만 보고 통과시킨 긴 원본이 TRANSCRIBE 에서
     * 크레딧 부족으로 죽는다 — 선검사가 막으려던 바로 그 상황이다.
     */
    @Test
    fun `긴 원본은 늘어난 필요 크레딧으로 생성 전에 막는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns MAX_SOURCE_DURATION_MS
        // 짧은 원본이었다면 충분했을 잔액. 길이를 무시하면 이 요청이 통과한다.
        every { creditService.getBalance(userId) } returns
            balance(ShortsPipelineCreditRequirements.totalCreditsForRun(SHORT_SOURCE_DURATION_MS))

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_INSUFFICIENT_CREDIT_FOR_RUN", ex.code)
        assertNothingStarted()
    }

    /*
     * 화면은 이 메시지를 그대로 보여준다. 숫자만 바뀌고 근거가 없으면 사용자는 왜 금액이
     * 달라졌는지 알 수 없고, 그건 조용히 다르게 청구하는 것과 같다.
     */
    @Test
    fun `부족 안내는 필요 크레딧과 함께 길이 기준을 밝힌다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns MAX_SOURCE_DURATION_MS
        every { creditService.getBalance(userId) } returns balance(0)

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        val required = ShortsPipelineCreditRequirements.totalCreditsForRun(MAX_SOURCE_DURATION_MS)
        assertTrue(ex.message!!.contains("${required}개"), "필요 크레딧이 안내에 없다: ${ex.message}")
        assertTrue(ex.message!!.contains("10분마다"), "과금 단위가 안내에 없다: ${ex.message}")
        assertTrue(ex.message!!.contains("60분"), "측정된 길이가 안내에 없다: ${ex.message}")
    }

    /*
     * URL 이 없는 원본은 잴 대상이 없다. 근거가 없으니 지어내지 않고, 저장도 NULL 이며,
     * 필요 크레딧은 종전 정액이다.
     */
    @Test
    fun `길이를 잴 수 없는 원본은 길이를 저장하지 않고 정액으로 본다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo(fileUrl = null)
        val saved = slot<PipelineRun>()
        every { pipelineRunRepository.save(capture(saved)) } returns run(PipelineRunStatus.PENDING)
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        assertEquals(null, saved.captured.sourceDurationMs)
        verify(exactly = 0) { audioPort.probeDurationMs(any()) }
    }

    /*
     * 프로브를 못 쓴다는 사실은 원본이 짧다는 증거가 아니다. "모르니 일단 진행"하면
     * 돈이 걸린 파이프라인이 한참 뒤에 죽는다.
     */
    @Test
    fun `길이 프로브를 쓸 수 없으면 실행을 만들지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } throws
            AudioPreparationException("원본 길이를 확인할 수 없습니다")

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_SOURCE_DURATION_UNKNOWN", ex.code)
        assertNothingStarted()
    }

    /* 어댑터가 계약을 깨고 0 을 돌려줘도 "0초짜리라 상한 이하"로 통과시키지 않는다. */
    @Test
    fun `길이 프로브가 0 을 주면 짧은 영상으로 취급하지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { audioPort.probeDurationMs(SOURCE_URL) } returns 0L

        val ex = assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        assertEquals("SHORTS_SOURCE_DURATION_UNKNOWN", ex.code)
        assertNothingStarted()
    }

    /* 크기 상한이 먼저 걸리면 길이를 잴 필요가 없다 — 프로브는 원격 원본을 읽는 비용이 있다. */
    @Test
    fun `크기 상한에 걸리면 길이를 재지 않는다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns sourceVideo(fileSizeBytes = MAX_SOURCE_BYTES + 1)

        assertFailsWith<BusinessException> {
            useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))
        }

        verify(exactly = 0) { audioPort.probeDurationMs(any()) }
        assertNothingStarted()
    }

    /* 멱등 재사용은 길이 검사보다 먼저다. 같은 키로 다시 부를 때마다 원격 프로브를 돌리면 안 된다. */
    @Test
    fun `멱등 키로 기존 실행을 되돌려줄 때는 길이를 재지 않는다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "key-1") } returns
            run(PipelineRunStatus.RUNNING)
        every { videoRepository.findById(videoId) } returns sourceVideo()

        useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "key-1",
        )

        verify(exactly = 0) { audioPort.probeDurationMs(any()) }
        assertNothingStarted()
    }

    /** 실행 행도, 이벤트도, 오디오 준비도 시작되지 않았음을 한 번에 본다. */
    private fun assertNothingStarted() {
        verify(exactly = 0) { pipelineRunRepository.save(any()) }
        verify(exactly = 0) { pipelineRunRepository.saveIdempotently(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
        verify(exactly = 0) { audioPort.prepare(any()) }
    }

    /**
     * @param fileUrl `null` 이면 길이를 잴 대상이 없는 원본이다. 프로브를 부르지 않고
     *   길이도 저장하지 않는 경로를 검증할 때 쓴다.
     */
    private fun sourceVideo(
        fileSizeBytes: Long? = 1_000_000L,
        fileUrl: String? = SOURCE_URL,
    ) = Video(
        id = videoId,
        userId = userId,
        title = "내 롱폼",
        fileUrl = fileUrl,
        fileSizeBytes = fileSizeBytes,
    )

    @Test
    fun `실행을 만들면 TRANSCRIBE 부터 이벤트를 발행한다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { pipelineRunRepository.save(any()) } returns run(PipelineRunStatus.PENDING)
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        val response = useCase.createRun(userId, workspaceId, CreatePipelineRunRequest(sourceVideoId = videoId))

        assertEquals(PipelineStage.TRANSCRIBE, event.captured.fromStage)
        assertEquals(runId, event.captured.runId)
        assertEquals("내 롱폼", response.sourceVideoTitle)
    }

    @Test
    fun `자동 쇼츠 실행은 예약 설정을 영속화하고 서버 워커 시작 이벤트를 발행한다`() {
        grantAccess(workspaceId)
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        val startAt = Instant.parse("2026-08-10T01:00:00Z")
        val saved = slot<PipelineRun>()
        every { pipelineRunRepository.save(capture(saved)) } returns run(PipelineRunStatus.PENDING)
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(
                sourceVideoId = videoId,
                autoSchedule = true,
                scheduleStartAt = startAt,
                scheduleIntervalHours = 2,
                platforms = listOf("YOUTUBE#channel-1", "INSTAGRAM#channel-2"),
            ),
        )

        assertEquals(true, saved.captured.autoSchedule)
        assertEquals(startAt, saved.captured.autoScheduleStartAt)
        assertEquals(2, saved.captured.autoScheduleIntervalHours)
        assertEquals(listOf("YOUTUBE#channel-1", "INSTAGRAM#channel-2"), saved.captured.autoSchedulePlatforms)
        assertEquals(PipelineStage.TRANSCRIBE, event.captured.fromStage)
    }

    @Test
    fun `자동 쇼츠는 예약 설정이 빠지면 생성되지 않는다`() {
        grantAccess(workspaceId)

        assertFailsWith<IllegalArgumentException> {
            useCase.createRun(
                userId,
                workspaceId,
                CreatePipelineRunRequest(sourceVideoId = videoId, autoSchedule = true),
            )
        }
        verify(exactly = 0) { pipelineRunRepository.save(any()) }
    }

    @Test
    fun `같은 멱등 키로 재시도하면 기존 실행을 반환하고 이벤트를 다시 발행하지 않는다`() {
        grantAccess(workspaceId)
        val existing = run(PipelineRunStatus.RUNNING)
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "retry-key") } returns existing
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")

        val response = useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "retry-key",
        )

        assertEquals(runId, response.id)
        verify(exactly = 0) { pipelineRunRepository.save(any()) }
        verify(exactly = 0) { pipelineRunRepository.saveIdempotently(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    @Test
    fun `동시 멱등 저장에서 기존 실행을 돌려받으면 이벤트를 한 번만 발행한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, "race-key") } returns null
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { pipelineRunRepository.saveIdempotently(any()) } returns
            PipelineRunRepository.SaveResult(run(PipelineRunStatus.PENDING), created = false)

        val response = useCase.createRun(
            userId,
            workspaceId,
            CreatePipelineRunRequest(sourceVideoId = videoId),
            idempotencyKey = "race-key",
        )

        assertEquals(runId, response.id)
        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    // ---- 단계 재실행 가드 ----

    @Test
    fun `실행 중에는 재실행할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.RUNNING)

        val ex = assertFailsWith<BusinessException> {
            useCase.rerunStage(userId, workspaceId, runId, "SEGMENT")
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `예약 단계는 재실행할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.COMPLETED)

        val ex = assertFailsWith<BusinessException> {
            useCase.rerunStage(userId, workspaceId, runId, "SCHEDULE")
        }
        assertEquals("SHORTS_STAGE_NOT_RERUNNABLE", ex.code)
    }

    @Test
    fun `알 수 없는 단계명이면 SHORTS_RUN_INVALID_STATE`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.FAILED)

        val ex = assertFailsWith<BusinessException> {
            useCase.rerunStage(userId, workspaceId, runId, "NOT_A_STAGE")
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `재실행하면 해당 단계부터 이후 단계 기록을 지운다`() {
        grantAccess(workspaceId)
        val target = run(PipelineRunStatus.AWAITING_HOOK_SELECTION)
        every { pipelineRunRepository.findById(runId) } returns target
        every { runStageRepository.deleteFrom(runId, any()) } returns 3
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1), clip(2L, 2))
        every { clipHookRepository.deleteByClipIds(any()) } returns 4
        every { shortsClipRepository.update(any()) } answers { firstArg() }
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs
        every { pilotEventRepository.save(any()) } answers { firstArg() }

        useCase.rerunStage(userId, workspaceId, runId, "HOOK")

        verify { runStageRepository.deleteFrom(runId, PipelineStage.HOOK.sortOrder) }
        // HOOK 재실행이면 후킹만 지우고 클립은 DRAFT 로 되돌린다
        verify { clipHookRepository.deleteByClipIds(listOf(1L, 2L)) }
        verify(exactly = 0) { shortsClipRepository.deleteByRunId(any()) }
    }

    @Test
    fun `SEGMENT 부터 재실행하면 클립을 전부 삭제한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.FAILED)
        every { runStageRepository.deleteFrom(runId, any()) } returns 5
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.deleteByClipIds(any()) } returns 2
        every { shortsClipRepository.deleteByRunId(runId) } returns 1
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs
        every { pilotEventRepository.save(any()) } answers { firstArg() }

        useCase.rerunStage(userId, workspaceId, runId, "SEGMENT")

        verify { shortsClipRepository.deleteByRunId(runId) }
    }

    // ---- 파일럿 측정: 재실행 증거 ----

    /**
     * 순서가 전부다.
     *
     * `deleteFrom` 은 스테이지 행을, 이어지는 분기는 클립과 후킹을 하드 삭제한다. 그 뒤에
     * 기록하면 남길 대상이 이미 사라진 뒤이고, 수정률은 파일럿에서 가장 비싼 신호다.
     */
    @Test
    fun `재실행 이벤트를 스테이지 삭제보다 먼저 기록한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_HOOK_SELECTION)
        every { runStageRepository.deleteFrom(runId, any()) } returns 3
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.deleteByClipIds(any()) } returns 2
        every { shortsClipRepository.update(any()) } answers { firstArg() }
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs
        val recorded = slot<ShortsPilotEvent>()
        every { pilotEventRepository.save(capture(recorded)) } answers { firstArg() }

        useCase.rerunStage(userId, workspaceId, runId, "HOOK")

        // verifyOrder 는 호출 순서를 강제한다. 기록이 삭제 뒤로 밀리면 실패한다.
        verifyOrder {
            pilotEventRepository.save(any())
            runStageRepository.deleteFrom(runId, PipelineStage.HOOK.sortOrder)
            clipHookRepository.deleteByClipIds(any())
        }

        assertEquals(ShortsPilotEventType.STAGE_RERUN, recorded.captured.eventType)
        assertEquals(ShortsPilotActorType.CUSTOMER, recorded.captured.actorType)
        assertEquals(userId, recorded.captured.actorId)
        assertEquals(runId, recorded.captured.runId)
        // 측정 행에는 콘텐츠를 담지 않는다. 회차는 렌더 실패 전용이다.
        assertEquals(null, recorded.captured.attemptNo)
    }

    // ---- 후킹 선택 가드 ----

    @Test
    fun `후킹 선택 대기 상태가 아니면 선택할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.RUNNING)

        val ex = assertFailsWith<BusinessException> {
            useCase.selectHooks(userId, workspaceId, runId, HookSelectionRequest(selections = emptyList()))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `이 실행의 클립이 아니면 SHORTS_CLIP_NOT_FOUND`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_HOOK_SELECTION)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.findByClipIds(any()) } returns emptyList()
        every { clipPublicationRepository.findByClipIds(any()) } returns emptyList()

        val ex = assertFailsWith<BusinessException> {
            useCase.selectHooks(
                userId, workspaceId, runId,
                HookSelectionRequest(selections = listOf(HookSelection(clipId = 999L, variant = HookVariant.A))),
            )
        }
        assertEquals("SHORTS_CLIP_NOT_FOUND", ex.code)
    }

    @Test
    fun `discardClipIds 로 넘긴 클립은 DISCARDED 가 된다`() {
        grantAccess(workspaceId)
        val keep = clip(1L, 1)
        val drop = clip(2L, 2)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_HOOK_SELECTION)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(keep, drop)
        every { clipHookRepository.findByClipIds(any()) } returns listOf(
            ClipHook(id = 11L, clipId = 1L, variant = HookVariant.A, text = "A안 문구"),
            ClipHook(id = 12L, clipId = 1L, variant = HookVariant.B, text = "B안 문구"),
        )
        every { clipPublicationRepository.findByClipIds(any()) } returns emptyList()
        every { clipHookRepository.clearSelection(any()) } just runs
        every { clipHookRepository.markSelected(any(), any(), any()) } answers {
            ClipHook(id = 99L, clipId = firstArg(), variant = secondArg(), text = thirdArg(), selected = true)
        }
        val updated = mutableListOf<ShortsClip>()
        every { shortsClipRepository.update(capture(updated)) } answers { firstArg() }
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        every { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) } just runs

        useCase.selectHooks(
            userId, workspaceId, runId,
            HookSelectionRequest(
                selections = listOf(HookSelection(clipId = 1L, variant = HookVariant.B)),
                discardClipIds = listOf(2L),
            ),
        )

        assertEquals(ClipStatus.HOOK_SELECTED, updated.first { it.id == 1L }.status)
        assertEquals(ClipStatus.DISCARDED, updated.first { it.id == 2L }.status)
    }

    @Test
    fun `후킹 선택 후 TEMPLATE 부터 이어 달린다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_HOOK_SELECTION)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))
        every { clipHookRepository.findByClipIds(any()) } returns emptyList()
        every { clipPublicationRepository.findByClipIds(any()) } returns emptyList()
        every { clipHookRepository.clearSelection(any()) } just runs
        every { clipHookRepository.markSelected(any(), any(), any()) } answers {
            ClipHook(id = 99L, clipId = firstArg(), variant = secondArg(), text = thirdArg(), selected = true)
        }
        every { shortsClipRepository.update(any()) } answers { firstArg() }
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        // 후킹 후보가 없어도 직접 입력(customText)이면 선택할 수 있다
        useCase.selectHooks(
            userId, workspaceId, runId,
            HookSelectionRequest(
                selections = listOf(
                    HookSelection(clipId = 1L, variant = HookVariant.CUSTOM, customText = "직접 쓴 후킹"),
                ),
            ),
        )

        assertEquals(PipelineStage.TEMPLATE, event.captured.fromStage)
    }

    // ---- 예약 확정 가드 ----

    @Test
    fun `예약 대기 상태가 아니면 예약할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_HOOK_SELECTION)

        val ex = assertFailsWith<BusinessException> {
            useCase.confirmSchedule(
                userId, workspaceId, runId,
                ScheduleConfirmRequest(startAt = Instant.parse("2026-08-13T07:00:00Z"), intervalHours = 24),
            )
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `예약 간격이 0 이하이면 거부한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)

        val ex = assertFailsWith<BusinessException> {
            useCase.confirmSchedule(
                userId, workspaceId, runId,
                ScheduleConfirmRequest(startAt = Instant.parse("2026-08-13T07:00:00Z"), intervalHours = 0),
            )
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `예약을 확정하면 SCHEDULE 단계 이벤트에 파라미터가 실린다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "내 롱폼")
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        every { channelRepository.findById(77L) } returns activeChannel()

        val startAt = Instant.parse("2026-08-13T07:00:00Z")
        useCase.confirmSchedule(
            userId, workspaceId, runId,
            ScheduleConfirmRequest(startAt = startAt, intervalHours = 24, platforms = listOf("YOUTUBE#77")),
        )

        assertEquals(PipelineStage.SCHEDULE, event.captured.fromStage)
        assertEquals(startAt, event.captured.scheduleStartAt)
        assertEquals(24, event.captured.scheduleIntervalHours)
        // 정확한 계정 지정이 그대로 보존돼야 게시 결과를 계정별로 대사할 수 있다.
        assertEquals(listOf("YOUTUBE#77"), event.captured.platforms)
    }

    // ---- 예약 대상 검증 (서버 직접 호출 차단) ----

    /**
     * 화면은 연결된 채널만 보여주도록 고쳤지만 API 를 직접 부르면 그 필터를 우회한다.
     * 확정 전에 막지 않으면 200 을 돌려준 뒤 비동기 게시 단계에서야 실패하고, 사용자는
     * 성공 응답을 본 다음에 실패한 실행을 본다.
     */
    private fun assertScheduleRejected(platforms: List<String>, expectedCode: String) {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)

        val ex = assertFailsWith<BusinessException> {
            useCase.confirmSchedule(
                userId, workspaceId, runId,
                ScheduleConfirmRequest(
                    startAt = Instant.parse("2026-08-13T07:00:00Z"),
                    intervalHours = 24,
                    platforms = platforms,
                ),
            )
        }

        assertEquals(expectedCode, ex.code)
        // 상태를 바꾸지도, 파이프라인을 깨우지도 않는다.
        verify(exactly = 0) { pipelineRunRepository.update(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsPipelineEvent>()) }
    }

    /* 플랫폼 이름만 오면 게시 단계가 채널을 임의로 고른다 — 어디에 올라갈지 알 수 없다. */
    @Test
    fun `계정 없는 플랫폼 이름만으로는 예약할 수 없다`() {
        assertScheduleRejected(listOf("YOUTUBE"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    @Test
    fun `형식이 깨진 게시 대상은 거부한다`() {
        assertScheduleRejected(listOf("YOUTUBE#not-a-number"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    @Test
    fun `알 수 없는 플랫폼은 거부한다`() {
        assertScheduleRejected(listOf("MYSPACE#77"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    /* 없는 채널과 남의 채널을 같은 문구로 거절한다 — 나누면 존재 여부가 샌다. */
    @Test
    fun `존재하지 않는 채널은 거부한다`() {
        every { channelRepository.findById(77L) } returns null
        assertScheduleRejected(listOf("YOUTUBE#77"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    @Test
    fun `남의 채널로는 예약할 수 없다`() {
        every { channelRepository.findById(77L) } returns activeChannel(userId = userId + 1)
        assertScheduleRejected(listOf("YOUTUBE#77"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    @Test
    fun `키의 플랫폼과 채널의 플랫폼이 다르면 거부한다`() {
        every { channelRepository.findById(77L) } returns activeChannel(platform = Platform.TIKTOK)
        assertScheduleRejected(listOf("YOUTUBE#77"), "SHORTS_PUBLISH_TARGET_INVALID")
    }

    @Test
    fun `비활성 채널은 거부한다`() {
        every { channelRepository.findById(77L) } returns activeChannel(status = ChannelStatus.EXPIRED)
        assertScheduleRejected(listOf("YOUTUBE#77"), "SHORTS_PUBLISH_TARGET_UNAVAILABLE")
    }

    @Test
    fun `토큰이 만료된 채널은 거부한다`() {
        every { channelRepository.findById(77L) } returns
            activeChannel(tokenExpiresAt = LocalDateTime.now().minusMinutes(1))
        assertScheduleRejected(listOf("YOUTUBE#77"), "SHORTS_PUBLISH_TARGET_UNAVAILABLE")
    }

    /* 하나라도 못 쓰면 전부 막는다. 일부만 통과시키면 부분 성공이 조용히 생긴다. */
    @Test
    fun `대상 중 하나라도 못 쓰면 아무것도 예약하지 않는다`() {
        every { channelRepository.findById(77L) } returns activeChannel()
        every { channelRepository.findById(88L) } returns activeChannel(id = 88L, status = ChannelStatus.REVOKED)

        assertScheduleRejected(listOf("YOUTUBE#77", "YOUTUBE#88"), "SHORTS_PUBLISH_TARGET_UNAVAILABLE")
    }

    /*
     * 빈 목록은 ScheduleStageExecutor 가 "게시 없이 예약 시각만 확정"으로 정의한 유효한
     * 사용이다. 대상 검증이 이것까지 막으면 기존 기능이 사라진다.
     */
    @Test
    fun `게시 대상이 비어 있으면 예약 시각만 확정한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "내 롱폼")
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        useCase.confirmSchedule(
            userId, workspaceId, runId,
            ScheduleConfirmRequest(startAt = Instant.parse("2026-08-13T07:00:00Z"), intervalHours = 24),
        )

        assertEquals(emptyList(), event.captured.platforms)
        verify(exactly = 0) { channelRepository.findById(any()) }
    }

    /* 여러 계정을 정확히 지정한 정상 경로. 순서와 값이 그대로 실려야 한다. */
    @Test
    fun `같은 플랫폼의 여러 활성 계정을 각각 예약할 수 있다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { pipelineRunRepository.update(any()) } answers { firstArg() }
        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "내 롱폼")
        every { channelRepository.findById(77L) } returns activeChannel()
        every { channelRepository.findById(88L) } returns activeChannel(id = 88L)
        val event = slot<ShortsPipelineEvent>()
        every { eventPublisher.publishEvent(capture(event)) } just runs

        useCase.confirmSchedule(
            userId, workspaceId, runId,
            ScheduleConfirmRequest(
                startAt = Instant.parse("2026-08-13T07:00:00Z"),
                intervalHours = 24,
                platforms = listOf("YOUTUBE#77", "YOUTUBE#88"),
            ),
        )

        assertEquals(listOf("YOUTUBE#77", "YOUTUBE#88"), event.captured.platforms)
    }

    // ---- 렌더 완성 영상 연결 ----

    @Test
    fun `렌더 영상을 연결하면 클립이 RENDERED 가 된다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1)
        every { videoRepository.findById(900L) } returns
            Video(id = 900L, userId = userId, title = "clip-1.mp4")
        val updated = slot<ShortsClip>()
        every { shortsClipRepository.update(capture(updated)) } answers { firstArg() }
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns emptyList()
        every { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) } just runs

        val response = useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L)

        assertEquals(900L, updated.captured.renderedVideoId)
        assertEquals(ClipStatus.RENDERED, updated.captured.status)
        assertEquals("RENDERED", response.status)
        // 응답에도 실려야 화면이 새로고침 뒤에 결과물 유무를 알 수 있다.
        assertEquals(900L, response.renderedVideoId)
    }

    /*
     * 새로고침 뒤 화면이 의지할 수 있는 유일한 근거다. 렌더 job 상태는 브라우저 세션에만
     * 있어 사라지지만, 이 값은 서버가 매번 돌려주므로 결과물 유무를 다시 그릴 수 있다.
     */
    @Test
    fun `상세 응답의 클립에 연결된 완성 영상 id 가 실린다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(
            clip(1L, 1, ClipStatus.RENDERED, renderedVideoId = 900L),
            clip(2L, 2, ClipStatus.RENDER_READY),
        )
        every { clipHookRepository.findByClipIds(listOf(1L, 2L)) } returns emptyList()
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { clipPublicationRepository.findByClipIds(listOf(1L, 2L)) } returns emptyList()

        val detail = useCase.getRunDetail(userId, workspaceId, runId)

        assertEquals(900L, detail.clips.first { it.id == 1L }.renderedVideoId)
        // 아직 연결되지 않은 클립은 null 이어야 한다 — 있는 것처럼 보이면 안 된다.
        assertEquals(null, detail.clips.first { it.id == 2L }.renderedVideoId)
    }

    // ---- 대상별 게시 결과 노출 ----

    /**
     * 클립 상태(SCHEDULED)는 **대상 하나라도** 성공하면 붙는다. 그래서 이 목록 없이는
     * 3개 중 1개만 성공한 클립과 전부 성공한 클립을 구분할 수 없다.
     */
    @Test
    fun `상세 응답에 클립별 대상 게시 결과가 실린다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.PARTIALLY_COMPLETED)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(
            clip(1L, 1, ClipStatus.SCHEDULED, renderedVideoId = 900L),
            clip(2L, 2, ClipStatus.RENDER_READY),
        )
        every { clipHookRepository.findByClipIds(listOf(1L, 2L)) } returns emptyList()
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { channelRepository.findByUserId(userId) } returns listOf(
            activeChannel(id = 77L),
            activeChannel(id = 88L, platform = Platform.TIKTOK),
        )
        every { clipPublicationRepository.findByClipIds(listOf(1L, 2L)) } returns listOf(
            publication(1L, "YOUTUBE#77"),
            publication(1L, "TIKTOK#88", ClipPublicationStatus.FAILED, "채널 인증이 만료되었습니다"),
        )

        val detail = useCase.getRunDetail(userId, workspaceId, runId)

        val first = detail.clips.first { it.id == 1L }
        assertEquals(2, first.publications.size)
        assertEquals("YOUTUBE#77", first.publications[0].platform)
        assertEquals("채널 77", first.publications[0].channelName)
        assertEquals("SCHEDULED", first.publications[0].status)
        assertEquals(null, first.publications[0].errorMessage)
        assertEquals("TIKTOK#88", first.publications[1].platform)
        assertEquals("채널 88", first.publications[1].channelName)
        assertEquals("FAILED", first.publications[1].status)
        // 실패 원인이 보여야 사용자가 무엇을 고칠지 안다.
        assertEquals("채널 인증이 만료되었습니다", first.publications[1].errorMessage)
        assertEquals(Instant.parse("2026-08-14T07:00:00Z"), first.publications[0].scheduledAt)
    }

    /* 게시를 요청한 적이 없는 클립은 빈 배열이다 — null 이 아니라 "대상 없음"이다. */
    @Test
    fun `게시 결과가 없는 클립은 빈 배열을 돌려준다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1, ClipStatus.RENDER_READY))
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns emptyList()
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { clipPublicationRepository.findByClipIds(listOf(1L)) } returns emptyList()

        val detail = useCase.getRunDetail(userId, workspaceId, runId)

        assertEquals(emptyList(), detail.clips.first().publications)
    }

    /*
     * 클립마다 조회하면 클립 수에 비례해 질의가 는다. 실행 하나에 클립이 여러 개인 것이
     * 정상이라 그 비용이 매번 발생한다.
     */
    @Test
    fun `게시 결과는 클립 수와 무관하게 한 번만 조회한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.PARTIALLY_COMPLETED)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(
            clip(1L, 1, ClipStatus.SCHEDULED),
            clip(2L, 2, ClipStatus.SCHEDULED),
            clip(3L, 3, ClipStatus.SCHEDULED),
        )
        every { clipHookRepository.findByClipIds(any()) } returns emptyList()
        every { clipPublicationRepository.findByClipIds(any()) } returns emptyList()
        every { runStageRepository.findByRunId(runId) } returns emptyList()
        every { videoRepository.findById(videoId) } returns sourceVideo()
        every { clipPublicationRepository.findByClipIds(listOf(1L, 2L, 3L)) } returns emptyList()

        useCase.getRunDetail(userId, workspaceId, runId)

        verify(exactly = 1) { clipPublicationRepository.findByClipIds(listOf(1L, 2L, 3L)) }
        verify(exactly = 0) { clipPublicationRepository.findByClipIdAndPlatform(any(), any()) }
    }

    @Test
    fun `남의 영상은 클립에 연결할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1)
        every { videoRepository.findById(900L) } returns
            Video(id = 900L, userId = 999L, title = "남의 영상")

        val ex = assertFailsWith<BusinessException> {
            useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L)
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    @Test
    fun `다른 실행의 클립에는 영상을 연결할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1).copy(runId = 999L)

        val ex = assertFailsWith<BusinessException> {
            useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L)
        }
        assertEquals("SHORTS_CLIP_NOT_FOUND", ex.code)
    }

    @Test
    fun `제외된 클립에는 영상을 연결할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1, ClipStatus.DISCARDED)

        val ex = assertFailsWith<BusinessException> {
            useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L)
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    /*
     * 보완 경로도 서버 렌더와 **같은** 사건을 낸다. 사건의 정의가 "서버가 렌더했다"가
     * 아니라 "접근 가능한 완성 영상이 연결됐다"이기 때문이다. 이 경로를 빼면 서버 렌더를
     * 못 쓰는 환경의 고객이 지표에서 통째로 사라진다.
     */
    @Test
    fun `외부 완성본 연결도 클립 가용 사건을 낸다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1)
        every { videoRepository.findById(900L) } returns
            Video(id = 900L, userId = userId, title = "clip-1.mp4")
        every { shortsClipRepository.update(any()) } answers { firstArg() }
        every { clipHookRepository.findByClipIds(listOf(1L)) } returns emptyList()
        every { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) } just runs

        useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L)

        verify(exactly = 1) {
            eventPublisher.publishEvent(
                ShortsClipAvailableEvent(userId = userId, runId = runId, clipId = 1L),
            )
        }
    }

    /*
     * 거절된 연결은 고객이 받아갈 것을 만들지 않는다. 네 가지 거절 사유 모두에서
     * 사건이 나가면 안 된다 — 하나라도 새면 도달하지 않은 첫 가치가 도달로 세어진다.
     */
    @Test
    fun `연결이 거절되면 클립 가용 사건을 내지 않는다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)

        // 남의 영상
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1)
        every { videoRepository.findById(900L) } returns Video(id = 900L, userId = 999L, title = "남의 영상")
        assertFailsWith<BusinessException> { useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L) }

        // 다른 실행의 클립
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1).copy(runId = 999L)
        assertFailsWith<BusinessException> { useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L) }

        // 제외된 클립
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1, ClipStatus.DISCARDED)
        assertFailsWith<BusinessException> { useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L) }

        // 없는 영상
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1)
        every { videoRepository.findById(900L) } returns null
        assertFailsWith<BusinessException> { useCase.attachRenderedVideo(userId, workspaceId, runId, 1L, 900L) }

        verify(exactly = 0) { eventPublisher.publishEvent(any<ShortsClipAvailableEvent>()) }
    }

    // ---- 렌더 산출물 ----

    @Test
    fun `렌더 스펙이 없는 클립은 다운로드할 수 없다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns clip(1L, 1) // renderSpec = null

        val ex = assertFailsWith<BusinessException> {
            useCase.getRenderSpec(userId, workspaceId, runId, 1L)
        }
        assertEquals("SHORTS_CLIP_NOT_FOUND", ex.code)
    }

    @Test
    fun `다른 실행의 클립은 렌더 스펙을 내주지 않는다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findById(1L) } returns
            clip(1L, 1).copy(runId = 999L, renderSpec = "{}")

        val ex = assertFailsWith<BusinessException> {
            useCase.getRenderSpec(userId, workspaceId, runId, 1L)
        }
        assertEquals("SHORTS_CLIP_NOT_FOUND", ex.code)
    }

    @Test
    fun `내려줄 렌더 산출물이 없으면 번들 다운로드를 거부한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(clip(1L, 1))

        val ex = assertFailsWith<BusinessException> {
            useCase.getRenderBundle(userId, workspaceId, runId)
        }
        assertEquals("SHORTS_CLIP_NOT_FOUND", ex.code)
    }

    // ---- 번들의 원본 URL 재서명 ----

    /**
     * 번들 테스트는 **진짜** 스펙 빌더를 쓴다.
     *
     * 빌더를 목으로 두면 "새 URL 이 담긴 spec 을 넘겼다"까지만 보이고, 정작 사용자가 받는
     * zip 안의 render-spec.json 과 render.sh 에 그 URL 이 실제로 들어갔는지는 증명되지 않는다.
     */
    private fun bundleUseCase() = ShortsPipelineUseCase(
        pipelineRunRepository = pipelineRunRepository,
        runStageRepository = runStageRepository,
        shortsClipRepository = shortsClipRepository,
        clipHookRepository = clipHookRepository,
        shortsTemplateRepository = shortsTemplateRepository,
        videoRepository = videoRepository,
        workspaceRepository = workspaceRepository,
        renderSpecBuilder = ShortsRenderSpecBuilder(),
        eventPublisher = eventPublisher,
        maxSourceBytes = MAX_SOURCE_BYTES,
        maxSourceDurationMs = MAX_SOURCE_DURATION_MS,
        audioPort = audioPort,
        storageService = storageService,
        pilotEventRepository = pilotEventRepository,
        creditService = creditService,
        activityLogUseCase = activityLogUseCase,
        channelRepository = channelRepository,
        clipPublicationRepository = clipPublicationRepository,
    )

    private fun storedSpecJson(videoId: Long = 99L, url: String = STALE_URL) = """
        {"clipSeq":1,
         "source":{"videoId":$videoId,"fileUrl":"$url"},
         "cut":{"startMs":0,"endMs":15000},
         "reframe":{"targetWidth":1080,"targetHeight":1920},
         "hook":{"text":"훅","position":"TOP"},
         "subtitles":[],
         "template":{"id":null,"backgroundStyle":null,"captionFontFamily":null}}
    """.trimIndent()

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes().decodeToString()
            }
        }
        return out
    }

    private fun arrangeBundle(specJson: String): ShortsClip {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.AWAITING_SCHEDULE)
        val stored = clip(1L, 1).copy(renderSpec = specJson)
        every { shortsClipRepository.findByRunId(runId) } returns listOf(stored)
        // 실행에 템플릿이 없으면 워크스페이스 기본 템플릿을 찾는다.
        every { shortsTemplateRepository.findByWorkspace(workspaceId) } returns emptyList()
        return stored
    }

    /*
     * 번들은 사용자가 자기 PC 에서 돌리는 물건이다. 다운로드 시점에 살아 있는 주소가 아니면
     * 풀어서 render.sh 를 돌린 뒤에야 403 을 보게 된다.
     */
    @Test
    fun `번들의 render-spec 과 render_sh 양쪽에 새 URL 이 들어간다`() {
        val stored = arrangeBundle(storedSpecJson())
        every { storageService.getFileUrl(99L, STALE_URL) } returns FRESH_URL

        val entries = unzip(bundleUseCase().getRenderBundle(userId, workspaceId, runId))

        val specEntry = entries.getValue("clip-1/render-spec.json")
        val scriptEntry = entries.getValue("clip-1/render.sh")
        assertTrue(specEntry.contains(FRESH_URL), "render-spec.json 에 새 URL 이 없다")
        assertFalse(specEntry.contains(STALE_URL), "render-spec.json 에 오래된 URL 이 남았다")
        assertTrue(scriptEntry.contains(FRESH_URL), "render.sh 에 새 URL 이 없다")
        assertFalse(scriptEntry.contains(STALE_URL), "render.sh 에 오래된 URL 이 남았다")

        // 저장 URL 은 legacy key 해석용 입력으로만 넘어간다.
        verify(exactly = 1) { storageService.getFileUrl(99L, STALE_URL) }

        // DB 의 스펙은 그대로다. 서명은 또 만료되므로 저장해봐야 같은 문제가 반복된다.
        assertTrue(stored.renderSpec!!.contains(STALE_URL), "DB 스펙 문자열이 바뀌었다")
        verify(exactly = 0) { shortsClipRepository.update(any()) }
    }

    @Test
    fun `번들 재서명이 실패하면 오래된 URL 산출물을 내려주지 않는다`() {
        arrangeBundle(storedSpecJson())
        every { storageService.getFileUrl(99L, STALE_URL) } throws
            IllegalStateException("업로드된 파일을 찾을 수 없습니다")

        val ex = assertFailsWith<BusinessException> {
            bundleUseCase().getRenderBundle(userId, workspaceId, runId)
        }

        assertEquals("SHORTS_SOURCE_URL_REFRESH_FAILED", ex.code)
        // 사용자에게 보이는 문구에 URL·서명이 섞이면 presigned URL 이 그대로 새어나간다.
        assertFalse(ex.message.orEmpty().contains("http"))
        assertFalse(ex.message.orEmpty().contains("sig="))
    }

    @Test
    fun `번들 스펙에 videoId 가 없으면 저장 URL 로 번들을 만들지 않는다`() {
        arrangeBundle(storedSpecJson(videoId = 0L))

        val ex = assertFailsWith<BusinessException> {
            bundleUseCase().getRenderBundle(userId, workspaceId, runId)
        }

        assertEquals("SHORTS_SOURCE_URL_REFRESH_FAILED", ex.code)
        verify(exactly = 0) { storageService.getFileUrl(any(), any()) }
    }

    // ---- 삭제 ----

    @Test
    fun `실행 중 삭제하면 CANCELLED 로 표시한 뒤 삭제한다`() {
        grantAccess(workspaceId)
        every { pipelineRunRepository.findById(runId) } returns run(PipelineRunStatus.RUNNING)
        val updated = slot<PipelineRun>()
        every { pipelineRunRepository.update(capture(updated)) } answers { firstArg() }
        every { pipelineRunRepository.delete(runId) } returns true

        useCase.deleteRun(userId, workspaceId, runId)

        assertEquals(PipelineRunStatus.CANCELLED, updated.captured.status)
        verify { pipelineRunRepository.delete(runId) }
    }

    companion object {
        /** 실제 기본값(2GiB / 3시간)과 무관하게 테스트가 경계를 스스로 정한다. */
        private const val MAX_SOURCE_BYTES = 1_000_000L
        private const val MAX_SOURCE_DURATION_MS = 3_600_000L

        /**
         * 대부분의 테스트가 쓰는 원본 길이(1분). 과금 창 이하라 전사 크레딧이 종전 정액과
         * 같다 — 즉 이 값을 쓰는 테스트들은 이번 변경 이전과 같은 금액을 본다.
         */
        private const val SHORT_SOURCE_DURATION_MS = 60_000L
        private const val SOURCE_URL = "https://storage.test/videos/55/source.mp4?sig=abc"

        /** 스펙에 굳은 URL. 실제로는 RENDER_SPEC 단계 서명이라 7일이면 만료된다. */
        private const val STALE_URL = "https://storage.test/videos/99/src.mp4?sig=stale"
        private const val FRESH_URL = "https://storage.test/videos/99/src.mp4?sig=fresh"
    }
}
