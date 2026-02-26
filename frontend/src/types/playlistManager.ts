export type PlaylistPlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'
export type PlaylistVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE'

export interface Playlist {
  id: number
  title: string
  description?: string
  platform: PlaylistPlatform
  platformPlaylistId?: string
  visibility: PlaylistVisibility
  thumbnailUrl?: string
  videoCount: number
  totalViews: number
  totalDuration: number
  tags: string[]
  isSynced: boolean
  lastSyncedAt?: string
  createdAt: string
  updatedAt: string
}

export interface PlaylistVideo {
  id: number
  playlistId: number
  videoId: string
  title: string
  thumbnailUrl?: string
  duration: number
  views: number
  position: number
  addedAt: string
}

export interface PlaylistSummary {
  totalPlaylists: number
  totalVideos: number
  totalViews: number
  platformBreakdown: { platform: PlaylistPlatform; count: number }[]
  topPlaylists: { id: number; title: string; views: number; platform: PlaylistPlatform }[]
}

export interface CreatePlaylistRequest {
  title: string
  description?: string
  platform: PlaylistPlatform
  visibility: PlaylistVisibility
  tags?: string[]
}

export interface AddVideoToPlaylistRequest {
  playlistId: number
  videoId: string
  position?: number
}

export interface ReorderPlaylistRequest {
  playlistId: number
  videoIds: string[]
}
