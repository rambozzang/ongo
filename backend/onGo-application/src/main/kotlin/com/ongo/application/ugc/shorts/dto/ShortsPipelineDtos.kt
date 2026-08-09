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
