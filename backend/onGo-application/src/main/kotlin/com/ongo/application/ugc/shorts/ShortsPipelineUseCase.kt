package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.audio.AudioPreparationException
import com.ongo.application.activitylog.ActivityLogActions
import com.ongo.application.activitylog.ActivityLogUseCase
import com.ongo.application.ai.audio.TranscriptionAudioPort
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.dto.ClipHookResponse
import com.ongo.application.ugc.shorts.dto.CreatePipelineRunRequest
import com.ongo.application.ugc.shorts.dto.HookSelectionRequest
import com.ongo.application.ugc.shorts.dto.PipelineRunDetailResponse
import com.ongo.application.ugc.shorts.dto.PipelineRunListResponse
import com.ongo.application.ugc.shorts.dto.PipelineRunResponse
import com.ongo.application.ugc.shorts.dto.RunStageResponse
import com.ongo.application.ugc.shorts.dto.ScheduleConfirmRequest
import com.ongo.application.ugc.shorts.dto.ClipPublicationResponse
import com.ongo.application.ugc.shorts.dto.ShortsClipResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
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
import com.ongo.domain.ugc.shorts.ShortsTemplate
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.application.video.StorageService
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.WorkspaceRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 쇼츠 파이프라인 유스케이스. 실행 생성/조회/재실행/후킹 선택/예약 확정/산출물 다운로드를 담당한다.
 * 실제 단계 실행은 이벤트 → 리스너 → 오케스트레이터가 비동기로 처리한다.
 */
