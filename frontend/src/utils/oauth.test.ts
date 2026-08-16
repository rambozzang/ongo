import { beforeEach, describe, expect, it } from 'vitest'
import { buildOAuthState, generateOAuthStateNonce, getOAuthRedirectUri } from './oauth'

describe('channel OAuth state contracts', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { location: { origin: 'https://ongo.test' } },
    })
    sessionStorage.clear()
  })

  it('encodes the callback destination and optional new-channel mode', () => {
    expect(buildOAuthState('YOUTUBE', '/channels-v2', 'nonce', true)).toBe('YOUTUBE|/channels-v2|nonce|new')
    expect(buildOAuthState('TIKTOK', '/onboarding')).toBe('TIKTOK|/onboarding')
  })

  it('stores and carries a per-session callback nonce', () => {
    const nonce = generateOAuthStateNonce()

    expect(sessionStorage.getItem('channel_oauth_state_nonce')).toBe(nonce)
    expect(buildOAuthState('YOUTUBE', '/channels-v2', nonce)).toBe(`YOUTUBE|/channels-v2|${nonce}`)
  })

  it('returns the callback URI sent to the server-owned authorization builder', () => {
    expect(getOAuthRedirectUri()).toBe('https://ongo.test/auth/channel-callback')
  })
})
