import type { Platform } from '@/types/channel'

const REDIRECT_URI_PATH = '/auth/channel-callback'

/**
 * Build the OAuth authorization URL for a given platform.
 * The `state` parameter encodes `PLATFORM|returnPath` so the callback
 * view can route the user back after the token exchange.
 */
export function buildOAuthUrl(platform: Platform, returnPath: string): string {
  const redirectUri = `${window.location.origin}${REDIRECT_URI_PATH}`
  const state = `${platform}|${returnPath}`

  switch (platform) {
    case 'YOUTUBE':
      return `https://accounts.google.com/o/oauth2/v2/auth?${new URLSearchParams({
        client_id: import.meta.env.VITE_GOOGLE_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'https://www.googleapis.com/auth/youtube',
        access_type: 'offline',
        state,
      })}`

    case 'TIKTOK':
      return `https://www.tiktok.com/v2/auth/authorize/?${new URLSearchParams({
        client_key: import.meta.env.VITE_TIKTOK_CLIENT_KEY || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'video.upload,video.list',
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

    case 'TWITTER':
      return `https://twitter.com/i/oauth2/authorize?${new URLSearchParams({
        client_id: import.meta.env.VITE_TWITTER_CLIENT_ID || '',
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: 'tweet.read tweet.write users.read offline.access',
        code_challenge: 'challenge',
        code_challenge_method: 'plain',
        state,
      })}`

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

    default:
      throw new Error(`Unsupported platform for OAuth: ${platform}`)
  }
}

/** The redirect URI sent to the backend for token exchange (must match the one used in the auth URL). */
export function getOAuthRedirectUri(): string {
  return `${window.location.origin}${REDIRECT_URI_PATH}`
}
