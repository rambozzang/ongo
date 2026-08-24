package com.ongo.application.ugc.shorts

import com.ongo.application.video.StorageService
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.RenderedClip
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.ugc.shorts.VideoRenderer
import com.ongo.domain.workspace.WorkspaceRepository
import com.ongo.domain.workspace.Workspace
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
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
    @MockK lateinit var renderedClipPersister: RenderedClipPersister
    @MockK lateinit var renderSpecBuilder: ShortsRenderSpecBuilder
    @MockK lateinit var workspaceRepository: WorkspaceRepository
    @MockK lateinit var storageService: StorageService
    @MockK lateinit var pilotEventRepository: ShortsPilotEventRepository

    @InjectMockKs lateinit var useCase: ShortsRenderUseCase

    /** 저장된 스펙 URL. 실제로는 RENDER_SPEC 단계에서 굳은 값이고 7일이면 만료된다. */
    private val staleUrl = "https://storage.test/videos/99/src.mp4?sig=stale"
    private val freshUrl = "https://storage.test/videos/99/src.mp4?sig=fresh"

    private fun spec(
        sourceVideoId: Long = 99L,
        sourceFileUrl: String? = staleUrl,
    ) = ShortsRenderSpecBuilder.RenderSpec(
        clipSeq = 1,
        sourceVideoId = sourceVideoId,
        sourceFileUrl = sourceFileUrl,
        startMs = 0,
        endMs = 1_000,
        crop = null,
        hookText = null,
        hookPosition = "TOP",
        subtitles = emptyList(),
        templateId = null,
        backgroundStyle = null,
        captionFontFamily = null,
    )

    /** 렌더가 실제로 돌기 직전까지의 공통 준비. 반환된 latch 는 실패 확정 신호다. */
    private fun arrangeRender(
        runId: Long,
        clipId: Long,
        jobId: String,
        renderSpec: ShortsRenderSpecBuilder.RenderSpec = spec(),
    ): CountDownLatch {
        val workspace = Workspace(id = 5L, ownerId = 7L, name = "ws", slug = "ws")
        every { workspaceRepository.findAccessibleByUserId(7L) } returns listOf(workspace)
        every { runRepository.findById(runId) } returns
            PipelineRun(id = runId, workspaceId = 5L, userId = 7L, sourceVideoId = 99L)
        every { clipRepository.findById(clipId) } returns ShortsClip(
            id = clipId, runId = runId, seq = 1, startMs = 0, endMs = 1_000,
            renderSpec = """{"spec":true}""",
        )
        every { templateRepository.findById(any()) } returns null

        val job = ShortsRenderJob(id = jobId, runId = runId, clipId = clipId)
        every { stateService.enqueue(runId, clipId) } returns job
        every { stateService.claimForExecution(jobId) } returns job
        every { stateService.find(jobId) } returns job
        every { resourceManager.withPermit<Any>(runId, any()) } answers { secondArg<() -> Any>().invoke() }
        every { renderSpecBuilder.parseSpec(any()) } returns renderSpec
        every { renderSpecBuilder.buildAss(any(), any()) } returns "[Script Info]"

        // 실패 경로는 항상 측정 이벤트를 남긴다. 개별 테스트가 필요하면 다시 스텁한다.
        every { pilotEventRepository.save(any()) } answers { firstArg() }

        val finished = CountDownLatch(1)
        every { stateService.markFailed(jobId, any()) } answers {
            finished.countDown()
            job
        }
        return finished
    }

    // ---- 파일럿 측정: 렌더 실패 누적 ----

    /**
     * job 행 하나로는 재시도 이력을 알 수 없다.
     *
     * `shorts_render_jobs` 는 (run_id, clip_id) 유니크라 클립당 한 행이고, 재시도는
     * `enqueue` 가 실패 사유와 시각을 지우고 같은 행을 재사용한다. 세 번 실패하고 성공한
     * 클립과 첫 시도에 성공한 클립이 최종 상태로는 구분되지 않는다.
     */
    @Test
    fun `렌더 재시도 2회 실패가 서로 다른 회차의 이벤트 2건으로 쌓인다`() {
        val runId = 61L
        val clipId = 62L
        val jobId = "job-attempts"

        val events = mutableListOf<ShortsPilotEvent>()

        listOf(1, 2).forEach { attempt ->
            arrangeRender(runId, clipId, jobId)

            /*
             * 대기 기준은 markFailed 가 아니라 **save 완료**다.
             *
             * 운영 코드는 markFailed 로 job 을 확정한 뒤에 이벤트를 기록한다. markFailed 에서
             * latch 를 열면 주 스레드가 먼저 깨어나 다음 시도로 넘어가고, 그 사이 렌더 스레드가
             * events 에 append 하는 중에 주 스레드가 filter 로 순회해 터진다.
             *
             * CountDownLatch 의 countDown → await 은 happens-before 를 만든다. 여기서 열면
             * append 가 끝났다는 것과, 그 결과가 주 스레드에 보인다는 것이 함께 보장된다.
             * 리스트 타입만 바꾸는 것으로는 후자만 얻고 "아직 안 온 이벤트"를 못 본 채
             * 통과할 수 있다.
             */
            val saved = CountDownLatch(1)
            every { pilotEventRepository.save(any()) } answers {
                events += firstArg<ShortsPilotEvent>()
                saved.countDown()
                firstArg()
            }
            // markFailed 가 남긴 job 의 실제 회차를 그대로 쓴다.
            every { stateService.markFailed(jobId, any()) } returns
                ShortsRenderJob(id = jobId, runId = runId, clipId = clipId, attemptCount = attempt)
            every { storageService.getFileUrl(99L, staleUrl) } throws IllegalStateException("재서명 실패")

            useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)

            // 이벤트 기록이 아예 없으면 여기서 시간 초과로 드러난다 — 조용히 0건으로 지나가지 않는다.
            assertTrue(saved.await(10, TimeUnit.SECONDS), "$attempt 회차 실패 이벤트가 기록되지 않았다")
        }

        val failures = events.filter { it.eventType == ShortsPilotEventType.RENDER_ATTEMPT_FAILED }
        assertEquals(2, failures.size, "재시도가 같은 행을 덮어써 이벤트가 누적되지 않았다")
        assertEquals(listOf(1, 2), failures.map { it.attemptNo })
        assertTrue(failures.all { it.actorType == ShortsPilotActorType.SYSTEM })
        assertTrue(failures.all { it.runId == runId })
    }

    /*
     * 이 시점의 job 은 이미 실패로 확정됐다. 측정 기록이 안 됐다고 예외를 올리면
     * 비동기 실행기 밖으로 새어 아무도 못 보는 곳에서 죽는다.
     */
    @Test
    fun `측정 이벤트 기록이 실패해도 job 실패 처리는 그대로 끝난다`() {
        val runId = 71L
        val clipId = 72L
        val jobId = "job-event-fail"
        val finished = arrangeRender(runId, clipId, jobId)

        every { storageService.getFileUrl(99L, staleUrl) } throws IllegalStateException("재서명 실패")
        every { pilotEventRepository.save(any()) } throws IllegalStateException("측정 저장소 장애")

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)

        assertTrue(finished.await(10, TimeUnit.SECONDS), "측정 실패가 job 확정을 막았다")
        verify(exactly = 1) { stateService.markFailed(jobId, any()) }
    }

    /*
     * 클립 여러 개가 병렬로 끝나면 완료 경로를 동시에 지난다. 조건이 SQL 안에 있어야
     * 마지막 완료가 첫 납품 시각을 덮지 않는다.
     */
    @Test
    fun `첫 렌더 완료에서 납품 시각을 원자적 setter 로 기록한다`() {
        val runId = 81L
        val clipId = 82L
        val renderedFile = Files.createTempFile("ongo-shorts-delivered-", ".mp4")
        Files.write(renderedFile, ByteArray(8))
        arrangeRender(runId, clipId, "job-delivered")

        every { storageService.getFileUrl(99L, staleUrl) } returns freshUrl
        every { renderer.render(any()) } returns RenderedClip(path = renderedFile, sizeBytes = 8L)
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage.test/out.mp4"
        every { renderedClipPersister.persist(any(), any(), any(), any(), any(), any()) } returns 1234L

        // 납품 기록이 확정 이후에 오므로 여기서 완료를 기다린다. persist 에서 세면
        // 아직 실행되지 않은 호출을 검증하게 된다.
        val done = CountDownLatch(1)
        every { runRepository.markDeliveredIfAbsent(runId, any()) } answers {
            done.countDown()
            true
        }

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)
        assertTrue(done.await(10, TimeUnit.SECONDS), "비동기 렌더가 시간 안에 끝나지 않았다")

        // read-copy-update 가 아니라 조건부 갱신 포트를 쓴다.
        verify(exactly = 1) { runRepository.markDeliveredIfAbsent(runId, any()) }
        verify(exactly = 0) { runRepository.update(any()) }
        // 성공에는 실패 이벤트를 남기지 않는다.
        verify(exactly = 0) { pilotEventRepository.save(any()) }
    }

    /*
     * 스펙의 URL 은 RENDER_SPEC 단계에서 굳는다. 사용자가 훅 선택이나 예약 확정을 며칠
     * 미루면 렌더를 누르는 시점에는 이미 죽은 주소다. 그러면 ffmpeg 이 403 을 받고
     * 사용자에게는 "렌더 실패"만 남는다.
     */
    @Test
    fun `렌더 직전에 원본 URL 을 다시 발급해 renderer 에 넘긴다`() {
        val runId = 31L
        val clipId = 32L
        val renderedFile = Files.createTempFile("ongo-shorts-resign-", ".mp4")
        Files.write(renderedFile, ByteArray(8))
        arrangeRender(runId, clipId, "job-resign")

        every { storageService.getFileUrl(99L, staleUrl) } returns freshUrl
        val request = slot<com.ongo.domain.ugc.shorts.ClipRenderRequest>()
        every { renderer.render(capture(request)) } returns RenderedClip(path = renderedFile, sizeBytes = 8L)
        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } returns "https://storage.test/out.mp4"

        val done = CountDownLatch(1)
        every {
            renderedClipPersister.persist(any(), any(), any(), any(), any(), any())
        } answers {
            done.countDown()
            1234L
        }

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)
        assertTrue(done.await(10, TimeUnit.SECONDS), "비동기 렌더가 시간 안에 끝나지 않았다")

        assertEquals(freshUrl, request.captured.sourceUrl)
        assertNotEquals(staleUrl, request.captured.sourceUrl, "만료됐을 수 있는 저장 URL 을 그대로 썼다")
        // 저장 URL 은 legacy key 해석용 입력으로만 넘어간다.
        verify(exactly = 1) { storageService.getFileUrl(99L, staleUrl) }
    }

    /*
     * 재서명이 실패한 순간이 저장 URL 이 만료됐을 가능성이 가장 높은 때다. 폴백하면
     * CPU 를 분 단위로 쓰고 어차피 403 으로 죽는다.
     */
    @Test
    fun `재서명이 실패하면 renderer 를 부르지 않고 job 을 실패로 남긴다`() {
        val runId = 41L
        val clipId = 42L
        val jobId = "job-resign-fail"
        val finished = arrangeRender(runId, clipId, jobId)

        every { storageService.getFileUrl(99L, staleUrl) } throws IllegalStateException("업로드된 파일을 찾을 수 없습니다")

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)
        assertTrue(finished.await(10, TimeUnit.SECONDS), "실패 확정이 시간 안에 오지 않았다")

        verify(exactly = 0) { renderer.render(any()) }
        verify(exactly = 0) { fileStoragePort.uploadByKey(any(), any(), any(), any()) }
        verify(exactly = 0) { renderedClipPersister.persist(any(), any(), any(), any(), any(), any()) }

        // 사용자에게 남는 사유에 URL·서명이 섞이면 presigned URL 이 그대로 새어나간다.
        val reason = slot<String>()
        verify(exactly = 1) { stateService.markFailed(jobId, capture(reason)) }
        assertFalse(reason.captured.contains("http"), "실패 사유에 URL 이 노출됐다")
        assertFalse(reason.captured.contains("sig="), "실패 사유에 서명이 노출됐다")
    }

    @Test
    fun `원본 videoId 가 없으면 저장 URL 로 렌더하지 않는다`() {
        val runId = 51L
        val clipId = 52L
        val jobId = "job-no-video-id"
        val finished = arrangeRender(runId, clipId, jobId, renderSpec = spec(sourceVideoId = 0L))

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)
        assertTrue(finished.await(10, TimeUnit.SECONDS), "실패 확정이 시간 안에 오지 않았다")

        // durable key 를 찾을 근거가 없으면 재서명 자체를 시도하지 않는다.
        verify(exactly = 0) { storageService.getFileUrl(any(), any()) }
        verify(exactly = 0) { renderer.render(any()) }
        verify(exactly = 1) { stateService.markFailed(jobId, any()) }
    }

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

    @Test
    fun `렌더 실행이 다른 워크스페이스에 속하면 존재 여부를 노출하지 않고 거부한다`() {
        every { workspaceRepository.findAccessibleByUserId(7L) } returns listOf(
            Workspace(id = 11L, ownerId = 7L, name = "내 워크스페이스", slug = "mine"),
        )
        every { runRepository.findById(31L) } returns com.ongo.domain.ugc.shorts.PipelineRun(
            id = 31L,
            workspaceId = 99L,
            userId = 8L,
            sourceVideoId = 41L,
        )

        assertFailsWith<NotFoundException> {
            useCase.status(userId = 7L, workspaceId = 11L, runId = 31L, clipId = 41L)
        }

        verify(exactly = 0) { renderJobRepository.findByRunAndClip(any(), any()) }
    }

    /*
     * 업로드가 예외로 끝나면 오브젝트가 올라갔는지 알 수 없다. 정책은 "모른다면 지운다" —
     * 삭제는 없는 키에도 성공하는 멱등 연산이라 헛삭제 대가가 없는 반면, 남기면 아무도 못 찾는
     * 파일이 매달 과금된다. 비용 누수의 실제 경로라 공개 진입점(requestRender)에서 확인한다.
     *
     * private runRender 를 리플렉션으로 부르지 않는다. 그러면 executor·permit·예외 처리 등
     * 실제 경로를 건너뛰어 "테스트만 통과하는" 검증이 된다.
     */
    @Test
    fun `업로드가 실패하면 방금 올리려던 키만 정확히 한 번 지운다`() {
        val runId = 11L
        val clipId = 22L
        val jobId = "job-upload-fail"
        val renderedFile = Files.createTempFile("ongo-shorts-render-test-", ".mp4")
        Files.write(renderedFile, ByteArray(8))

        val workspace = Workspace(id = 5L, ownerId = 7L, name = "ws", slug = "ws")
        every { workspaceRepository.findAccessibleByUserId(7L) } returns listOf(workspace)
        val run = PipelineRun(id = runId, workspaceId = 5L, userId = 7L, sourceVideoId = 99L)
        every { runRepository.findById(runId) } returns run
        val clip = ShortsClip(
            id = clipId, runId = runId, seq = 1, startMs = 0, endMs = 1_000,
            renderSpec = """{"spec":true}""",
        )
        every { clipRepository.findById(clipId) } returns clip
        every { templateRepository.findById(any()) } returns null

        val job = ShortsRenderJob(id = jobId, runId = runId, clipId = clipId)
        every { stateService.enqueue(runId, clipId) } returns job
        every { stateService.claimForExecution(jobId) } returns job
        every { stateService.find(jobId) } returns job

        every { resourceManager.withPermit<Any>(runId, any()) } answers {
            secondArg<() -> Any>().invoke()
        }
        every { renderSpecBuilder.parseSpec(any()) } returns
            ShortsRenderSpecBuilder.RenderSpec(
                clipSeq = 1,
                sourceVideoId = 99L,
                sourceFileUrl = "https://src/video.mp4",
                startMs = 0,
                endMs = 1_000,
                crop = null,
                hookText = null,
                hookPosition = "TOP",
                subtitles = emptyList(),
                templateId = null,
                backgroundStyle = null,
                captionFontFamily = null,
            )
        every { renderSpecBuilder.buildAss(any(), any()) } returns "[Script Info]"
        every { storageService.getFileUrl(99L, any()) } returns "https://src/video.mp4?sig=fresh"
        every { renderer.render(any()) } returns RenderedClip(path = renderedFile, sizeBytes = 8L)

        every { fileStoragePort.uploadByKey(any(), any(), any(), any()) } throws
            IllegalStateException("스토리지 장애")
        every { fileStoragePort.deleteByKey(any()) } just runs

        // 실패 표시가 곧 비동기 완료 신호다. 폴링 대신 확정적으로 기다린다.
        val finished = CountDownLatch(1)
        every { stateService.markFailed(jobId, any()) } answers {
            finished.countDown()
            job
        }

        useCase.requestRender(userId = 7L, workspaceId = 5L, runId = runId, clipId = clipId)

        assertTrue(finished.await(10, TimeUnit.SECONDS), "비동기 렌더가 시간 안에 끝나지 않았다")

        val deletedKey = slot<String>()
        verify(exactly = 1) { fileStoragePort.deleteByKey(capture(deletedKey)) }
        assertTrue(
            deletedKey.captured.matches(Regex("""shorts/run-$runId/clip-$clipId-\d+\.mp4""")),
            "지운 키가 이번 시도의 고유 키가 아니다: ${deletedKey.captured}",
        )

        // 프리픽스 전체 삭제 금지 — 같은 실행의 성공한 클립은 사용자의 영구 자산이다.
        verify(exactly = 0) { fileStoragePort.deleteByKey("shorts/run-$runId/") }

        // 업로드가 실패했으면 확정 단계는 시작조차 하면 안 된다.
        verify(exactly = 0) { renderedClipPersister.persist(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { stateService.markCompleted(any(), any()) }
        verify(exactly = 1) { stateService.markFailed(jobId, any()) }

        // 인코딩 산출물(원본만큼 크다)은 실패해도 지워져야 한다.
        assertTrue(Files.notExists(renderedFile), "임시 렌더 파일이 남았다")
    }
}
