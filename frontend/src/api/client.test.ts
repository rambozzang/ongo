import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'

const unauthorizedAdapter = async (
  config: InternalAxiosRequestConfig,
): Promise<AxiosResponse> => {
  throw new AxiosError(
    'Unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    {
      data: { success: false, message: null, data: null, error: 'login failed' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    },
  )
}

describe('API authentication retry policy', () => {
  let originalAdapter: typeof apiClient.defaults.adapter

  beforeEach(() => {
    originalAdapter = apiClient.defaults.adapter
    apiClient.defaults.adapter = unauthorizedAdapter
    localStorage.setItem('accessToken', 'stale-access-token')
    localStorage.setItem('refreshToken', 'refresh-token')
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    localStorage.clear()
    sessionStorage.clear()
    vi.restoreAllMocks()
  })

  it('does not refresh a failed social login request', async () => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.post('/auth/login/google', { code: 'bad-code' })).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it('does not refresh a failed dev login request', async () => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.post('/auth/dev-login', { email: 'dev@example.com' })).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it.each([
    ['/auth/google/state', 'Google OAuth state request'],
    ['/auth/kakao/state', 'Kakao OAuth state request'],
    ['/auth/refresh', 'token refresh request'],
  ])('does not recursively refresh a failed %s', async (path) => {
    const refresh = vi.spyOn(axios, 'post')

    await expect(apiClient.get(path)).rejects.toMatchObject({
      response: { status: 401 },
    })

    expect(refresh).not.toHaveBeenCalled()
  })

  it('still refreshes a protected request after a 401 response', async () => {
    let attempts = 0
    apiClient.defaults.adapter = async (config) => {
      if (attempts++ === 0) {
        return unauthorizedAdapter(config)
      }
      return {
        data: { success: true, message: null, data: { id: 1 }, error: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      }
    }
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        success: true,
        message: null,
        data: { accessToken: 'new-access-token', refreshToken: 'new-refresh-token' },
        error: null,
      },
    } as AxiosResponse)

    const response = await apiClient.get('/auth/profile')

    expect(response.status).toBe(200)
    expect(refresh).toHaveBeenCalledOnce()
    expect(localStorage.getItem('accessToken')).toBe('new-access-token')
  })
})
