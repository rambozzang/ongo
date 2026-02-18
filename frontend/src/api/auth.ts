import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { LoginRequest, LoginResponse, AuthTokens, User, CreatorCategory } from '@/types/user'

export const authApi = {
  login(provider: 'google' | 'kakao', request: LoginRequest) {
    return apiClient
      .post<ResData<LoginResponse>>(`/auth/login/${provider}`, request)
      .then(unwrapResponse)
  },

  refresh(refreshToken: string) {
    return apiClient
      .post<ResData<AuthTokens>>('/auth/refresh', { refreshToken })
      .then(unwrapResponse)
  },

  getProfile() {
    return apiClient.get<ResData<User>>('/auth/profile').then(unwrapResponse)
  },

  updateProfile(data: { nickname: string; category: CreatorCategory | null }) {
    return apiClient
      .put<ResData<User>>('/auth/profile', data)
      .then(unwrapResponse)
  },

  completeOnboarding() {
    return apiClient
      .post<ResData<void>>('/auth/onboarding/complete')
      .then(unwrapResponse)
  },

  devLogin() {
    return apiClient
      .post<ResData<LoginResponse>>('/auth/dev-login')
      .then(unwrapResponse)
  },

  deleteAccount() {
    return apiClient
      .delete<ResData<void>>('/auth/account')
      .then(unwrapResponse)
  },

  /** SSE 연결용 단기 토큰 발급 (5분 만료) */
  getSseToken() {
    return apiClient
      .post<ResData<{ token: string }>>('/auth/sse-token')
      .then(unwrapResponse)
  },
}
