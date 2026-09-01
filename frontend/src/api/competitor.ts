import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ChannelLookupRequest,
  ChannelLookupResponse,
  CompetitorListResponse,
  CompetitorResponse,
  CompetitorSyncResponse,
  CompetitorTrendResponse,
  BenchmarkResponse,
  CompetitorInsightResult,
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

  trends(competitorIds: number[] = [], days = 30) {
    return apiClient
      .post<ResData<CompetitorTrendResponse[]>>('/competitors/trends', { competitorIds, days })
      .then(unwrapResponse)
  },

  benchmark() {
    return apiClient
      .get<ResData<BenchmarkResponse>>('/competitors/benchmark')
      .then(unwrapResponse)
  },

  // 백엔드 CompetitorController.POST /competitors/sync 와 1:1 계약.
  // unwrapResponse 로 실제 동기화 결과(CompetitorSyncResponse)를 그대로 돌려준다.
  // 응답의 message(안내 문구)는 서버가 수치로 만드므로, 화면은 lastSync 수치로
  // 동일한 문구를 구성한다 — 성공을 무조건 "완료"로 치지 않는다.
  sync() {
    return apiClient
      .post<ResData<CompetitorSyncResponse>>('/competitors/sync')
      .then(unwrapResponse)
  },

  insight() {
    return apiClient
      .post<ResData<CompetitorInsightResult>>('/ai/competitor-insight')
      .then(unwrapResponse)
  },
}
