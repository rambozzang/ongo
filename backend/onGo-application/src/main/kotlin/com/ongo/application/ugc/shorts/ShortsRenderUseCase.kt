package com.ongo.application.ugc.shorts

import com.ongo.application.common.FileStoragePort
import com.ongo.application.config.ExecutorConfig
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipRenderRequest
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.RenderCropBox
import com.ongo.domain.ugc.shorts.RendererAvailability
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.ugc.shorts.VideoRenderer
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.ugc.shorts.ClipStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.util.concurrent.ExecutorService

/**
 * 클립을 **서버에서** 렌더해 완성 영상을 만든다.
 *
 * 지금까지는 `render-spec.json` / `clip-N.ass` / `render.sh` 를 zip 으로 내려주고 사용자가
 * 자기 PC 에서 돌린 뒤 결과물을 다시 올렸다. 크롭 좌표·자막·구간 같은 어려운 판단은 이미
 * 파이프라인이 다 해뒀는데 실행만 사용자 몫이었다.
 *
 * ## 왜 비동기인가
 *
 * 인코딩은 초 단위가 아니라 분 단위다. 요청 스레드를 붙잡고 있으면 타임아웃이 나고,
 * 그 사이 연결이 끊기면 결과를 잃는다. 그래서 job 을 먼저 만들어 돌려주고(202) 실제
 * 작업은 뒤에서 돈다. 진행 상황은 [status] 로 묻는다.
 *
 * ## 동시 실행을 왜 제한하는가
 *
 * 인코딩은 CPU 를 오래 쓴다. 지금 백엔드는 API 서버 하나뿐이라 렌더가 몰리면 일반 요청이
 * 같이 느려진다. [ShortsRenderResourceManager] 가 그 수를 막는다.
 */
