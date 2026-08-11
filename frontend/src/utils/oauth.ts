import type { Platform } from '@/types/channel'

const REDIRECT_URI_PATH = '/auth/channel-callback'

function base64URLEncode(buffer: Uint8Array): string {
  return btoa(String.fromCharCode(...buffer))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '')
}

function generateCodeVerifier(): string {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  return base64URLEncode(array)
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return base64URLEncode(new Uint8Array(digest))
}

/** PKCE 파라미터를 생성하고 sessionStorage에 verifier를 저장합니다. */
export async function generatePKCE(storageKey: string): Promise<{ verifier: string; challenge: string }> {
  const verifier = generateCodeVerifier()
  const challenge = await generateCodeChallenge(verifier)
  sessionStorage.setItem(storageKey, verifier)
  return { verifier, challenge }
}

/** OAuth callback을 현재 브라우저 세션에서 시작했는지 확인하기 위한 nonce입니다. */
export function generateOAuthStateNonce(storageKey = 'channel_oauth_state_nonce'): string {
  const bytes = new Uint8Array(24)
  crypto.getRandomValues(bytes)
  const nonce = base64URLEncode(bytes)
  sessionStorage.setItem(storageKey, nonce)
  return nonce
}

/**
 * Build the OAuth authorization URL for a given platform.
 * The `state` parameter encodes `PLATFORM|returnPath` so the callback
 * view can route the user back after the token exchange.
 */
export function buildOAuthUrl(platform: Platform, returnPath: string, codeChallenge?: string, stateNonce?: string, addAsNew = false): string {
    const redirectUri = `${window.location.origin}${REDIRECT_URI_PATH}`
  const state = `${platform}|${returnPath}${stateNonce ? `|${stateNonce}` : ''}${addAsNew ? '|new' : ''}`

  switch (platform) {
    case 'YOUTUBE':
      return `https://accounts.google.com/o/oauth2/v2/auth?${new URLSearchParams({
        client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'https://www.googleapis.com/auth/youtube',
        access_type: 'offline',
        // 채널 연동은 로그인과 반대로 consent 를 강제해야 한다.
        // 구글은 access_type=offline 만으로는 **최초 동의 때만** 리프레시 토큰을 준다.
        // 예약 게시와 지표 동기화는 사용자가 접속해 있지 않을 때 도는 작업이라
        // 리프레시 토큰이 없으면 액세스 토큰 만료와 함께 채널이 죽는다.
        // 그러면 한 번 연동했던 계정을 다시 연동할 때 조용히 재발한다.
        prompt: 'consent',
        state,
      })}`

    case 'TIKTOK':
      return `https://www.tiktok.com/v2/auth/authorize/?${new URLSearchParams({
        client_key: import.meta.env.VITE_TIKTOK_CLIENT_KEY || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        // Direct Post uses video.publish. Keep the upload/list scopes used by
        // the draft flow as well so the browser request matches the backend.
        scope: 'video.publish,video.upload,video.list',
        state,
      })}`

    case 'INSTAGRAM':
      return `https://api.instagram.com/oauth/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_INSTAGRAM_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'instagram_basic,instagram_content_publish',
        state,
      })}`

    case 'NAVER_CLIP':
      return `https://nid.naver.com/oauth2.0/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_NAVER_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        state,
      })}`

    case 'TWITTER': {
      if (!codeChallenge) {
        throw new Error('Twitter OAuth requires PKCE code_challenge. Use generatePKCE() first.')
      }
      return `https://twitter.com/i/oauth2/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_TWITTER_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'tweet.read tweet.write users.read offline.access',
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
        state,
      })}`
    }

    case 'FACEBOOK':
      return `https://www.facebook.com/v21.0/dialog/oauth?${new URLSearchParams({
        client_id: import.meta.env.VITE_FACEBOOK_APP_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'pages_manage_posts,pages_read_engagement,pages_show_list',
        state,
      })}`

    case 'THREADS':
      return `https://threads.net/oauth/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_THREADS_APP_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'threads_basic,threads_content_publish,threads_manage_insights',
        state,
      })}`

    case 'PINTEREST':
      return `https://www.pinterest.com/oauth/?${new URLSearchParams({
        client_id: import.meta.env.VITE_PINTEREST_APP_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'boards:read,boards:write,pins:read,pins:write',
        state,
      })}`

    case 'LINKEDIN':
      return `https://www.linkedin.com/oauth/v2/authorization?${new URLSearchParams({
        client_id: import.meta.env.VITE_LINKEDIN_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'openid profile w_member_social',
        state,
      })}`

    case 'WORDPRESS':
      return `https://public-api.wordpress.com/oauth2/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_WORDPRESS_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'global',
        state,
      })}`

    case 'DAILYMOTION':
      return `https://api.dailymotion.com/oauth/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_DAILYMOTION_API_KEY || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'video.manage video.read account.read offline',
        state,
      })}`

    case 'VIMEO':
      return `https://api.vimeo.com/oauth/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_VIMEO_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'public private upload edit',
        state,
      })}`

    case 'TUMBLR':
      return `https://www.tumblr.com/oauth2/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_TUMBLR_CONSUMER_KEY || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'basic write offline_access',
        state,
      })}`

    default:
      throw new Error(`Unsupported platform for OAuth: ${platform}`)
  }
}

/** The redirect URI sent to the backend for token exchange (must match the one used in the auth URL). */
export function getOAuthRedirectUri(): string {
  return `${window.location.origin}${REDIRECT_URI_PATH}`
}
