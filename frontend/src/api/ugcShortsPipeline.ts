import apiClient, { unwrapResponse } from './client'
import type { ResData, PageResponse } from '@/types/api'
import type { PipelineStage } from '@/api/ugcShortsPrompt'

// Phase 1 에서 정의한 9단계 타입을 그대로 재사용한다
export type { PipelineStage } from '@/api/ugcShortsPrompt'

export type PipelineRunStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'AWAITING_HOOK_SELECTION'
  | 'AWAITING_SCHEDULE'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export type RunStageStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'

export type RenderJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface RenderAvailabilityResponse {
  available: boolean
  reason: string | null
}

export interface StartRenderResponse {
  renderJobId: string
}

export interface RenderJobStatusResponse {
  status: RenderJobStatus
  progress: number | null
  videoId: number | null
  failureReason: string | null
}

export type HookVariant = 'A' | 'B' | 'CUSTOM'

// 설계 5장의 TypeScript 인터페이스와 필드명이 정확히 일치해야 한다
export interface PipelineRunResponse {
  id: number
  sourceVideoId: number
  sourceVideoTitle: string | null
  templateId: number | null
  status: PipelineRunStatus
  currentStage: PipelineStage | null
  clipCount: number
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface RunStageResponse {
  stage: PipelineStage
  status: RunStageStatus
  promptId: number | null
  promptRevision: number | null
  aiProvider: string | null
  creditCost: number
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface ClipHookResponse {
  id: number
  variant: HookVariant
  text: string
  selected: boolean
}

export interface ShortsClipResponse {
  id: number
  seq: number
  startMs: number
  endMs: number
  durationMs: number
  title: string | null
  caption: string | null
  status: string
  scheduledAt: string | null
  hooks: ClipHookResponse[]
  subtitleCount: number
  hasRenderSpec: boolean
}

export interface PipelineRunDetailResponse {
  run: PipelineRunResponse
  stages: RunStageResponse[]
  clips: ShortsClipResponse[]
}

export interface CreatePipelineRunRequest {
  sourceVideoId: number
  templateId?: number | null
  /** 브라우저 없이 서버가 후킹·렌더·예약 게시까지 이어서 처리한다. */
  autoSchedule?: boolean
  scheduleStartAt?: string | null
  scheduleIntervalHours?: number | null
  /** PLATFORM 또는 PLATFORM#channelId */
  platforms?: string[]
}

export interface HookSelectionItem {
  clipId: number
  variant: HookVariant
  customText?: string
}

export interface SelectHooksRequest {
  selections: HookSelectionItem[]
  discardClipIds: number[]
}

export interface ScheduleRunRequest {
  startAt: string
  intervalHours: number
  /** PLATFORM or PLATFORM#channelId for a specific connected account. */
  platforms: string[]
}

const base = (workspaceId: number) => `/workspaces/${workspaceId}/ugc/shorts/runs`

export const ugcShortsPipelineApi = {
  create(workspaceId: number, request: CreatePipelineRunRequest, idempotencyKey?: string) {
    const response = idempotencyKey
      ? apiClient.post<ResData<PipelineRunResponse>>(base(workspaceId), request, {
          headers: { 'Idempotency-Key': idempotencyKey },
        })
      : apiClient.post<ResData<PipelineRunResponse>>(base(workspaceId), request)
    return response
      .then(unwrapResponse)
  },

  list(workspaceId: number, page: number, size: number) {
    return apiClient
      .get<ResData<PageResponse<PipelineRunResponse>>>(base(workspaceId), {
        params: { page, size },
      })
      .then(unwrapResponse)
  },

  get(workspaceId: number, runId: number) {
    return apiClient
      .get<ResData<PipelineRunDetailResponse>>(`${base(workspaceId)}/${runId}`)
      .then(unwrapResponse)
  },

  /** 해당 단계부터 재실행 — 이후 단계 결과는 서버에서 무효화된다 */
  rerunStage(workspaceId: number, runId: number, stage: PipelineStage) {
    return apiClient
      .post<ResData<PipelineRunResponse>>(`${base(workspaceId)}/${runId}/stages/${stage}/rerun`)
      .then(unwrapResponse)
  },

  /** 후킹 일괄 선택 — 선택하지 않은 클립은 discardClipIds 로 제외 */
  selectHooks(workspaceId: number, runId: number, request: SelectHooksRequest) {
    return apiClient
      .post<ResData<PipelineRunResponse>>(`${base(workspaceId)}/${runId}/hooks`, request)
      .then(unwrapResponse)
  },

  confirmSchedule(workspaceId: number, runId: number, request: ScheduleRunRequest) {
    return apiClient
      .post<ResData<PipelineRunResponse>>(`${base(workspaceId)}/${runId}/schedule`, request)
      .then(unwrapResponse)
  },

  /** 클립별 render-spec.json 다운로드 */
  downloadRenderSpec(workspaceId: number, runId: number, clipId: number): Promise<Blob> {
    return apiClient
      .get(`${base(workspaceId)}/${runId}/clips/${clipId}/render-spec`, { responseType: 'blob' })
      .then((r) => r.data as Blob)
  },

  /** render-spec + ass + render.sh 3종 zip 다운로드 */
  downloadRenderBundle(workspaceId: number, runId: number): Promise<Blob> {
    return apiClient
      .get(`${base(workspaceId)}/${runId}/render-bundle`, { responseType: 'blob' })
      .then((r) => r.data as Blob)
  },

  /** 사용자가 render.sh 로 만든 완성 영상을 클립에 연결한다 — 연결돼야 게시 대상이 된다 */
  attachRenderedVideo(workspaceId: number, runId: number, clipId: number, videoId: number) {
    return apiClient
      .post<ResData<ShortsClipResponse>>(
        `${base(workspaceId)}/${runId}/clips/${clipId}/rendered-video`,
        { videoId },
      )
      .then(unwrapResponse)
  },

  remove(workspaceId: number, runId: number) {
    return apiClient
      .delete<ResData<void>>(`${base(workspaceId)}/${runId}`)
      .then(unwrapResponse)
  },

  /** ffmpeg 렌더 가용성 조회 — 미가용이면 진입점을 감춘다 */
  getRenderAvailability() {
    return apiClient
      .get<ResData<RenderAvailabilityResponse>>('/ugc/shorts/render/availability')
      .then(unwrapResponse)
  },

  /** 클립 서버 렌더 시작 — 202 Accepted */
  startRender(workspaceId: number, runId: number, clipId: number) {
    return apiClient
      .post<ResData<StartRenderResponse>>(
        `${base(workspaceId)}/${runId}/clips/${clipId}/render`,
        {},
      )
      .then(unwrapResponse)
  },

  /** 클립 렌더 상태 조회 — 폴링용 */
  getRenderStatus(workspaceId: number, runId: number, clipId: number) {
    return apiClient
      .get<ResData<RenderJobStatusResponse>>(
        `${base(workspaceId)}/${runId}/clips/${clipId}/render`,
      )
      .then(unwrapResponse)
  },
}
