import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

// 쇼츠 파이프라인 9단계 (백엔드 PipelineStage 와 동일)
export type PipelineStage =
  | 'TRANSCRIBE'
  | 'REFRAME'
  | 'SEGMENT'
  | 'SUBTITLE'
  | 'HOOK'
  | 'TEMPLATE'
  | 'RENDER_SPEC'
  | 'VALIDATE'
  | 'SCHEDULE'

export interface ShortsPromptResponse {
  id: number
  stage: PipelineStage
  name: string
  description: string | null
  systemPrompt: string | null
  userPrompt: string
  executable: boolean
  revision: number
  /** true = 워크스페이스 오버라이드, false = 시스템 기본값 */
  customized: boolean
  /** 복원 미리보기용 시스템 기본값 */
  defaultSystemPrompt: string | null
  defaultUserPrompt: string
  updatedAt: string | null
}

export interface ShortsPromptRevisionResponse {
  revision: number
  systemPrompt: string | null
  userPrompt: string
  changeNote: string | null
  changedBy: number
  createdAt: string
}

export interface UpdateShortsPromptRequest {
  systemPrompt: string | null
  userPrompt: string
  changeNote: string | null
}

const base = (workspaceId: number) => `/workspaces/${workspaceId}/ugc/shorts/prompts`

export const ugcShortsPromptApi = {
  list(workspaceId: number) {
    return apiClient
      .get<ResData<ShortsPromptResponse[]>>(base(workspaceId))
      .then(unwrapResponse)
  },

  get(workspaceId: number, stage: PipelineStage) {
    return apiClient
      .get<ResData<ShortsPromptResponse>>(`${base(workspaceId)}/${stage}`)
      .then(unwrapResponse)
  },

  update(workspaceId: number, stage: PipelineStage, request: UpdateShortsPromptRequest) {
    return apiClient
      .put<ResData<ShortsPromptResponse>>(`${base(workspaceId)}/${stage}`, request)
      .then(unwrapResponse)
  },

  /** 오버라이드 삭제 = 기본값 복원 */
  resetToDefault(workspaceId: number, stage: PipelineStage) {
    return apiClient
      .delete<ResData<ShortsPromptResponse>>(`${base(workspaceId)}/${stage}`)
      .then(unwrapResponse)
  },

  revisions(workspaceId: number, stage: PipelineStage) {
    return apiClient
      .get<ResData<ShortsPromptRevisionResponse[]>>(`${base(workspaceId)}/${stage}/revisions`)
      .then(unwrapResponse)
  },

  restoreRevision(workspaceId: number, stage: PipelineStage, revision: number) {
    return apiClient
      .post<ResData<ShortsPromptResponse>>(
        `${base(workspaceId)}/${stage}/revisions/${revision}/restore`,
      )
      .then(unwrapResponse)
  },
}
