package com.ongo.application.ugc.shorts

import com.ongo.application.common.FileStoragePort
import com.ongo.application.common.StorageObjectCleanup
import com.ongo.application.config.ExecutorConfig
import com.ongo.application.video.StorageService
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipRenderRequest
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.RenderCropBox
import com.ongo.domain.ugc.shorts.RendererAvailability
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.ugc.shorts.VideoRenderer
import com.ongo.domain.workspace.WorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.ConcurrentHashMap

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
    private val renderedClipPersister: RenderedClipPersister,
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
    private val workspaceRepository: WorkspaceRepository,
    /** 렌더 직전 원본 URL 재발급용. 저장된 스펙 URL 은 7일이면 만료된다. */
    private val storageService: StorageService,
    /** 파일럿 측정용 append-only 이벤트. 재시도마다 실패가 누적된다. */
    private val pilotEventRepository: ShortsPilotEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 렌더 작업 전용 실행기. 요청 스레드를 붙잡지 않는다. */
    private val executor: ExecutorService = ExecutorConfig.newVirtualExecutor()
    private val activeJobIds = ConcurrentHashMap.newKeySet<String>()

    fun availability(): RendererAvailability = renderer.checkAvailability()

    /**
     * 렌더를 요청한다. 이미 진행 중이거나 끝난 작업이 있으면 **그 작업을 그대로 돌려준다.**
     *
     * 같은 클립에 두 번 눌렀다고 두 번 인코딩하면 CPU 를 두 배로 쓰고 결과도 둘이 된다.
     * 멱등성은 [ShortsRenderJobStateService.enqueue] 가 보장한다.
     */
    fun requestRender(userId: Long, workspaceId: Long, runId: Long, clipId: Long): ShortsRenderJob {
        assertWorkspaceAccess(userId, workspaceId)
        // 다른 워크스페이스의 실행을 렌더하지 못하게 막는다. 경로에 runId 가 노출되므로
        // 소속을 확인하지 않으면 남의 클립을 인코딩시킬 수 있다.
        val run = runRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)
        if (run.workspaceId != workspaceId) {
            // 경로에 runId가 노출되므로 다른 워크스페이스의 존재 여부도 숨긴다.
            throw NotFoundException("파이프라인 실행", runId)
        }

        val clip = clipRepository.findById(clipId)
            ?: throw NotFoundException("클립", clipId)
        require(clip.runId == runId) { "클립이 이 실행에 속하지 않는다: clipId=$clipId runId=$runId" }
        require(!clip.renderSpec.isNullOrBlank()) {
            "렌더 스펙이 없다. RENDER_SPEC 단계를 먼저 실행해야 한다: clipId=$clipId"
        }

        val job = stateService.enqueue(runId, clipId)
        if (job.status == com.ongo.domain.ugc.shorts.ShortsRenderJobStatus.QUEUED) {
            // 제출 전에 원자적으로 선점한다. 워커 tick/재시도/동시 POST가 겹쳐도
            // QUEUED job 하나당 실행 스레드는 하나만 만들어진다.
            val claimed = stateService.claimForExecution(job.id)
            if (claimed != null) {
                executor.submit { runRender(claimed.id, runId, clipId) }
            }
        }
        return stateService.find(job.id)
    }

    fun status(userId: Long, workspaceId: Long, runId: Long, clipId: Long): ShortsRenderJob {
        assertWorkspaceAccess(userId, workspaceId)
        val run = runRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)
        if (run.workspaceId != workspaceId) {
            throw NotFoundException("파이프라인 실행", runId)
        }

        val job = renderJobRepository.findByRunAndClip(runId, clipId)
            ?: throw NotFoundException("렌더 작업", clipId)
        return job
    }

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    /**
     * 실제 인코딩. 예외를 밖으로 내보내지 않는다 — 비동기 실행기에서 터지면 아무도 못 본다.
     * 실패는 job 상태로 남겨야 사용자가 안다.
     */
    private fun runRender(jobId: String, runId: Long, clipId: Long) {
        activeJobIds += jobId
        try {
            resourceManager.withPermit(runId) {
                val clip = clipRepository.findById(clipId) ?: throw NotFoundException("클립", clipId)
                val spec = renderSpecBuilder.parseSpec(clip.renderSpec!!)
                val source = freshSourceUrl(spec)

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

                    /*
                     * 업로드가 예외로 끝나면 오브젝트가 올라갔는지 알 수 없다. 스토리지가 끊긴
                     * 시점에 따라 아무것도 없을 수도, 일부만 남을 수도, 사실상 다 올라갔을
                     * 수도 있다.
                     *
                     * 정책: **모른다면 지운다.** 오브젝트 삭제는 없는 키에 대해서도 성공하는
                     * 멱등 연산이라 헛삭제의 대가가 없는 반면, 남겨두면 아무도 못 찾는 파일이
                     * 매달 과금된다. 이 키는 방금 만든 고유값(nanoTime 포함)이라 다른 시도의
                     * 결과물을 건드릴 일도 없다.
                     */
                    val fileUrl = try {
                        Files.newInputStream(rendered.path).use { input ->
                            fileStoragePort.uploadByKey(key, input, rendered.contentType, rendered.sizeBytes)
                        }
                    } catch (e: Exception) {
                        StorageObjectCleanup(fileStoragePort, key).deleteOnce()
                        throw e
                    }

                    // 업로드 이후의 확정은 트랜잭션 경계 안에서 한다. 여기서 실패하거나 커밋이
                    // 깨지면 persister 가 이 키만 정리한다(프리픽스 전체가 아니라).
                    val videoId = renderedClipPersister.persist(
                        jobId = jobId,
                        clip = clip,
                        userId = run.userId,
                        objectKey = key,
                        fileUrl = fileUrl,
                        sizeBytes = rendered.sizeBytes,
                    )
                    log.info(
                        "shorts_render event=completed runId={} clipId={} videoId={} bytes={}",
                        runId, clipId, videoId, rendered.sizeBytes,
                    )

                    /*
                     * 첫 납품 시각. 클립이 여러 개면 이 경로를 병렬로 지나므로 조건을 SQL 에
                     * 둔 원자적 갱신을 쓴다 — 읽고 쓰면 마지막 완료 시각이 첫 납품을 덮는다.
                     *
                     * 별도 성공 이벤트를 두지 않는다. delivered_at 이 있으면 납품된 것이다.
                     */
                    runCatching { runRepository.markDeliveredIfAbsent(runId, Instant.now()) }
                        .onFailure { log.warn("쇼츠 첫 납품 시각 기록 실패: runId={}", runId, it) }
                } finally {
                    // 인코딩 산출물은 원본만큼 크다. 올렸든 실패했든 반드시 지운다.
                    runCatching { Files.deleteIfExists(rendered.path) }
                    runCatching { Files.deleteIfExists(rendered.path.parent) }
                }
            }
        } catch (e: Exception) {
            // 사유는 사용자에게 보인다. 경로나 스택트레이스를 넣지 않는다.
            log.error("shorts_render event=failed runId={} clipId={} jobId={}", runId, clipId, jobId, e)
            val failed = runCatching { stateService.markFailed(jobId, safeReason(e)) }.getOrNull()
            recordRenderFailure(runId, jobId, failed?.attemptCount)
        } finally {
            activeJobIds -= jobId
        }
    }

    /**
     * 렌더 직전에 원본 URL 을 **다시 발급**한다.
     *
     * ## 왜 저장된 스펙 URL 을 못 쓰는가
     *
     * `render_spec` 의 `source.fileUrl` 은 RENDER_SPEC 단계에서 굳은 presigned URL 이고,
     * 서명 유효기간은 7일이 상한이다(SigV4/R2 의 제약이라 늘릴 수 없다). 사용자가 훅 선택이나
     * 예약 확정을 며칠 미루면 렌더를 누르는 시점에는 이미 죽은 URL 이다. 그러면 ffmpeg 이
     * 403 을 받고, 사용자에게는 "렌더 실패"만 남는다 — 원인을 알 길이 없다.
     *
     * 그래서 durable object key 에서 새로 서명한다. 스펙은 그대로 두고 이 호출의 결과만 쓴다.
     *
     * ## 실패하면 렌더하지 않는다
     *
     * 재서명이 실패했을 때 저장된 URL 로 폴백하면, 그 URL 이 만료됐을 가능성이 정확히 지금
     * 가장 높다. 인코딩은 CPU 를 분 단위로 쓰므로 죽을 것을 알면서 시작할 이유가 없다.
     * 여기서 던지면 호출자의 catch 가 job 을 FAILED 로 남겨 사용자가 상태로 확인한다.
     *
     * 예외 메시지에 URL·서명·키를 담지 않는다. 실패 사유는 videoId 까지만 남긴다 —
     * presigned URL 자체가 자격증명이라 로그에 남으면 유효기간 동안 누구나 쓸 수 있다.
     */
    private fun freshSourceUrl(spec: ShortsRenderSpecBuilder.RenderSpec): String {
        val storedUrl = spec.sourceFileUrl
        if (storedUrl.isNullOrBlank()) {
            throw IllegalStateException("원본 영상 위치가 없어 렌더할 수 없습니다")
        }
        if (spec.sourceVideoId <= 0) {
            // videoId 가 없으면 durable key 를 찾을 근거가 없다. 저장 URL 로 때우지 않는다.
            throw IllegalStateException("원본 영상을 식별할 수 없어 렌더할 수 없습니다")
        }

        val refreshed = try {
            // 저장 URL 은 legacy 행의 object key 를 되짚는 입력으로만 넘긴다.
            storageService.getFileUrl(spec.sourceVideoId, storedUrl)
        } catch (e: Exception) {
            // cause 를 달지 않는다. 스토리지 예외 메시지에 서명된 URL 이 섞여 로그로 새는 경로를 막는다.
            log.warn("shorts_render event=resign_failed videoId={} reason={}", spec.sourceVideoId, e.javaClass.simpleName)
            throw IllegalStateException("원본 영상 주소를 갱신하지 못해 렌더할 수 없습니다")
        }

        if (refreshed.isBlank()) {
            throw IllegalStateException("원본 영상 주소를 갱신하지 못해 렌더할 수 없습니다")
        }
        return refreshed
    }

    /**
     * 렌더 실패를 파일럿 측정 이벤트로 누적한다.
     *
     * ## 왜 job 행만으로는 부족한가
     *
     * `shorts_render_jobs` 는 (run_id, clip_id) 유니크라 클립당 한 행이고, 재시도는
     * [ShortsRenderJobStateService.enqueue] 가 `failureReason`/`startedAt` 을 지우고
     * 같은 행을 재사용한다. 세 번 실패하고 성공한 클립과 첫 시도에 성공한 클립이
     * 최종 상태로는 구분되지 않는다. 그래서 실패마다 별도 행을 남긴다.
     *
     * ## 왜 예외를 삼키는가
     *
     * 이 시점의 job 은 **이미 실패로 확정**됐다. 측정 기록이 안 됐다고 여기서 예외를
     * 올리면 비동기 실행기 밖으로 새어 아무도 못 보는 곳에서 죽고, 확정된 실패 상태를
     * 되돌리지도 못한다. 측정은 파이프라인보다 중요하지 않다.
     */
    private fun recordRenderFailure(runId: Long, jobId: String, attemptCount: Int?) {
        runCatching {
            pilotEventRepository.save(
                ShortsPilotEvent(
                    runId = runId,
                    eventType = ShortsPilotEventType.RENDER_ATTEMPT_FAILED,
                    actorType = ShortsPilotActorType.SYSTEM,
                    // 재시도마다 달라지는 실제 회차. 이 값이 행이 덮이지 않았다는 근거다.
                    attemptNo = attemptCount,
                ),
            )
        }.onFailure { log.warn("쇼츠 렌더 실패 이벤트 기록 실패: runId={} jobId={}", runId, jobId, it) }
    }

    /** 같은 JVM에서 아직 실행 중인 job은 재기동 복구기가 건드리지 않게 한다. */
    fun isActiveInThisProcess(jobId: String): Boolean = jobId in activeJobIds

    private fun safeReason(e: Exception): String = when (e) {
        is ShortsRenderCapacityException -> "렌더 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요."
        else -> "영상 렌더링에 실패했습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요."
    }
}
