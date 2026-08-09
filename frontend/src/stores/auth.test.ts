import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authApi } from '@/api/auth'
import { POST_LOGIN_REDIRECT_KEY, useAuthStore } from './auth'
import type { User } from '@/types/user'

const mocks = vi.hoisted(() => ({ routerPush: vi.fn() }))

vi.mock('@/api/auth', () => ({
  authApi: {
    login: vi.fn(),
    devLogin: vi.fn(),
    getProfile: vi.fn(),
    logout: vi.fn(),
  },
}))

vi.mock('@/router', () => ({ default: { push: mocks.routerPush } }))

const user: User = {
  id: 1,
  email: 'creator@example.com',
  name: 'Creator',
  nickname: 'Creator',
  profileImageUrl: null,
  category: 'IT',
  planType: 'FREE',
  role: 'USER',
  onboardingCompleted: true,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
}

describe('auth deep-link handoff', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
    mocks.routerPush.mockReset()
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'access',
      refreshToken: 'refresh',
      user,
      isNewUser: false,
    })
  })

  it('returns to the OAuth consent page after social login', async () => {
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, '/oauth/authorize?client_id=pca_demo&state=s1')
    const store = useAuthStore()

    await store.login('google', { code: 'code', redirectUri: '/auth/callback/google', state: 's1' })

    expect(mocks.routerPush).toHaveBeenCalledWith('/oauth/authorize?client_id=pca_demo&state=s1')
    expect(sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY)).toBeNull()
  })

  it('rejects an external post-login redirect and uses the normal landing page', async () => {
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, 'https://attacker.example/phish')
    const store = useAuthStore()

    await store.login('google', { code: 'code', redirectUri: '/auth/callback/google', state: 's1' })

    expect(mocks.routerPush).toHaveBeenCalledWith('/today')
  })
})
