export type Platform =
  | 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'
  | 'TWITTER' | 'FACEBOOK' | 'THREADS' | 'PINTEREST' | 'LINKEDIN'
  | 'WORDPRESS' | 'TUMBLR' | 'VIMEO' | 'DAILYMOTION'

export type TokenStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'DISCONNECTED'

export interface Channel {
  id: number
  platform: Platform
  channelName: string
  channelUrl: string | null
  profileImageUrl: string | null
  /**
   * 구독자(팔로워) 수. **그 플랫폼이 조회하지 않으면 `null`.**
   *
   * Threads·LinkedIn 어댑터는 팔로워 수를 묻지도 않고 `0` 을 저장하고, Naver Clip 은
   * 채널 조회 자체가 없다. 서버가 이제 그 자리를 `null` 로 준다 — `?? 0` 으로 채우면
   * 화면이 **"구독자 0명"** 을 측정 결과로 그린다.
   *
   * 조회하는 플랫폼의 `0` 은 관측이므로 숫자 `0` 으로 그린다.
   */
  subscriberCount: number | null
  status: string
  tokenStatus: TokenStatus
  connectedAt: string
  lastSyncedAt: string | null
  tokenExpiresAt: string | null
}

export interface ChannelConnectRequest {
  authorizationCode: string
  redirectUri: string
  /** 서버가 사용자·플랫폼·redirect URI에 바인딩한 OAuth state */
  state: string
  /** Twitter OAuth 2.0 PKCE code_verifier (Twitter 연동 시 필수) */
  codeVerifier?: string
  /** 같은 플랫폼의 다른 계정을 추가 연결할 때 사용 */
  addAsNew?: boolean
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
