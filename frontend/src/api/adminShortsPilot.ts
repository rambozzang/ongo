import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { ShortsPilotEnrollmentResponse, ShortsPilotReport } from '@/types/adminShortsPilot'

export const adminShortsPilotApi = {
  getReport(): Promise<ShortsPilotReport> {
    return apiClient.get<ResData<ShortsPilotReport>>('/admin/shorts-pilot/report').then(unwrapResponse)
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

  logOperatorTime(runId: number, minutes: number): Promise<void> {
    return apiClient
      .post<ResData<void>>(`/admin/shorts-pilot/runs/${runId}/operator-time`, { minutes })
      .then(unwrapResponse)
  },
}
