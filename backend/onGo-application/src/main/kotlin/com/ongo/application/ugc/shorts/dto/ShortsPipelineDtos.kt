package com.ongo.application.ugc.shorts.dto

import com.ongo.domain.ugc.shorts.HookVariant
import java.time.Instant

/**
 * 쇼츠 파이프라인 실행 응답.
 * 프론트엔드 `PipelineRunResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class PipelineRunResponse(
    val id: Long,
    val sourceVideoId: Long,
    val sourceVideoTitle: String?,
    val templateId: Long?,
    val status: String,
    val currentStage: String?,
    val clipCount: Int,
    val errorMessage: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

/** 실행 목록 페이지 응답. */
data class PipelineRunListResponse(
    val runs: List<PipelineRunResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
)

/**
 * 단계 실행 기록 응답.
 * 프론트엔드 `RunStageResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class RunStageResponse(
    val stage: String,
    val status: String,
    val promptId: Long?,
    val promptRevision: Int?,
    val aiProvider: String?,
    val creditCost: Int,
    val errorMessage: String?,
    val startedAt: Instant?,
    val completedAt: Instant?,
)

/**
 * 후킹 문구 응답.
 * 프론트엔드 `ClipHookResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class ClipHookResponse(
    val id: Long,
    val variant: String,
    val text: String,
    val selected: Boolean,
)

/**
 * 클립 응답.
 * 프론트엔드 `ShortsClipResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class ShortsClipResponse(
    val id: Long,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val title: String?,
    val caption: String?,
    val status: String,
    val scheduledAt: Instant?,
    val hooks: List<ClipHookResponse>,
    val subtitleCount: Int,
    val hasRenderSpec: Boolean,
    /**
     * 이 클립에 연결된 완성 영상의 id. 연결 전에는 `null` 이다.
     *
     * 서버 렌더와 외부 완성본 연결 둘 다 이 값을 채운다. 화면은 이 값 하나로 "결과물이
     * 있다"를 판정할 수 있어야 한다 — 렌더 job 상태는 브라우저 세션에 남는 진행 정보라
     * 새로고침하면 사라지지만, 이 값은 서버 응답에 있으므로 다시 그릴 수 있다.
     */
    val renderedVideoId: Long?,
    /**
     * 이 클립의 **대상별 게시 결과**. 렌더 전이거나 게시를 요청한 적이 없으면 빈 배열이다.
     *
     * nullable 이 아니라 빈 배열인 이유: 화면이 "결과가 없다"와 "필드를 못 받았다"를
     * 구분할 필요가 없다. 둘 다 보여줄 대상이 없다는 같은 뜻이다.
     */
    val publications: List<ClipPublicationResponse> = emptyList(),
)

/**
 * 대상 하나의 게시 결과.
 *
 * 클립 상태(`SCHEDULED`)는 **대상 하나라도** 성공하면 붙는다. 그래서 이 목록 없이는
 * 3개 중 1개만 성공한 클립과 3개 모두 성공한 클립을 구분할 수 없다.
 *
 * 외부 게시물 URL 은 담지 않는다. 저장하는 곳이 없어 만들어 내야 하는데, 그러면 열지도
 * 못하는 링크를 "게시 완료"의 증거처럼 보여주게 된다.
 */
data class ClipPublicationResponse(
    /** `PLATFORM` 또는 `PLATFORM#channelId`. 같은 플랫폼의 계정을 구분하는 키다. */
    val platform: String,
    /** 연결된 채널이 아직 사용자 소유로 남아 있을 때만 표시되는 이름. */
    val channelName: String? = null,
    val status: String,
    val errorMessage: String?,
    val scheduledAt: Instant?,
    val publishedAt: Instant?,
)

/**
 * 실행 상세 응답 (단계 + 클립 + 후킹 포함).
 * 프론트엔드 `PipelineRunDetailResponse` 인터페이스와 필드명이 일치해야 한다.
 */
data class PipelineRunDetailResponse(
    val run: PipelineRunResponse,
    val stages: List<RunStageResponse>,
    val clips: List<ShortsClipResponse>,
)

/** 실행 생성 요청. */
data class CreatePipelineRunRequest(
    val sourceVideoId: Long,
    val templateId: Long? = null,
    /** true면 후킹 선택·렌더·예약 확정까지 서버 워커가 이어서 처리한다. */
    val autoSchedule: Boolean = false,
    val scheduleStartAt: Instant? = null,
    val scheduleIntervalHours: Int? = null,
    val platforms: List<String> = emptyList(),
)

/** 후킹 선택 하나. CUSTOM이면 customText가 필요하다. */
data class HookSelection(
    val clipId: Long,
    val variant: HookVariant,
    val customText: String? = null,
)

/** 후킹 일괄 선택 요청. */
data class HookSelectionRequest(
    val selections: List<HookSelection>,
    val discardClipIds: List<Long> = emptyList(),
)

/** 예약 확정 요청. */
data class ScheduleConfirmRequest(
    val startAt: Instant,
    val intervalHours: Int,
    val platforms: List<String> = emptyList(),
)

/** 렌더 완성 영상 연결 요청. 업로드가 끝난 영상의 id 를 넘긴다. */
data class AttachRenderedVideoRequest(
    val videoId: Long,
)
