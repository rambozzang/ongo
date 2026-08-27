import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ShortsPilotCandidatePage,
  ShortsPilotEnrollmentResponse,
  ShortsPilotEntryListResponse,
  ShortsPilotReport,
  ShortsPilotReversalResponse,
} from '@/types/adminShortsPilot'

export const adminShortsPilotApi = {
  getReport(): Promise<ShortsPilotReport> {
    return apiClient.get<ResData<ShortsPilotReport>>('/admin/shorts-pilot/report').then(unwrapResponse)
  },

  /** 아직 코호트에 없는 최근 실행. 등록된 실행은 서버가 제외해 보낸다. */
  getCandidates(page = 0, size = 20): Promise<ShortsPilotCandidatePage> {
    return apiClient
      .get<ResData<ShortsPilotCandidatePage>>('/admin/shorts-pilot/runs/candidates', {
        params: { page, size },
      })
      .then(unwrapResponse)
  },

  enroll(runId: number): Promise<ShortsPilotEnrollmentResponse> {
    return apiClient
      .post<ResData<ShortsPilotEnrollmentResponse>>(`/admin/shorts-pilot/runs/${runId}/enrollment`)
      .then(unwrapResponse)
  },

  logRevenue(runId: number, amountKrw: number): Promise<void> {
    return apiClient
      .post<ResData<void>>(`/admin/shorts-pilot/runs/${runId}/revenue`, { amountKrw })
      .then(unwrapResponse)
  },

  logExternalCost(runId: number, amountKrw: number): Promise<void> {
    return apiClient
      .post<ResData<void>>(`/admin/shorts-pilot/runs/${runId}/external-cost`, { amountKrw })
      .then(unwrapResponse)
  },

  /** 이 실행의 수기 기록 목록. 취소된 기록도 isReversed=true 로 함께 온다. */
  getEntries(runId: number): Promise<ShortsPilotEntryListResponse> {
    return apiClient
      .get<ResData<ShortsPilotEntryListResponse>>(`/admin/shorts-pilot/runs/${runId}/entries`)
      .then(unwrapResponse)
  },

  /** 잘못 입력한 기록 하나를 무효화한다. 원본은 지워지지 않고 합계에서만 빠진다. */
  reverseEntry(runId: number, entryId: number): Promise<ShortsPilotReversalResponse> {
    return apiClient
      .post<ResData<ShortsPilotReversalResponse>>(
        `/admin/shorts-pilot/runs/${runId}/entries/${entryId}/reversal`,
      )
      .then(unwrapResponse)
  },

  logOperatorTime(runId: number, minutes: number): Promise<void> {
    return apiClient
      .post<ResData<void>>(`/admin/shorts-pilot/runs/${runId}/operator-time`, { minutes })
      .then(unwrapResponse)
  },
}
