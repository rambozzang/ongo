package com.ongo.application.ugc.shorts

import com.ongo.application.common.StorageService
import com.ongo.application.ugc.shorts.dto.ShortsTemplateRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ShortsTemplateUseCaseTest {

    @MockK
    lateinit var shortsTemplateRepository: ShortsTemplateRepository

    @MockK
    lateinit var workspaceRepository: WorkspaceRepository

    @MockK
    lateinit var storageService: StorageService

    @InjectMockKs
    lateinit var useCase: ShortsTemplateUseCase

    private val userId = 1L
    private val workspaceId = 10L
    private val otherWorkspaceId = 99L

    private fun grantAccess(vararg accessibleIds: Long) {
        every { workspaceRepository.findAccessibleByUserId(userId) } returns
            accessibleIds.map { Workspace(id = it, ownerId = userId, name = "WS $it", slug = "ws-$it") }
    }

    private fun template(id: Long = 100L, wsId: Long = workspaceId, isDefault: Boolean = false) =
        ShortsTemplate(
            id = id,
            workspaceId = wsId,
            name = "기본 템플릿",
            isDefault = isDefault,
            createdBy = userId,
        )

    // ---- 생성 ----

    @Test
    fun `create with isDefault true clears previous default first`() {
        grantAccess(workspaceId)
        justRun { shortsTemplateRepository.clearDefault(workspaceId) }
        val saveSlot = slot<ShortsTemplate>()
        every { shortsTemplateRepository.save(capture(saveSlot)) } answers { saveSlot.captured.copy(id = 100L) }

        val result = useCase.createTemplate(
            userId, workspaceId,
            ShortsTemplateRequest(name = "새 템플릿", isDefault = true),
        )

        verify(exactly = 1) { shortsTemplateRepository.clearDefault(workspaceId) }
        assertTrue(saveSlot.captured.isDefault)
        assertEquals(workspaceId, saveSlot.captured.workspaceId)
        assertEquals(userId, saveSlot.captured.createdBy)
        assertTrue(result.isDefault)
    }

    @Test
    fun `create without isDefault does not touch existing default`() {
        grantAccess(workspaceId)
        val saveSlot = slot<ShortsTemplate>()
        every { shortsTemplateRepository.save(capture(saveSlot)) } answers { saveSlot.captured.copy(id = 100L) }

        useCase.createTemplate(userId, workspaceId, ShortsTemplateRequest(name = "새 템플릿"))

        verify(exactly = 0) { shortsTemplateRepository.clearDefault(any()) }
    }

    // ---- 수정 ----

    @Test
    fun `update setting isDefault true clears previous default first`() {
        grantAccess(workspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template()
        justRun { shortsTemplateRepository.clearDefault(workspaceId) }
        val updateSlot = slot<ShortsTemplate>()
        every { shortsTemplateRepository.update(capture(updateSlot)) } answers { updateSlot.captured }

        val result = useCase.updateTemplate(
            userId, workspaceId, 100L,
            ShortsTemplateRequest(name = "수정된 템플릿", isDefault = true),
        )

        verify(exactly = 1) { shortsTemplateRepository.clearDefault(workspaceId) }
        assertEquals("수정된 템플릿", updateSlot.captured.name)
        assertTrue(result.isDefault)
    }

    // ---- 조회/삭제 ----

    @Test
    fun `get returns template of the workspace`() {
        grantAccess(workspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template()

        val result = useCase.getTemplate(userId, workspaceId, 100L)

        assertEquals(100L, result.id)
        assertEquals("9:16", result.aspectRatio)
        assertEquals(1080, result.width)
        assertEquals(1920, result.height)
        assertEquals("BLACK_BARS", result.backgroundStyle)
        assertEquals("TOP", result.hookPosition)
        assertEquals("BOTTOM", result.captionPosition)
    }

    @Test
    fun `get missing template throws SHORTS_TEMPLATE_NOT_FOUND`() {
        grantAccess(workspaceId)
        every { shortsTemplateRepository.findById(100L) } returns null

        val ex = assertFailsWith<BusinessException> {
            useCase.getTemplate(userId, workspaceId, 100L)
        }
        assertEquals("SHORTS_TEMPLATE_NOT_FOUND", ex.code)
    }

    @Test
    fun `delete removes template of the workspace`() {
        grantAccess(workspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template()
        every { shortsTemplateRepository.delete(100L) } returns true

        useCase.deleteTemplate(userId, workspaceId, 100L)

        verify(exactly = 1) { shortsTemplateRepository.delete(100L) }
    }

    // ---- 워크스페이스 격리 ----

    @Test
    fun `get template of another workspace throws ACCESS_DENIED`() {
        grantAccess(workspaceId, otherWorkspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template(wsId = otherWorkspaceId)

        val ex = assertFailsWith<BusinessException> {
            useCase.getTemplate(userId, workspaceId, 100L)
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    @Test
    fun `update template of another workspace throws ACCESS_DENIED`() {
        grantAccess(workspaceId, otherWorkspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template(wsId = otherWorkspaceId)

        val ex = assertFailsWith<BusinessException> {
            useCase.updateTemplate(userId, workspaceId, 100L, ShortsTemplateRequest(name = "x"))
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    @Test
    fun `delete template of another workspace throws ACCESS_DENIED`() {
        grantAccess(workspaceId, otherWorkspaceId)
        every { shortsTemplateRepository.findById(100L) } returns template(wsId = otherWorkspaceId)

        val ex = assertFailsWith<BusinessException> {
            useCase.deleteTemplate(userId, workspaceId, 100L)
        }
        assertEquals("ACCESS_DENIED", ex.code)
    }

    @Test
    fun `list is blocked with 404 when user has no access to the workspace`() {
        grantAccess()
        assertFailsWith<NotFoundException> {
            useCase.listTemplates(userId, workspaceId)
        }
    }
}
