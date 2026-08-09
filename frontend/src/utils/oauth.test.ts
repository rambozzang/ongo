import { beforeEach, describe, expect, it } from 'vitest'
import type { Platform } from '@/types/channel'
import { buildOAuthUrl, generateOAuthStateNonce, getOAuthRedirectUri } from './oauth'

describe('channel OAuth contracts', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { location: { origin: 'https://ongo.test' } },
    })
    sessionStorage.clear()
  })

  it.each([
    ['YOUTUBE', 'accounts.google.com'],
    ['TIKTOK', 'www.tiktok.com'],
    ['INSTAGRAM', 'api.instagram.com'],
    ['NAVER_CLIP', 'nid.naver.com'],
    ['FACEBOOK', 'www.facebook.com'],
    ['THREADS', 'threads.net'],
    ['PINTEREST', 'www.pinterest.com'],
    ['LINKEDIN', 'www.linkedin.com'],
    ['WORDPRESS', 'public-api.wordpress.com'],
    ['DAILYMOTION', 'api.dailymotion.com'],
    ['VIMEO', 'api.vimeo.com'],
    ['TUMBLR', 'www.tumblr.com'],
  ] as const)('%s builds a provider authorization URL', (platform, host) => {
    const url = new URL(buildOAuthUrl(platform as Platform, '/channels'))
    expect(url.hostname).toBe(host)
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('redirect_uri')).toBe('https://ongo.test/auth/channel-callback')
    expect(url.searchParams.get('state')).toBe(`${platform}|/channels`)
  })

  it('requests Dailymotion scopes needed for upload and lifecycle checks', () => {
    const url = new URL(buildOAuthUrl('DAILYMOTION', '/channels'))
    expect(url.searchParams.get('scope')).toContain('video.manage')
    expect(url.searchParams.get('scope')).toContain('video.read')
  })

  it('requires PKCE for X and includes the verifier challenge', () => {
    expect(() => buildOAuthUrl('TWITTER', '/channels')).toThrow('PKCE')
    const url = new URL(buildOAuthUrl('TWITTER', '/channels', 'challenge'))
    expect(url.hostname).toBe('twitter.com')
    expect(url.searchParams.get('code_challenge')).toBe('challenge')
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')
  })

  it('stores and carries a per-session callback nonce', () => {
    const nonce = generateOAuthStateNonce()
    const url = new URL(buildOAuthUrl('YOUTUBE', '/channels-v2', undefined, nonce))

    expect(sessionStorage.getItem('channel_oauth_state_nonce')).toBe(nonce)
    expect(url.searchParams.get('state')).toBe(`YOUTUBE|/channels-v2|${nonce}`)
  })

  it('marks an OAuth connection as a new account when requested', () => {
    const url = new URL(buildOAuthUrl('YOUTUBE', '/channels-v2', undefined, 'nonce', true))
    expect(url.searchParams.get('state')).toBe('YOUTUBE|/channels-v2|nonce|new')
  })

  it('returns the same callback URI sent to the provider', () => {
    expect(getOAuthRedirectUri()).toBe('https://ongo.test/auth/channel-callback')
  })
})
