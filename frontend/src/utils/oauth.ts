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

/** State shared by the provider and the callback view. */
export function buildOAuthState(
  platform: Platform,
  returnPath: string,
  stateNonce?: string,
  addAsNew = false,
): string {
  return `${platform}|${returnPath}${stateNonce ? `|${stateNonce}` : ''}${addAsNew ? '|new' : ''}`
}

/** The redirect URI sent to the server for token exchange. */
export function getOAuthRedirectUri(): string {
  return `${window.location.origin}${REDIRECT_URI_PATH}`
}