@Service
class ShortsPipelineUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val runStageRepository: RunStageRepository,
    private val shortsClipRepository: ShortsClipRepository,
    private val clipHookRepository: ClipHookRepository,
    private val shortsTemplateRepository: ShortsTemplateRepository,
    private val videoRepository: VideoRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
    private val eventPublisher: ApplicationEventPublisher,
    /**
     * 전사 가능한 원본 크기 상한. 기본 2GiB.
     *
     * 이 값은 제공자 한도가 아니라 우리가 감당하기로 정한 선이다. TRANSCRIBE 는 원본을
     * 통째로 읽어 오디오를 뽑으므로 여기가 커질수록 추출 시간·임시 디스크가 같이 는다.
     */
    @param:Value("\${shorts.transcribe.max-source-bytes:2147483648}")
    private val maxSourceBytes: Long,
    /**
     * 전사 가능한 원본 길이 상한. 기본 3시간.
     *
     * 크기와 별개로 필요한 상한이다. 조각 수·전사 요금·렌더 큐 대기는 길이에 비례하는데
     * 바이트 수로는 길이를 알 수 없다. 이 값도 제공자 한도가 아니라 우리가 정한 선이다.
     */
    @param:Value("\${shorts.transcribe.max-source-duration-ms:10800000}")
    private val maxSourceDurationMs: Long,
    /** 길이 측정 전용. 여기서는 재지 않고 [TranscriptionAudioPort] 에 맡긴다. */
    private val audioPort: TranscriptionAudioPort,
    /** 번들에 넣을 원본 URL 재발급용. 저장된 스펙 URL 은 7일이면 만료된다. */
    private val storageService: StorageService,
    /** 파일럿 측정용 append-only 이벤트. 재실행 증거를 삭제 전에 남긴다. */
    private val pilotEventRepository: ShortsPilotEventRepository,
    /** 생성 전 잔액 확인 전용. 여기서 차감하거나 예약하지 않는다. */
    private val creditService: CreditService,
    /** 전환 퍼널 측정. 기록은 업무 흐름의 성패를 바꾸지 않는다. */
    private val activityLogUseCase: ActivityLogUseCase,
    /**
     * 예약 대상 검증 전용. 여기서 채널을 바꾸지 않는다 — 확정 전에 "지금 게시 가능한
     * 계정인가"만 읽는다.
     */
    private val channelRepository: ChannelRepository,
    /** 대상별 게시 결과 조회 전용. 상세 응답에 사실대로 실어 보내기 위해서만 읽는다. */
    private val clipPublicationRepository: ClipPublicationRepository,
) {

    private val mapper = jacksonObjectMapper()

    /** 실행 생성: 원본 영상 확인 → PENDING 저장 → TRANSCRIBE부터 이벤트 발행. */
    @Transactional
    fun createRun(
        userId: Long,
        workspaceId: Long,
        request: CreatePipelineRunRequest,
        idempotencyKey: String? = null,
    ): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey)
        val requestHash = normalizedIdempotencyKey?.let { requestFingerprint(request) }

        normalizedIdempotencyKey?.let { key ->
            pipelineRunRepository.findByUserIdAndIdempotencyKey(userId, key)?.let { existing ->
                require(existing.workspaceId == workspaceId) {
                    "Idempotency-Key가 다른 워크스페이스 요청에 재사용되었습니다"
                }
                require(existing.requestHash == null || existing.requestHash == requestHash) {
                    "Idempotency-Key가 다른 요청에 재사용되었습니다"
                }
                val existingTitle = videoRepository.findById(existing.sourceVideoId)?.title
                return existing.toResponse(existingTitle)
            }
        }

        if (request.autoSchedule) {
            require(request.scheduleStartAt != null) { "자동 쇼츠 예약 시작 시각이 필요합니다" }
            require(request.scheduleIntervalHours != null && request.scheduleIntervalHours > 0) {
                "자동 쇼츠 예약 간격은 1시간 이상이어야 합니다"
            }
            require(request.platforms.isNotEmpty()) { "자동 쇼츠 게시 대상 플랫폼이 필요합니다" }
        }

        val video = videoRepository.findById(request.sourceVideoId)
            ?: throw BusinessException("SHORTS_SOURCE_VIDEO_NOT_FOUND", "원본 영상을 찾을 수 없습니다: ${request.sourceVideoId}")
        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "해당 영상에 접근 권한이 없습니다")
        }
        assertTranscribableSize(video.fileSizeBytes)
        /*
         * 프로브는 여기서 **한 번만** 부른다. 반환값을 그대로 견적·선검사·저장에 쓴다.
         *
         * 다시 재면 두 측정이 어긋날 수 있고, 그러면 사용자에게 알린 금액과 실제 차감액이
         * 달라진다. 그건 인용한 적 없는 청구다. 게다가 프로브는 원본을 여는 외부 프로세스라
         * 부를 때마다 생성 API 가 느려진다.
         */
        val sourceDurationMs = assertTranscribableDuration(video.fileUrl)
        assertEnoughCreditsForRun(userId, sourceDurationMs)

        val run = PipelineRun(
            workspaceId = workspaceId,
            userId = userId,
            sourceVideoId = request.sourceVideoId,
            templateId = request.templateId,
            autoSchedule = request.autoSchedule,
            autoScheduleStartAt = request.scheduleStartAt,
            autoScheduleIntervalHours = request.scheduleIntervalHours,
            autoSchedulePlatforms = request.platforms,
            idempotencyKey = normalizedIdempotencyKey,
            requestHash = requestHash,
            // 방금 잰 값을 그대로 고정한다. 재실행은 이 값을 다시 읽어 같은 금액을 낸다.
            sourceDurationMs = sourceDurationMs,
            status = PipelineRunStatus.PENDING,
        )

        val saved = if (normalizedIdempotencyKey == null) {
            PipelineRunRepository.SaveResult(pipelineRunRepository.save(run), created = true)
        } else {
            pipelineRunRepository.saveIdempotently(run)
        }
        require(saved.run.workspaceId == workspaceId) {
            "Idempotency-Key가 다른 워크스페이스 요청에 재사용되었습니다"
        }
        require(saved.run.requestHash == null || saved.run.requestHash == requestHash) {
            "Idempotency-Key가 다른 요청에 재사용되었습니다"
        }
        if (saved.created) {
            eventPublisher.publishEvent(ShortsPipelineEvent(runId = saved.run.id, fromStage = PipelineStage.TRANSCRIBE))
            /*
             * 퍼널의 마지막 단계. 실행 행이 실제로 만들어졌을 때만 기록한다 —
             * 멱등 재사용으로 기존 실행을 돌려준 경우는 새 사건이 아니다.
             *
             * 일반(트랜잭션 결속) 기록이다. 이 트랜잭션이 롤백되면 실행도 없으므로
             * 성공 흔적도 함께 사라져야 한다.
             */
            activityLogUseCase.logActivity(
                userId = userId,
                action = ActivityLogActions.SHORTS_RUN_CREATED,
                entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
                entityId = saved.run.id,
            )
        }
        return saved.run.toResponse(video.title)
    }

    /**
     * 전사 가능한 크기인지 **실행을 만들기 전에** 본다.
     *
     * TRANSCRIBE 는 원본에서 오디오를 뽑아 조각내는 단계라 원본이 커질수록 추출 시간과
     * 디스크가 함께 는다. 이걸 단계 안에서 터뜨리면 사용자는 실행을 만들고 크레딧이 걸린
     * 뒤에야 실패를 본다. 그래서 행도 이벤트도 만들기 전에 거절한다.
     *
     * 상한의 근거는 제공자 문서가 아니라 **우리 설정값**이다. 외부 API 한도를 사실처럼
     * 단정하지 않는다.
     *
     * 크기를 모르는 원본(외부 URL 로 만든 행 등)은 통과시킨다 — 모른다는 것이 초과했다는
     * 증거는 아니고, 실제로 못 다루면 추출 단계가 정직하게 실패한다.
     */
    private fun assertTranscribableSize(fileSizeBytes: Long?) {
        val limit = maxSourceBytes
        if (fileSizeBytes == null || fileSizeBytes <= limit) return
        throw BusinessException(
            "SHORTS_SOURCE_VIDEO_TOO_LARGE",
            "원본 영상이 너무 커서 쇼츠로 변환할 수 없습니다. " +
                "현재 상한은 ${gib(limit)}GB이며, 이 영상은 약 ${gib(fileSizeBytes)}GB입니다. " +
                "영상을 나눠서 올린 뒤 다시 시도해 주세요.",
        )
    }

    /** 사용자에게 보이는 숫자다. 내림하면 2.9GB가 "2GB"가 되어 상한과 모순돼 보인다. */
    private fun gib(bytes: Long): String = String.format("%.1f", bytes.toDouble() / BYTES_PER_GIB)

    /**
     * 전사 가능한 **길이**인지 실행을 만들기 전에 측정한다.
     *
     * 크기 상한만으로는 못 막는다. 저화질 롱폼은 작고 길다 — 3시간짜리 200MB 원본은
     * 크기 검사를 통과하지만 조각 수·전사 요금·렌더 큐 대기가 모두 길이에 비례해 는다.
     * 그래서 바이트로 길이를 추정하지 않고 프로브로 **잰다**.
     *
     * ## 실패는 통과가 아니다
     *
     * 프로브를 못 쓰거나 출력이 길이로 안 읽히면 거절한다. "모르니 일단 진행"은 돈이 걸린
     * 파이프라인을 한참 뒤에 죽이는 선택이다. 여기서 막으면 사용자는 즉시, 이유와 함께
     * 알게 된다.
     *
     * URL 이 없는 원본은 [assertTranscribableSize] 뒤 TRANSCRIBE 가 VIDEO_FILE_NOT_FOUND 로
     * 처리한다. 여기서는 잴 대상 자체가 없으므로 프로브를 부르지 않는다.
     *
     * @return 측정해 수락한 길이(ms). 잴 대상이 없으면 `null` 이다.
     *   이 값이 그대로 청구 근거가 되므로 호출자는 다시 재지 말고 이것을 써야 한다.
     */
    private fun assertTranscribableDuration(fileUrl: String?): Long? {
        if (fileUrl.isNullOrBlank()) return null

        val durationMs = try {
            audioPort.probeDurationMs(fileUrl)
        } catch (e: AudioPreparationException) {
            throw BusinessException(
                "SHORTS_SOURCE_DURATION_UNKNOWN",
                "원본 영상의 길이를 확인할 수 없어 쇼츠 변환을 시작할 수 없습니다. " +
                    "파일이 손상되었는지 확인하거나 잠시 후 다시 시도해 주세요.",
            )
        }

        /*
         * 포트 계약은 "항상 0 보다 크다"지만 여기서 한 번 더 본다. 어댑터가 계약을 깨고
         * 0 이나 음수를 돌려주면 아래 비교는 조용히 통과하고, 그게 정확히 우리가 막으려던
         * "길이를 모르는 채 진행" 상태다. 방어가 아니라 fail-closed 를 유지하는 검사다.
         */
        if (durationMs <= 0) {
            throw BusinessException(
                "SHORTS_SOURCE_DURATION_UNKNOWN",
                "원본 영상의 길이를 확인할 수 없어 쇼츠 변환을 시작할 수 없습니다. " +
                    "파일이 손상되었는지 확인하거나 잠시 후 다시 시도해 주세요.",
            )
        }

        if (durationMs > maxSourceDurationMs) {
            throw BusinessException(
                "SHORTS_SOURCE_VIDEO_TOO_LONG",
                "원본 영상이 너무 길어 쇼츠로 변환할 수 없습니다. " +
                    "현재 상한은 ${minutes(maxSourceDurationMs)}분이며, 이 영상은 약 ${minutes(durationMs)}분입니다. " +
                    "영상을 나눠서 올린 뒤 다시 시도해 주세요.",
            )
        }

        return durationMs
    }

    private fun minutes(ms: Long): String = String.format("%.0f", ms.toDouble() / 60_000)

    /**
     * 완주에 필요한 크레딧이 있는지 **실행을 만들기 전에** 본다.
     *
     * ## 왜 필요한가
     *
     * 차감은 단계마다 일어난다([ShortsPipelineOrchestrator]). 중간에 잔액이 떨어지면 그
     * 단계분만 환불되고 앞 단계에서 쓴 크레딧은 돌아오지 않는다. 결과물도 없다. 완주 비용이
     * 애초에 모자란 사용자는 그 손실이 확정돼 있는데도 여기까지 오게 된다.
     *
     * ## 이것은 예약이 아니다
     *
     * 여기서 읽은 잔액은 그 순간의 값일 뿐이다. 통과한 뒤 다른 요청이 크레딧을 써서 실제
     * 차감이 실패할 수 있다(TOCTOU). 그 경우는 [ShortsPipelineOrchestrator] 의
     * `validateAndDeduct` 와 기존 `InsufficientCreditException` 이 최종 방어선으로 그대로
     * 남는다. 이 검사는 **확실히 실패할 요청을 미리 걷어내는 것**이지 성공을 보장하지 않는다.
     * 그래서 크레딧을 잡아두지도 않는다 — 잡아두면 취소·만료 처리가 새로 필요해진다.
     *
     * @param sourceDurationMs 방금 측정한 길이. 전사 크레딧이 여기에 비례하므로 총액도
     *   길이에 따라 달라진다. 오케스트레이터의 실제 차감과 **같은 함수**를 써야 선검사를
     *   통과한 실행이 중간에 죽지 않는다.
     */
    private fun assertEnoughCreditsForRun(userId: Long, sourceDurationMs: Long?) {
        val required = ShortsPipelineCreditRequirements.totalCreditsForRun(sourceDurationMs)
        val available = creditService.getBalance(userId).totalBalance
        if (available >= required) return

        /*
         * 전환 퍼널의 분모다. 이 자리를 세지 않으면 "막힌 사람 중 몇 명이 체험을 시작했나"의
         * 모수가 없다.
         *
         * **독립 트랜잭션으로 기록한다.** 바로 아래에서 던지는 예외가 createRun 트랜잭션을
         * 통째로 롤백시키므로, 일반 기록은 세려는 그 사건과 함께 사라진다.
         *
         * 이 정확한 조건에서만 기록한다 — 크기·길이·소유권 거절은 다른 사건이고 같은
         * 이름으로 섞으면 집계가 무의미해진다.
         */
        activityLogUseCase.logActivityIndependently(
            userId = userId,
            action = ActivityLogActions.SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT,
            entityType = ActivityLogActions.ENTITY_SHORTS_RUN,
        )

        /*
         * 금액의 **근거**를 함께 말한다.
         *
         * 화면(ShortsPipelineView)은 이 코드일 때 서버 메시지를 그대로 보여준다. 숫자만
         * 바뀌면 사용자는 "왜 어제는 37 이었는데 오늘은 87 인가"를 알 수 없고, 그건 조용히
         * 다르게 청구하는 것과 같다. 길이를 못 잰 원본(URL 없음)은 근거를 만들어내지 않고
         * 숫자만 말한다.
         */
        val basis = sourceDurationMs?.let {
            "전사는 ${minutes(ShortsPipelineCreditRequirements.TRANSCRIBE_BILLING_WINDOW_MS)}분마다 " +
                "크레딧 ${AiFeature.STT.creditCost}개가 붙으며, 이 영상은 약 ${minutes(it)}분입니다. "
        }.orEmpty()

        throw BusinessException(
            "SHORTS_INSUFFICIENT_CREDIT_FOR_RUN",
            // 실제 숫자만 말한다. "업그레이드하면 N건" 같은 추정은 붙이지 않는다.
            "쇼츠 변환을 시작하려면 크레딧 ${required}개가 필요합니다. " + basis +
                "현재 잔여는 ${available}개입니다.",
        )
    }

    /** 실행 목록 (페이지네이션). */
    fun listRuns(userId: Long, workspaceId: Long, page: Int, size: Int): PipelineRunListResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val runs = pipelineRunRepository.findByWorkspace(workspaceId, offset = page * size, limit = size)
        val total = pipelineRunRepository.countByWorkspace(workspaceId)
        val titles = videoRepository.findByIds(runs.map { it.sourceVideoId })
            .mapNotNull { video -> video.id?.let { it to video.title } }
            .toMap()
        return PipelineRunListResponse(
            runs = runs.map { it.toResponse(titles[it.sourceVideoId]) },
            total = total,
            page = page,
            size = size,
        )
    }

    /** 실행 상세 (단계 + 클립 + 후킹 포함). */
    fun getRunDetail(userId: Long, workspaceId: Long, runId: Long): PipelineRunDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        return buildDetail(run)
    }

    /**
     * 단계 재실행: 그 단계와 이후 단계 기록을 지우고 해당 단계부터 다시 돌린다.
     * - SEGMENT 이전부터: 후킹 + 클립 전부 삭제, clipCount=0
     * - HOOK부터: 후킹만 삭제하고 클립은 DRAFT/scheduledAt=null로
     * - 그 외(SUBTITLE/TEMPLATE/RENDER_SPEC/VALIDATE): 실행기가 덮어쓴다
     */
    @Transactional
    fun rerunStage(userId: Long, workspaceId: Long, runId: Long, stageName: String): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status == PipelineRunStatus.RUNNING) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "실행 중에는 재실행할 수 없습니다")
        }
        val stage = parseStage(stageName)
        if (stage == PipelineStage.SCHEDULE) {
            throw BusinessException("SHORTS_STAGE_NOT_RERUNNABLE", "예약 단계는 재실행할 수 없습니다. 예약 확정을 이용해 주세요")
        }

        /*
         * **삭제보다 먼저** 기록한다. 아래 deleteFrom 은 스테이지 행을, 이어지는 분기는
         * 클립과 후킹을 하드 삭제하므로, 이 시점을 지나면 "고객이 결과를 물렸다"는 증거가
         * 어디에도 남지 않는다. 수정률은 파일럿에서 가장 비싼 신호다.
         *
         * 같은 @Transactional 안이라 삭제가 롤백되면 이벤트도 함께 사라진다 —
         * 일어나지 않은 재실행이 기록되지 않는다.
         *
         * stageName·클립·텍스트는 넣지 않는다. 판단에 필요한 것은 "누가 언제 몇 번"이다.
         */
        pilotEventRepository.save(
            ShortsPilotEvent(
                runId = runId,
                eventType = ShortsPilotEventType.STAGE_RERUN,
                actorType = ShortsPilotActorType.CUSTOMER,
                actorId = userId,
            ),
        )

        runStageRepository.deleteFrom(runId, stage.sortOrder)

        val clips = shortsClipRepository.findByRunId(runId)
        when {
            stage.sortOrder <= PipelineStage.SEGMENT.sortOrder -> {
                clipHookRepository.deleteByClipIds(clips.map { it.id })
                shortsClipRepository.deleteByRunId(runId)
                pipelineRunRepository.update(run.copy(clipCount = 0))
            }
            stage == PipelineStage.HOOK -> {
                clipHookRepository.deleteByClipIds(clips.map { it.id })
                clips.forEach { clip ->
                    shortsClipRepository.update(clip.copy(status = ClipStatus.DRAFT, scheduledAt = null))
                }
            }
            else -> Unit // 나머지 단계는 실행기가 결과를 덮어쓴다
        }

        val reset = pipelineRunRepository.update(
            pipelineRunRepository.findById(runId)!!.copy(
                status = PipelineRunStatus.PENDING,
                currentStage = null,
                errorMessage = null,
            ),
        )
        eventPublisher.publishEvent(ShortsPipelineEvent(runId = runId, fromStage = stage))
        val video = videoRepository.findById(reset.sourceVideoId)
        return reset.toResponse(video?.title)
    }

    /** 후킹 일괄 선택: A/B 또는 CUSTOM(직접 입력)을 확정하고 TEMPLATE부터 이어 달린다. */
    @Transactional
    fun selectHooks(userId: Long, workspaceId: Long, runId: Long, request: HookSelectionRequest): PipelineRunDetailResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status != PipelineRunStatus.AWAITING_HOOK_SELECTION) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "후킹 선택 대기 상태가 아닙니다: ${run.status}")
        }

        val clips = shortsClipRepository.findByRunId(runId)
        val clipsById = clips.associateBy { it.id }
        val hooksByClip = clipHookRepository.findByClipIds(clips.map { it.id }).groupBy { it.clipId }

        request.selections.forEach { selection ->
            val clip = clipsById[selection.clipId]
                ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: ${selection.clipId}")

            clipHookRepository.clearSelection(clip.id)
            val text = selection.customText
                ?: hooksByClip[clip.id]?.firstOrNull { it.variant == selection.variant }?.text
                ?: throw BusinessException("SHORTS_RUN_INVALID_STATE", "선택할 후킹 문구가 없습니다: clipId=${clip.id}, variant=${selection.variant}")
            clipHookRepository.markSelected(clip.id, selection.variant, text)
            shortsClipRepository.update(clip.copy(status = ClipStatus.HOOK_SELECTED))
        }

        request.discardClipIds.forEach { clipId ->
            val clip = clipsById[clipId]
                ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: $clipId")
            shortsClipRepository.update(clip.copy(status = ClipStatus.DISCARDED))
        }

        pipelineRunRepository.update(
            pipelineRunRepository.findById(runId)!!.copy(status = PipelineRunStatus.PENDING, errorMessage = null),
        )
        eventPublisher.publishEvent(ShortsPipelineEvent(runId = runId, fromStage = PipelineStage.TEMPLATE))

        return buildDetail(pipelineRunRepository.findById(runId)!!)
    }

    /** 예약 확정: SCHEDULE 단계만 파라미터와 함께 실행한다. */
    @Transactional
    fun confirmSchedule(userId: Long, workspaceId: Long, runId: Long, request: ScheduleConfirmRequest): PipelineRunResponse {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        if (run.status != PipelineRunStatus.AWAITING_SCHEDULE) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약 확정 대기 상태가 아닙니다: ${run.status}")
        }
        if (request.intervalHours <= 0) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약 간격은 1시간 이상이어야 합니다")
        }
        assertPublishTargetsUsable(userId, request.platforms)

        val updated = pipelineRunRepository.update(
            run.copy(status = PipelineRunStatus.PENDING, errorMessage = null),
        )
        eventPublisher.publishEvent(
            ShortsPipelineEvent(
                runId = runId,
                fromStage = PipelineStage.SCHEDULE,
                scheduleStartAt = request.startAt,
                scheduleIntervalHours = request.intervalHours,
                platforms = request.platforms,
            ),
        )
        val video = videoRepository.findById(updated.sourceVideoId)
        return updated.toResponse(video?.title)
    }

    /**
     * 예약 대상이 **지금 게시할 수 있는 계정**인지 확정 전에 본다.
     *
     * ## 왜 여기인가
     *
     * 지금까지 이 검사는 비동기 게시 단계(`PublishVideoUseCase`)에만 있었다. 그래서
     * 확정 API 는 어떤 문자열이든 200 을 돌려주고, 사용자는 성공 응답을 본 **뒤에**
     * 실패한 실행을 보게 됐다. 화면은 연결된 채널만 보여주도록 고쳤지만 API 를 직접
     * 부르면 같은 경로가 그대로 열려 있다.
     *
     * 실패를 앞으로 당긴다. 하나라도 못 쓰는 대상이 있으면 실행 상태를 바꾸지도, 이벤트를
     * 발행하지도 않고 즉시 던진다 — 이 메서드가 [assertPublishTargetsUsable] 를
     * `pipelineRunRepository.update` **앞에서** 부르는 이유다.
     *
     * ## 왜 계정까지 요구하는가
     *
     * `YOUTUBE` 처럼 플랫폼만 오면 게시 단계가 그 사용자의 채널 중 하나를 임의로 고른다.
     * 계정이 둘이면 어디에 올라갈지 사용자도 우리도 알 수 없다. 그래서 정확한 계정을
     * 가리키는 `PLATFORM#channelId` 만 받는다.
     *
     * 빈 목록은 통과시킨다. `ScheduleStageExecutor` 가 그것을 "게시 없이 예약 시각만
     * 확정"으로 정의하고 있고, 그건 유효한 사용이다.
     */
    private fun assertPublishTargetsUsable(userId: Long, rawTargets: List<String>) {
        if (rawTargets.isEmpty()) return

        rawTargets.forEach { raw ->
            // 파싱 실패는 형식 오류다. 원인을 그대로 알려야 사용자가 고칠 수 있다.
            val target = runCatching { parseShortsPublishTarget(raw) }.getOrElse {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_INVALID",
                    "게시 대상 형식이 올바르지 않습니다: $raw",
                )
            }

            val channelId = target.channelId
                ?: throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_INVALID",
                    "게시할 채널을 지정해야 합니다. 플랫폼 이름만으로는 어느 계정에 올릴지 알 수 없습니다: $raw",
                )

            val platform = runCatching { Platform.valueOf(target.platformName) }.getOrElse {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_INVALID",
                    "지원하지 않는 플랫폼입니다: ${target.platformName}",
                )
            }

            val channel = channelRepository.findById(channelId)
            /*
             * 남의 채널과 없는 채널을 같은 문구로 거절한다. 나눠 말하면 채널 id 를 넣어
             * 보며 남의 계정 존재 여부를 알아낼 수 있다.
             */
            if (channel == null || channel.userId != userId) {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_INVALID",
                    "연결된 채널을 찾을 수 없습니다: $raw",
                )
            }
            // 키의 플랫폼과 실제 채널의 플랫폼이 다르면 엉뚱한 곳에 올라간다.
            if (channel.platform != platform) {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_INVALID",
                    "채널의 플랫폼이 요청과 다릅니다: $raw",
                )
            }
            if (channel.status != ChannelStatus.ACTIVE) {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_UNAVAILABLE",
                    "사용할 수 없는 채널입니다. 채널 관리에서 상태를 확인해 주세요: $raw",
                )
            }
            val expiresAt = channel.tokenExpiresAt
            if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
                throw BusinessException(
                    "SHORTS_PUBLISH_TARGET_UNAVAILABLE",
                    "채널 인증이 만료되었습니다. 채널 관리에서 다시 연결한 뒤 예약해 주세요: $raw",
                )
            }
        }
    }

    /**
     * 서버 렌더를 사용할 수 없는 환경에서 외부에서 만든 완성 영상을 클립에 연결하는 보완 경로.
     * 일반적인 Compose/쇼츠 실행 흐름은 서버 렌더가 만든 videoId를 자동으로 연결한다.
     */
    @Transactional
    fun attachRenderedVideo(
        userId: Long,
        workspaceId: Long,
        runId: Long,
        clipId: Long,
        videoId: Long,
    ): ShortsClipResponse {
        assertWorkspaceAccess(userId, workspaceId)
        loadRunInWorkspace(workspaceId, runId)

        val clip = shortsClipRepository.findById(clipId)
            ?: throw BusinessException("SHORTS_CLIP_NOT_FOUND", "클립을 찾을 수 없습니다: $clipId")
        if (clip.runId != runId) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "이 실행의 클립이 아닙니다: $clipId")
        }
        if (clip.status == ClipStatus.DISCARDED) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "제외된 클립에는 영상을 연결할 수 없습니다")
        }

        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("SHORTS_SOURCE_VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")
        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "해당 영상에 접근 권한이 없습니다")
        }

        val updated = shortsClipRepository.update(
            clip.copy(renderedVideoId = videoId, status = ClipStatus.RENDERED),
        )

        /*
         * 서버 렌더와 **같은** 사건이다. 사건의 정의가 "서버가 렌더했다"가 아니라
         * "접근 가능한 완성 영상이 클립에 연결됐다"이기 때문이다. 서버 렌더만 세면
         * 이 보완 경로로 결과를 받은 고객이 통째로 빠져 지표가 과소집계된다.
         *
         * 위 검증(워크스페이스 접근·클립 소속·DISCARDED·영상 소유)이 하나라도 실패하면
         * 이미 예외로 빠져나갔으므로 여기 도달하지 못한다. 연결이 성립한 뒤에만 발행된다.
         *
         * 발행만 하고 기록은 커밋 뒤 리스너가 맡는다 — 기록 실패가 이 트랜잭션을
         * 롤백시키면 방금 성립한 연결이 사라진다.
         */
        eventPublisher.publishEvent(
            ShortsClipAvailableEvent(userId = userId, runId = runId, clipId = clipId),
        )

        val hooks = clipHookRepository.findByClipIds(listOf(clipId))
        return updated.toResponse(hooks)
    }

    /** 클립의 render-spec.json 문자열을 반환한다. */
    fun getRenderSpec(userId: Long, workspaceId: Long, runId: Long, clipId: Long): String {
        assertWorkspaceAccess(userId, workspaceId)
        loadRunInWorkspace(workspaceId, runId)
        val clip = shortsClipRepository.findById(clipId)
        if (clip == null || clip.runId != runId || clip.renderSpec == null) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "렌더 스펙을 찾을 수 없습니다: clipId=$clipId")
        }
        return clip.renderSpec!!
    }

    /** 렌더 산출물 3종(render-spec.json, clip-{seq}.ass, render.sh)을 클립별로 묶은 zip을 만든다. */
    fun getRenderBundle(userId: Long, workspaceId: Long, runId: Long): ByteArray {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)

        val clips = shortsClipRepository.findByRunId(runId)
            .filter { it.status != ClipStatus.DISCARDED && it.renderSpec != null }
        if (clips.isEmpty()) {
            throw BusinessException("SHORTS_CLIP_NOT_FOUND", "다운로드할 렌더 산출물이 없습니다")
        }

        val template = resolveTemplate(run)

        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            clips.forEach { clip ->
                /*
                 * 저장된 스펙의 URL 을 그대로 내보내면 사용자가 zip 을 받은 순간 이미 죽은
                 * 주소일 수 있다. 번들은 사용자가 자기 PC 에서 돌리는 물건이라 다운로드
                 * 시점에 살아 있는 주소여야 한다. 그래서 여기서 다시 서명한다.
                 *
                 * DB 의 clip.renderSpec 은 건드리지 않는다. 서명은 어차피 또 만료되므로
                 * 저장해봐야 같은 문제가 반복되고, 쓰기 트랜잭션만 늘어난다.
                 */
                val spec = renderSpecBuilder.parseSpec(clip.renderSpec!!)
                    .let { it.copy(sourceFileUrl = freshSourceUrl(it)) }
                val prefix = "clip-${clip.seq}"

                zip.putNextEntry(ZipEntry("$prefix/render-spec.json"))
                zip.write(renderSpecBuilder.toJson(spec).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$prefix/clip-${clip.seq}.ass"))
                zip.write(renderSpecBuilder.buildAss(spec, template).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("$prefix/render.sh"))
                zip.write(renderSpecBuilder.buildRenderScript(spec).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    /**
     * 번들에 넣을 원본 URL 을 다시 서명한다.
     *
     * 실패하면 **요청 전체를 실패시킨다.** 한 클립이라도 오래된 URL 이 섞이면 사용자는
     * zip 을 받아 풀고 render.sh 를 돌린 뒤에야 403 을 본다. 그때는 무엇이 잘못됐는지
     * 알 수 없고, 이미 시간을 썼다. 지금 실패시키면 다시 누르면 된다.
     *
     * 예외 메시지에 URL·서명·키를 담지 않는다. presigned URL 자체가 자격증명이다.
     */
    private fun freshSourceUrl(spec: ShortsRenderSpecBuilder.RenderSpec): String {
        val storedUrl = spec.sourceFileUrl
        if (storedUrl.isNullOrBlank() || spec.sourceVideoId <= 0) {
            throw BusinessException(
                "SHORTS_SOURCE_URL_REFRESH_FAILED",
                "원본 영상 주소를 갱신하지 못해 렌더 산출물을 만들 수 없습니다. 잠시 후 다시 시도해 주세요.",
            )
        }

        val refreshed = try {
            // 저장 URL 은 legacy 행의 object key 를 되짚는 입력으로만 넘긴다.
            storageService.getFileUrl(spec.sourceVideoId, storedUrl)
        } catch (e: Exception) {
            throw BusinessException(
                "SHORTS_SOURCE_URL_REFRESH_FAILED",
                "원본 영상 주소를 갱신하지 못해 렌더 산출물을 만들 수 없습니다. 잠시 후 다시 시도해 주세요.",
            )
        }

        if (refreshed.isBlank()) {
            throw BusinessException(
                "SHORTS_SOURCE_URL_REFRESH_FAILED",
                "원본 영상 주소를 갱신하지 못해 렌더 산출물을 만들 수 없습니다. 잠시 후 다시 시도해 주세요.",
            )
        }
        return refreshed
    }

    /** 실행 취소/삭제. RUNNING이면 CANCELLED로 표시해 리스너가 협조적 중단하게 한 뒤 삭제한다. */
    @Transactional
    fun deleteRun(userId: Long, workspaceId: Long, runId: Long) {
        assertWorkspaceAccess(userId, workspaceId)
        val run = loadRunInWorkspace(workspaceId, runId)
        if (run.status == PipelineRunStatus.RUNNING) {
            pipelineRunRepository.update(run.copy(status = PipelineRunStatus.CANCELLED))
        }
        pipelineRunRepository.delete(runId)
    }

    // ---- 날부 헬퍼 ----

    private fun assertWorkspaceAccess(userId: Long, workspaceId: Long) {
        val accessible = workspaceRepository.findAccessibleByUserId(userId).any { it.id == workspaceId }
        if (!accessible) throw NotFoundException("워크스페이스", workspaceId)
    }

    private fun normalizeIdempotencyKey(value: String?): String? {
        val key = value?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        require(key.length <= 255 && key.none(Char::isISOControl)) {
            "Idempotency-Key는 1~255자의 제어문자 없는 값이어야 합니다"
        }
        return key
    }

    private fun requestFingerprint(request: CreatePipelineRunRequest): String =
        MessageDigest.getInstance("SHA-256")
            .digest(mapper.writeValueAsBytes(request))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun loadRunInWorkspace(workspaceId: Long, runId: Long): PipelineRun {
        val run = pipelineRunRepository.findById(runId)
            ?: throw BusinessException("SHORTS_RUN_NOT_FOUND", "실행을 찾을 수 없습니다: $runId")
        if (run.workspaceId != workspaceId) {
            throw BusinessException("ACCESS_DENIED", "다른 워크스페이스의 실행입니다")
        }
        return run
    }

    private fun parseStage(stageName: String): PipelineStage =
        runCatching { PipelineStage.valueOf(stageName.uppercase()) }.getOrElse {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "알 수 없는 파이프라인 단계입니다: $stageName")
        }

    private fun resolveTemplate(run: PipelineRun): ShortsTemplate? =
        run.templateId?.let { shortsTemplateRepository.findById(it) }
            ?: shortsTemplateRepository.findByWorkspace(run.workspaceId).firstOrNull { it.isDefault }

    private fun buildDetail(run: PipelineRun): PipelineRunDetailResponse {
        val stages = runStageRepository.findByRunId(run.id)
            .sortedBy { it.stage.sortOrder }
            .map { it.toResponse() }
        val clips = shortsClipRepository.findByRunId(run.id)
        val clipIds = clips.map { it.id }
        val hooksByClip = clipHookRepository.findByClipIds(clipIds).groupBy { it.clipId }
        /*
         * 대상별 게시 결과를 **한 번에** 읽는다. 클립마다 조회하면 클립 수에 비례해 질의가
         * 늘고, 실행 하나에 클립이 여러 개인 것이 정상이라 그 비용이 매번 발생한다.
         */
        val publications = clipPublicationRepository.findByClipIds(clipIds)
        val publicationsByClip = publications.groupBy { it.clipId }
        // 채널 ID가 실제 결과에 있을 때만 조회한다. 렌더 전 상세 조회까지 채널 전체를
        // 읽지 않으며, 실행에 접근할 수 있는 사용자의 채널만 이름 보강에 사용한다.
        val hasChannelTargets = publications.any {
            runCatching { parseShortsPublishTarget(it.platform).channelId }.getOrNull() != null
        }
        val channelsById = if (hasChannelTargets) {
            channelRepository.findByUserId(run.userId)
                .filter { it.workspaceId == null || it.workspaceId == run.workspaceId }
                .mapNotNull { channel -> channel.id?.let { it to channel } }
                .toMap()
        } else {
            emptyMap()
        }
        val video = videoRepository.findById(run.sourceVideoId)
        return PipelineRunDetailResponse(
            run = run.toResponse(video?.title),
            stages = stages,
            clips = clips.map {
                it.toResponse(
                    hooksByClip[it.id].orEmpty(),
                    publicationsByClip[it.id].orEmpty(),
                    channelsById,
                )
            },
        )
    }

    // ---- 매핑 ----

    private fun PipelineRun.toResponse(sourceVideoTitle: String?) = PipelineRunResponse(
        id = id,
        sourceVideoId = sourceVideoId,
        sourceVideoTitle = sourceVideoTitle,
        templateId = templateId,
        status = status.name,
        currentStage = currentStage?.name,
        clipCount = clipCount,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun com.ongo.domain.ugc.shorts.RunStage.toResponse() = RunStageResponse(
        stage = stage.name,
        status = status.name,
        promptId = promptId,
        promptRevision = promptRevision,
        aiProvider = aiProvider,
        creditCost = creditCost,
        errorMessage = errorMessage,
        startedAt = startedAt,
        completedAt = completedAt,
    )

    private fun ShortsClip.toResponse(
        hooks: List<com.ongo.domain.ugc.shorts.ClipHook>,
        publications: List<com.ongo.domain.ugc.shorts.ClipPublication> = emptyList(),
        channelsById: Map<Long, com.ongo.domain.channel.Channel> = emptyMap(),
    ) = ShortsClipResponse(
        id = id,
        seq = seq,
        startMs = startMs,
        endMs = endMs,
        durationMs = endMs - startMs,
        title = title,
        caption = caption,
        status = status.name,
        scheduledAt = scheduledAt,
        hooks = hooks.map {
            ClipHookResponse(id = it.id, variant = it.variant.name, text = it.text, selected = it.selected)
        },
        subtitleCount = countSubtitles(subtitleJson),
        hasRenderSpec = renderSpec != null,
        // 새로고침 뒤에도 화면이 결과물 유무를 알 수 있는 유일한 서버측 근거다.
        renderedVideoId = renderedVideoId,
        publications = publications.map {
            ClipPublicationResponse(
                platform = it.platform,
                channelName = runCatching { parseShortsPublishTarget(it.platform) }.getOrNull()?.let { target ->
                    target.channelId
                        ?.let { channelId -> channelsById[channelId] }
                        ?.takeIf { channel -> channel.platform.name == target.platformName }
                        ?.channelName
                },
                status = it.status.name,
                errorMessage = it.errorMessage,
                scheduledAt = it.scheduledAt,
                publishedAt = it.publishedAt,
            )
        },
    )

    private fun countSubtitles(subtitleJson: String?): Int {
        if (subtitleJson.isNullOrBlank()) return 0
        return runCatching { mapper.readTree(subtitleJson).size() }.getOrDefault(0)
    }

    companion object {
        private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
    }
}
