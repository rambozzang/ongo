import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { FanPoll, PollSummary, PollCreateRequest } from '@/types/fanPoll'

export const fanPollApi = {
  getSummary: () =>
    apiClient.get<ResData<PollSummary>>('/fan-poll/summary').then(unwrapResponse),

  getPolls: (status?: string) =>
    apiClient.get<ResData<FanPoll[]>>('/fan-poll', { params: { status } }).then(unwrapResponse),

  getPoll: (id: number) =>
    apiClient.get<ResData<FanPoll>>(`/fan-poll/${id}`).then(unwrapResponse),

  createPoll: (request: PollCreateRequest) =>
    apiClient.post<ResData<FanPoll>>('/fan-poll', request).then(unwrapResponse),

  closePoll: (id: number) =>
    apiClient.post<ResData<FanPoll>>(`/fan-poll/${id}/close`).then(unwrapResponse),

  deletePoll: (id: number) =>
    apiClient.delete<ResData<void>>(`/fan-poll/${id}`).then(unwrapResponse),
}