@Service
class ShortsRenderUseCase(
    private val clipRepository: ShortsClipRepository,
    private val runRepository: PipelineRunRepository,
    private val templateRepository: ShortsTemplateRepository,
    private val renderer: VideoRenderer,
    private val stateService: ShortsRenderJobStateService,
    // 조회 전용. 상태 전이는 전부 stateService 를 거친다.
    private val renderJobRepository: ShortsRenderJobRepository,
    private val resourceManager: ShortsRenderResourceManager,
    private val fileStoragePort: FileStoragePort,
    private val videoRepository: VideoRepository,
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 렌더 작업 전용 실행기. 요청 스레드를 붙잡지 않는다. */
    private val executor: ExecutorService = ExecutorConfig.newVirtualExecutor()

    fun availability(): RendererAvailability = renderer.checkAvailability()

    /**
     * 렌더를 요청한다. 이미 진행 중이거나 끝난 작업이 있으면 **그 작업을 그대로 돌려준다.**
     *
     * 같은 클립에 두 번 눌렀다고 두 번 인코딩하면 CPU 를 두 배로 쓰고 결과도 둘이 된다.
     * 멱등성은 [ShortsRenderJobStateService.enqueue] 가 보장한다.
     */
    fun requestRender(workspaceId: Long, runId: Long, clipId: Long): ShortsRenderJob {
        // 다른 워크스페이스의 실행을 렌더하지 못하게 막는다. 경로에 runId 가 노출되므로
        // 소속을 확인하지 않으면 남의 클립을 인코딩시킬 수 있다.
        val run = runRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)
        require(run.workspaceId == workspaceId) { "실행이 이 워크스페이스에 속하지 않는다: runId=$runId" }

        val clip = clipRepository.findById(clipId)
            ?: throw NotFoundException("클립", clipId)
        require(clip.runId == runId) { "클립이 이 실행에 속하지 않는다: clipId=$clipId runId=$runId" }
        require(!clip.renderSpec.isNullOrBlank()) {
            "렌더 스펙이 없다. RENDER_SPEC 단계를 먼저 실행해야 한다: clipId=$clipId"
        }

        val job = stateService.enqueue(runId, clipId)
        if (job.status == com.ongo.domain.ugc.shorts.ShortsRenderJobStatus.QUEUED) {
            executor.submit { runRender(job.id, runId, clipId) }
        }
        return job
    }

    fun status(workspaceId: Long, runId: Long, clipId: Long): ShortsRenderJob {
        val run = runRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)
        require(run.workspaceId == workspaceId) { "실행이 이 워크스페이스에 속하지 않는다: runId=$runId" }

        val job = renderJobRepository.findByRunAndClip(runId, clipId)
            ?: throw NotFoundException("렌더 작업", clipId)
        return job
    }

    /**
     * 실제 인코딩. 예외를 밖으로 내보내지 않는다 — 비동기 실행기에서 터지면 아무도 못 본다.
     * 실패는 job 상태로 남겨야 사용자가 안다.
     */
    private fun runRender(jobId: String, runId: Long, clipId: Long) {
        try {
            resourceManager.withPermit(runId) {
                stateService.markRunning(jobId)

                val clip = clipRepository.findById(clipId) ?: throw NotFoundException("클립", clipId)
                val spec = renderSpecBuilder.parseSpec(clip.renderSpec!!)
                val source = spec.sourceFileUrl
                    ?: throw IllegalStateException("원본 영상 위치가 없어 렌더할 수 없습니다")

                val run = runRepository.findById(runId)
                    ?: throw NotFoundException("파이프라인 실행", runId)
                val template = run.templateId?.let { templateRepository.findById(it) }

                val rendered = renderer.render(
                    ClipRenderRequest(
                        sourceUrl = source,
                        startMs = spec.startMs,
                        endMs = spec.endMs,
                        crop = spec.crop?.let { RenderCropBox(it.x, it.y, it.width, it.height) },
                        subtitleAss = renderSpecBuilder.buildAss(spec, template),
                    ),
                )

                try {
                    val key = "shorts/run-$runId/clip-$clipId-${System.nanoTime()}.mp4"
                    val fileUrl = Files.newInputStream(rendered.path).use { input ->
                        fileStoragePort.uploadByKey(key, input, rendered.contentType, rendered.sizeBytes)
                    }
                    // 업로드만으로는 끝이 아니다. 영상 레코드를 만들고 클립에 연결해야
                    // 게시 대상이 된다. 기존에는 사용자가 결과물을 올린 뒤 이 연결을 직접 했다.
                    val video = videoRepository.save(
                        Video(
                            userId = run.userId,
                            title = clip.title ?: "쇼츠 클립 ${clip.seq}",
                            fileUrl = fileUrl,
                            fileSizeBytes = rendered.sizeBytes,
                            originalFilename = "clip-${clip.seq}.mp4",
                            status = UploadStatus.DRAFT,
                        ),
                    )
                    val videoId = video.id
                        ?: throw IllegalStateException("영상 레코드를 만들지 못했습니다")

                    clipRepository.update(
                        clip.copy(renderedVideoId = videoId, status = ClipStatus.RENDERED),
                    )
                    stateService.markCompleted(jobId, videoId)
                    log.info(
                        "shorts_render event=completed runId={} clipId={} videoId={} bytes={}",
                        runId, clipId, videoId, rendered.sizeBytes,
                    )
                } finally {
                    // 인코딩 산출물은 원본만큼 크다. 올렸든 실패했든 반드시 지운다.
                    runCatching { Files.deleteIfExists(rendered.path) }
                    runCatching { Files.deleteIfExists(rendered.path.parent) }
                }
            }
        } catch (e: Exception) {
            // 사유는 사용자에게 보인다. 경로나 스택트레이스를 넣지 않는다.
            log.error("shorts_render event=failed runId={} clipId={} jobId={}", runId, clipId, jobId, e)
            runCatching { stateService.markFailed(jobId, safeReason(e)) }
        }
    }

    private fun safeReason(e: Exception): String = when (e) {
        is ShortsRenderCapacityException -> "렌더 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요."
        else -> "영상 렌더링에 실패했습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요."
    }
}
