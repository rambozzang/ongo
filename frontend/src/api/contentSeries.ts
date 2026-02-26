import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ContentSeries,
  SeriesEpisode,
  SeriesAnalytics,
  CreateSeriesRequest,
} from '@/types/contentSeries'

export const contentSeriesApi = {
  getAll: () =>
    apiClient.get<ResData<ContentSeries[]>>('/content-series').then(unwrapResponse),

  getById: (id: number) =>
    apiClient.get<ResData<ContentSeries>>(`/content-series/${id}`).then(unwrapResponse),

  create: (request: CreateSeriesRequest) =>
    apiClient.post<ResData<ContentSeries>>('/content-series', request).then(unwrapResponse),

  update: (id: number, data: Partial<ContentSeries>) =>
    apiClient.put<ResData<ContentSeries>>(`/content-series/${id}`, data).then(unwrapResponse),

  delete: (id: number) =>
    apiClient.delete<ResData<void>>(`/content-series/${id}`).then(unwrapResponse),

  addEpisode: (seriesId: number, episode: Partial<SeriesEpisode>) =>
    apiClient.post<ResData<SeriesEpisode>>(`/content-series/${seriesId}/episodes`, episode).then(unwrapResponse),

  updateEpisode: (seriesId: number, episodeId: number, data: Partial<SeriesEpisode>) =>
    apiClient.put<ResData<SeriesEpisode>>(`/content-series/${seriesId}/episodes/${episodeId}`, data).then(unwrapResponse),

  deleteEpisode: (seriesId: number, episodeId: number) =>
    apiClient.delete<ResData<void>>(`/content-series/${seriesId}/episodes/${episodeId}`).then(unwrapResponse),

  getAnalytics: (seriesId: number) =>
    apiClient.get<ResData<SeriesAnalytics>>(`/content-series/${seriesId}/analytics`).then(unwrapResponse),
}
