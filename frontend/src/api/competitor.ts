import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ChannelLookupRequest,
  ChannelLookupResponse,
  CompetitorListResponse,
  CompetitorResponse,
  CreateCompetitorRequest,
  UpdateCompetitorRequest,
} from '@/types/competitor'

export const competitorApi = {
  list() {
    return apiClient
      .get<ResData<CompetitorListResponse>>('/competitors')
      .then(unwrapResponse)
  },

  add(request: CreateCompetitorRequest) {
    return apiClient
      .post<ResData<CompetitorResponse>>('/competitors', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateCompetitorRequest) {
    return apiClient
      .put<ResData<CompetitorResponse>>(`/competitors/${id}`, request)
      .then(unwrapResponse)
  },

  remove(id: number) {
    return apiClient
      .delete<ResData<null>>(`/competitors/${id}`)
      .then(unwrapResponse)
  },

  lookup(request: ChannelLookupRequest) {
    return apiClient
      .post<ResData<ChannelLookupResponse>>('/competitors/lookup', request)
      .then(unwrapResponse)
  },
}
