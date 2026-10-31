import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ClipEditResponse } from './ugcShortsPipeline'

/** 화자 추적 결과. 크레딧은 적용됐을 때만 차감된다(실패하면 서버가 돌려준다). */
export interface SpeakerTrackingResponse {
  edit: ClipEditResponse
  foundTiles: number
  totalTiles: number
  creditsCharged: number
}

const base = (workspaceId: number, runId: number, clipId: number) =>
  `/workspaces/${workspaceId}/ugc/shorts/runs/${runId}/clips/${clipId}/track-speaker`

export const ugcShortsTrackingApi = {
  cost(workspaceId: number, runId: number, clipId: number) {
    return apiClient
      .get<ResData<{ credits: number }>>(`${base(workspaceId, runId, clipId)}/cost`)
      .then(unwrapResponse)
  },

  track(workspaceId: number, runId: number, clipId: number) {
    // 장면 추출 + 비전 모델 호출이라 일반 요청보다 오래 걸린다.
    return apiClient
      .post<ResData<SpeakerTrackingResponse>>(base(workspaceId, runId, clipId), null, { timeout: 120_000 })
      .then(unwrapResponse)
  },
}
