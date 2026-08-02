package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.UpdateShortsPromptRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPrompt
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import com.ongo.domain.ugc.shorts.ShortsPromptRevision
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ShortsPromptUseCaseTest {

    @MockK
    lateinit var shortsPromptRepository: ShortsPromptRepository

    @MockK
    lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs
    lateinit var useCase: ShortsPromptUseCase

    private val userId = 1L
    private val workspaceId = 10L

    private fun grantAccess(vararg accessibleIds: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            accessibleIds.map { Workspace(id = it, ownerId = userId, name = "WS $it", slug = "ws-$it") }
    }

    private fun defaultPrompt(stage: PipelineStage) = ShortsPrompt(
        id = 1000L + stage.sortOrder,
        workspaceId = null,
        stage = stage,
        name = stage.displayName,
        systemPrompt = if (stage == PipelineStage.SEGMENT) "기본 시스템 프롬프트" else null,
        userPrompt = "기본 사용자 프롬프트 ${stage.name}",
        executable = stage.aiExecutable,
        revision = 1,
    )

    private fun overridePrompt(stage: PipelineStage, revision: Int = 2) = ShortsPrompt(
        id = 2000L + stage.sortOrder,
        workspaceId = workspaceId,
        stage = stage,
        name = stage.displayName,
        systemPrompt = "커스텀 시스템 프롬프트",
        userPrompt = "커스텀 사용자 프롬프트",
        executable = stage.aiExecutable,
        revision = revision,
        createdBy = userId,
    )

    // ---- 조회 ----

    @Test
    fun `list returns all 9 stages in sortOrder with customized flags`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.findByWorkspace(workspaceId) } returns
            listOf(overridePrompt(PipelineStage.HOOK))
        every { shortsPromptRepository.findDefaultByStage(any()) } answers
            { defaultPrompt(firstArg()) }

        val result = useCase.listPrompts(userId, workspaceId)

        assertEquals(9, result.size)
        assertEquals(PipelineStage.entries.sortedBy { it.sortOrder }.map { it.name }, result.map { it.stage })
        assertTrue(result.single { it.stage == "HOOK" }.customized)
        assertEquals(8, result.count { !it.customized })
    }

    @Test
    fun `list falls back to built-in defaults when DB default is missing`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.findByWorkspace(workspaceId) } returns emptyList()
        every { shortsPromptRepository.findDefaultByStage(any()) } returns null

        val result = useCase.listPrompts(userId, workspaceId)

        assertEquals(9, result.size)
        val segment = result.single { it.stage == "SEGMENT" }
        assertFalse(segment.customized)
        assertEquals(ShortsPromptDefaults.fallback(PipelineStage.SEGMENT).userPrompt, segment.userPrompt)
        assertEquals(ShortsPromptDefaults.fallback(PipelineStage.SEGMENT).userPrompt, segment.defaultUserPrompt)
    }

    @Test
    fun `get returns override content with default prompts for restore preview`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns
            overridePrompt(PipelineStage.SEGMENT)
        every { shortsPromptRepository.findDefaultByStage(PipelineStage.SEGMENT) } returns
            defaultPrompt(PipelineStage.SEGMENT)

        val result = useCase.getPrompt(userId, workspaceId, "SEGMENT")

        assertTrue(result.customized)
        assertEquals("커스텀 사용자 프롬프트", result.userPrompt)
        assertEquals("기본 시스템 프롬프트", result.defaultSystemPrompt)
        assertEquals("기본 사용자 프롬프트 SEGMENT", result.defaultUserPrompt)
    }

    @Test
    fun `get rejects unknown stage with SHORTS_PROMPT_STAGE_INVALID`() {
        grantAccess(workspaceId)
        val ex = assertFailsWith<BusinessException> {
            useCase.getPrompt(userId, workspaceId, "NOPE")
        }
        assertEquals("SHORTS_PROMPT_STAGE_INVALID", ex.code)
    }

    // ---- 편집 (오버라이드 생성/갱신) ----

    @Test
    fun `first edit creates override at next revision and archives default as revision 1`() {
        grantAccess(workspaceId)
        val default = defaultPrompt(PipelineStage.SEGMENT)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns null
        every { shortsPromptRepository.findDefaultByStage(PipelineStage.SEGMENT) } returns default

        val savedSlot = slot<ShortsPrompt>()
        every { shortsPromptRepository.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 55L) }
        val revisionSlot = slot<ShortsPromptRevision>()
        every { shortsPromptRepository.saveRevision(capture(revisionSlot)) } answers { revisionSlot.captured }

        val result = useCase.updatePrompt(
            userId, workspaceId, "SEGMENT",
            UpdateShortsPromptRequest(systemPrompt = "새 시스템", userPrompt = "새 사용자", changeNote = "첫 수정"),
        )

        // 오버라이드는 기본값 개정(1) + 1 = 2로 시작한다
        assertEquals(2, savedSlot.captured.revision)
        assertEquals(workspaceId, savedSlot.captured.workspaceId)
        assertEquals("새 사용자", savedSlot.captured.userPrompt)
        // 변경 전 내용(시스템 기본값)이 개정 1로 남는다
        assertEquals(1, revisionSlot.captured.revision)
        assertEquals(default.userPrompt, revisionSlot.captured.userPrompt)
        assertEquals("첫 수정", revisionSlot.captured.changeNote)
        assertEquals(userId, revisionSlot.captured.changedBy)
        assertTrue(result.customized)
        assertEquals(2, result.revision)
    }

    @Test
    fun `second edit bumps revision and archives previous override content`() {
        grantAccess(workspaceId)
        val existing = overridePrompt(PipelineStage.SEGMENT, revision = 2)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns existing
        every { shortsPromptRepository.findDefaultByStage(PipelineStage.SEGMENT) } returns defaultPrompt(PipelineStage.SEGMENT)

        val updateSlot = slot<ShortsPrompt>()
        every { shortsPromptRepository.update(capture(updateSlot)) } answers { updateSlot.captured }
        val revisionSlot = slot<ShortsPromptRevision>()
        every { shortsPromptRepository.saveRevision(capture(revisionSlot)) } answers { revisionSlot.captured }

        val result = useCase.updatePrompt(
            userId, workspaceId, "SEGMENT",
            UpdateShortsPromptRequest(systemPrompt = "더 새 시스템", userPrompt = "더 새 사용자", changeNote = "두번째 수정"),
        )

        assertEquals(3, updateSlot.captured.revision)
        // 변경 전 내용(기존 오버라이드 개정 2)이 남는다
        assertEquals(2, revisionSlot.captured.revision)
        assertEquals("커스텀 사용자 프롬프트", revisionSlot.captured.userPrompt)
        assertEquals(3, result.revision)
    }

    // ---- 기본값 복원 ----

    @Test
    fun `reset deletes override and returns default with customized false`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.deleteByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns true
        every { shortsPromptRepository.findDefaultByStage(PipelineStage.SEGMENT) } returns defaultPrompt(PipelineStage.SEGMENT)

        val result = useCase.resetPrompt(userId, workspaceId, "SEGMENT")

        assertFalse(result.customized)
        assertEquals("기본 사용자 프롬프트 SEGMENT", result.userPrompt)
    }

    @Test
    fun `reset without override throws SHORTS_PROMPT_NOT_CUSTOMIZED`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.deleteByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns false

        val ex = assertFailsWith<BusinessException> {
            useCase.resetPrompt(userId, workspaceId, "SEGMENT")
        }
        assertEquals("SHORTS_PROMPT_NOT_CUSTOMIZED", ex.code)
    }

    // ---- 롤백 ----

    @Test
    fun `restore creates a new revision from the target revision without deleting history`() {
        grantAccess(workspaceId)
        val existing = overridePrompt(PipelineStage.SEGMENT, revision = 3)
        val target = ShortsPromptRevision(
            id = 91,
            promptId = existing.id,
            revision = 1,
            systemPrompt = "개정1 시스템",
            userPrompt = "개정1 사용자",
            changedBy = userId,
        )
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns existing
        every { shortsPromptRepository.findRevision(existing.id, 1) } returns target
        every { shortsPromptRepository.findDefaultByStage(PipelineStage.SEGMENT) } returns defaultPrompt(PipelineStage.SEGMENT)

        val updateSlot = slot<ShortsPrompt>()
        every { shortsPromptRepository.update(capture(updateSlot)) } answers { updateSlot.captured }
        val revisionSlot = slot<ShortsPromptRevision>()
        every { shortsPromptRepository.saveRevision(capture(revisionSlot)) } answers { revisionSlot.captured }

        val result = useCase.restoreRevision(userId, workspaceId, "SEGMENT", 1)

        // 지정 개정의 내용으로 새 개정(4)이 만들어진다
        assertEquals(4, updateSlot.captured.revision)
        assertEquals("개정1 사용자", updateSlot.captured.userPrompt)
        // 롤백 전 현재 내용(개정 3)이 이력에 남는다
        assertEquals(3, revisionSlot.captured.revision)
        assertEquals("커스텀 사용자 프롬프트", revisionSlot.captured.userPrompt)
        assertEquals(4, result.revision)
        verify(exactly = 1) { shortsPromptRepository.saveRevision(any()) }
    }

    @Test
    fun `restore missing revision throws SHORTS_PROMPT_REVISION_NOT_FOUND`() {
        grantAccess(workspaceId)
        val existing = overridePrompt(PipelineStage.SEGMENT)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns existing
        every { shortsPromptRepository.findRevision(existing.id, 99) } returns null

        val ex = assertFailsWith<BusinessException> {
            useCase.restoreRevision(userId, workspaceId, "SEGMENT", 99)
        }
        assertEquals("SHORTS_PROMPT_REVISION_NOT_FOUND", ex.code)
    }

    @Test
    fun `restore without override throws SHORTS_PROMPT_NOT_CUSTOMIZED`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns null

        val ex = assertFailsWith<BusinessException> {
            useCase.restoreRevision(userId, workspaceId, "SEGMENT", 1)
        }
        assertEquals("SHORTS_PROMPT_NOT_CUSTOMIZED", ex.code)
    }

    // ---- 개정 이력 ----

    @Test
    fun `revisions returns empty list when not customized`() {
        grantAccess(workspaceId)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns null

        val result = useCase.listRevisions(userId, workspaceId, "SEGMENT")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `revisions maps revision history of the override`() {
        grantAccess(workspaceId)
        val existing = overridePrompt(PipelineStage.SEGMENT, revision = 2)
        every { shortsPromptRepository.findByWorkspaceAndStage(workspaceId, PipelineStage.SEGMENT) } returns existing
        every { shortsPromptRepository.findRevisions(existing.id) } returns listOf(
            ShortsPromptRevision(
                id = 91, promptId = existing.id, revision = 1,
                systemPrompt = null, userPrompt = "개정1 사용자", changeNote = "첫 수정", changedBy = userId,
            ),
        )

        val result = useCase.listRevisions(userId, workspaceId, "SEGMENT")

        assertEquals(1, result.size)
        assertEquals(1, result[0].revision)
        assertEquals("개정1 사용자", result[0].userPrompt)
        assertEquals("첫 수정", result[0].changeNote)
        assertEquals(userId, result[0].changedBy)
    }

    // ---- 워크스페이스 격리 ----

    @Test
    fun `list is blocked with 404 when user has no access to the workspace`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.listPrompts(userId, workspaceId)
        }
    }

    @Test
    fun `update is blocked with 404 when user has no access to the workspace`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.updatePrompt(userId, workspaceId, "SEGMENT", UpdateShortsPromptRequest(userPrompt = "x"))
        }
    }

    @Test
    fun `reset is blocked with 404 when user has no access to the workspace`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.resetPrompt(userId, workspaceId, "SEGMENT")
        }
    }
}
