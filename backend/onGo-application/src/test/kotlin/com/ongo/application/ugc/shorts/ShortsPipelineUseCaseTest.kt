package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.CreatePipelineRunRequest
import com.ongo.application.ugc.shorts.dto.HookSelection
import com.ongo.application.ugc.shorts.dto.HookSelectionRequest
import com.ongo.application.ugc.shorts.dto.ScheduleConfirmRequest
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
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @InjectMockKs
    lateinit var useCase: ShortsPipelineUseCase

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

    private fun clip(id: Long, seq: Int, status: ClipStatus = ClipStatus.DRAFT) = ShortsClip(
        id = id,
        runId = runId,
        seq = seq,
        startMs = 0,
        endMs = 45_000,
        status = status,
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

        useCase.rerunStage(userId, workspaceId, runId, "SEGMENT")

        verify { shortsClipRepository.deleteByRunId(runId) }
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

        val startAt = Instant.parse("2026-08-13T07:00:00Z")
        useCase.confirmSchedule(
            userId, workspaceId, runId,
            ScheduleConfirmRequest(startAt = startAt, intervalHours = 24, platforms = listOf("YOUTUBE")),
        )

        assertEquals(PipelineStage.SCHEDULE, event.captured.fromStage)
        assertEquals(startAt, event.captured.scheduleStartAt)
        assertEquals(24, event.captured.scheduleIntervalHours)
        assertEquals(listOf("YOUTUBE"), event.captured.platforms)
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
}
