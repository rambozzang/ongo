package com.ongo.application.ugc.shorts

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.ugc.shorts.VideoRenderer
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class ShortsRenderUseCaseTest {

    @MockK lateinit var clipRepository: ShortsClipRepository
    @MockK lateinit var runRepository: PipelineRunRepository
    @MockK lateinit var templateRepository: ShortsTemplateRepository
    @MockK lateinit var renderer: VideoRenderer
    @MockK lateinit var stateService: ShortsRenderJobStateService
    @MockK lateinit var renderJobRepository: ShortsRenderJobRepository
    @MockK lateinit var resourceManager: ShortsRenderResourceManager
    @MockK lateinit var fileStoragePort: com.ongo.application.common.FileStoragePort
    @MockK lateinit var videoRepository: VideoRepository
    @MockK lateinit var renderSpecBuilder: ShortsRenderSpecBuilder
    @MockK lateinit var workspaceRepository: WorkspaceRepository

    @InjectMockKs lateinit var useCase: ShortsRenderUseCase

    @Test
    fun `렌더 요청은 워크스페이스 접근 권한이 없으면 거부한다`() {
        every { workspaceRepository.findAccessibleByUserId(7L) } returns emptyList()

        assertFailsWith<NotFoundException> {
            useCase.requestRender(userId = 7L, workspaceId = 11L, runId = 31L, clipId = 41L)
        }

        verify(exactly = 0) { runRepository.findById(any()) }
        verify(exactly = 0) { stateService.enqueue(any(), any()) }
    }

    @Test
    fun `렌더 상태 조회도 워크스페이스 접근 권한이 없으면 거부한다`() {
        every { workspaceRepository.findAccessibleByUserId(7L) } returns emptyList()

        assertFailsWith<NotFoundException> {
            useCase.status(userId = 7L, workspaceId = 11L, runId = 31L, clipId = 41L)
        }

        verify(exactly = 0) { runRepository.findById(any()) }
        verify(exactly = 0) { renderJobRepository.findByRunAndClip(any(), any()) }
    }
}
