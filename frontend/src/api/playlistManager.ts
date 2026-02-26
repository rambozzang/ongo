import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Playlist,
  PlaylistVideo,
  PlaylistSummary,
  CreatePlaylistRequest,
  AddVideoToPlaylistRequest,
  ReorderPlaylistRequest,
} from '@/types/playlistManager'

export const playlistManagerApi = {
  getPlaylists: (platform?: string) =>
    apiClient.get<ResData<Playlist[]>>('/playlists', { params: { platform } }).then(unwrapResponse),

  getPlaylist: (id: number) =>
    apiClient.get<ResData<Playlist>>(`/playlists/${id}`).then(unwrapResponse),

  createPlaylist: (request: CreatePlaylistRequest) =>
    apiClient.post<ResData<Playlist>>('/playlists', request).then(unwrapResponse),

  updatePlaylist: (id: number, data: Partial<Playlist>) =>
    apiClient.put<ResData<Playlist>>(`/playlists/${id}`, data).then(unwrapResponse),

  deletePlaylist: (id: number) =>
    apiClient.delete<ResData<void>>(`/playlists/${id}`).then(unwrapResponse),

  getVideos: (playlistId: number) =>
    apiClient.get<ResData<PlaylistVideo[]>>(`/playlists/${playlistId}/videos`).then(unwrapResponse),

  addVideo: (request: AddVideoToPlaylistRequest) =>
    apiClient.post<ResData<PlaylistVideo>>(`/playlists/${request.playlistId}/videos`, request).then(unwrapResponse),

  removeVideo: (playlistId: number, videoId: string) =>
    apiClient.delete<ResData<void>>(`/playlists/${playlistId}/videos/${videoId}`).then(unwrapResponse),

  reorderVideos: (request: ReorderPlaylistRequest) =>
    apiClient.put<ResData<void>>(`/playlists/${request.playlistId}/videos/reorder`, request).then(unwrapResponse),

  syncPlaylist: (id: number) =>
    apiClient.post<ResData<Playlist>>(`/playlists/${id}/sync`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<PlaylistSummary>>('/playlists/summary').then(unwrapResponse),
}
