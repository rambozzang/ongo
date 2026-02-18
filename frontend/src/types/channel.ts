export type Platform =
  | 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'
  | 'TWITTER' | 'FACEBOOK' | 'THREADS' | 'PINTEREST' | 'LINKEDIN'
  | 'WORDPRESS' | 'TUMBLR' | 'VIMEO' | 'DAILYMOTION'

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
  authorizationCode: string
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
  TWITTER: { label: 'X (Twitter)', color: '#000000', icon: 'twitter' },
  FACEBOOK: { label: 'Facebook', color: '#1877F2', icon: 'facebook' },
  THREADS: { label: 'Threads', color: '#000000', icon: 'threads' },
  PINTEREST: { label: 'Pinterest', color: '#E60023', icon: 'pinterest' },
  LINKEDIN: { label: 'LinkedIn', color: '#0A66C2', icon: 'linkedin' },
  WORDPRESS: { label: 'WordPress', color: '#21759B', icon: 'wordpress' },
  TUMBLR: { label: 'Tumblr', color: '#36465D', icon: 'tumblr' },
  VIMEO: { label: 'Vimeo', color: '#1AB7EA', icon: 'vimeo' },
  DAILYMOTION: { label: 'Dailymotion', color: '#00D2F3', icon: 'dailymotion' },
}
