import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

// 백엔드 SheetDiffRow / SheetPreviewResponse 와 필드명이 일치해야 한다
export interface SheetDiffRow {
  clipId: number
  seq: number
  field: 'title' | 'hookText' | 'caption' | 'scheduledAt'
  before: string | null
  after: string | null
}

export interface SheetPreviewResponse {
  rows: SheetDiffRow[]
  unknownClipIds: number[]
  invalidRows: string[]
}

const base = (workspaceId: number) => `/workspaces/${workspaceId}/ugc/shorts/runs`

function uploadSheet(workspaceId: number, runId: number, step: 'preview' | 'apply', file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient
    .post<ResData<SheetPreviewResponse>>(`${base(workspaceId)}/${runId}/sheet/${step}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then(unwrapResponse)
}

export const ugcShortsSheetApi = {
  /** 예약표 .xlsx 다운로드 */
  downloadSheet(workspaceId: number, runId: number): Promise<Blob> {
    return apiClient
      .get(`${base(workspaceId)}/${runId}/sheet`, { responseType: 'blob' })
      .then((r) => r.data as Blob)
  },

  /** 가져오기 1단계 — 변경 diff만 받는다. 서버는 DB를 건드리지 않는다 */
  previewSheet(workspaceId: number, runId: number, file: File) {
    return uploadSheet(workspaceId, runId, 'preview', file)
  },

  /** 가져오기 2단계 — 미리보기에서 확인한 파일을 실제로 반영한다 */
  applySheet(workspaceId: number, runId: number, file: File) {
    return uploadSheet(workspaceId, runId, 'apply', file)
  },
}
