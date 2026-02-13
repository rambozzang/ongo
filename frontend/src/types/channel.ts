export type Platform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'

export type TokenStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED'

export interface Channel {
  id: number
  userId: number
  platform: Platform
  channelId: string
  channelName: string
  channelUrl: string | null
  profileImageUrl: string | null
  subscriberCount: number
  tokenStatus: TokenStatus
  connectedAt: string
  lastSyncedAt: string | null
}

export interface ChannelConnectRequest {
  code: string
  redirectUri: string
}

export const PLATFORM_CONFIG: Record<
  Platform,
  { label: string; color: string; icon: string }
> = {
  YOUTUBE: { label: 'YouTube', color: '#FF0000', icon: 'youtube' },
  TIKTOK: { label: 'TikTok', color: '#000000', icon: 'tiktok' },
  INSTAGRAM: { label: 'Instagram', color: '#E1306C', icon: 'instagram' },
  NAVER_CLIP: { label: 'Naver Clip', color: '#03C75A', icon: 'naver' },
}
