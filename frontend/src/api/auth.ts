import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { LoginRequest, LoginResponse, AuthTokens, User, CreatorCategory } from '@/types/user'

export interface AccountDeletionStatus {
  state: string
  status: string | null
  jobId: number | null
  requestedAt: string | null
  updatedAt: string | null
  completedAt: string | null
  lastErrorCode: string | null
  supportReference: string | null
  retryable: boolean
}

export const authApi = {
  getOAuthState(provider: 'google' | 'kakao') {
    return apiClient
      .get<ResData<{ state: string }>>(`/auth/${provider}/state`)
      .then(unwrapResponse)
  },

  authorizationUrl(provider: 'google' | 'kakao', redirectUri: string) {
    return apiClient
      .get<ResData<{ authorizationUrl: string; state: string }>>(`/auth/${provider}/authorization-url`, {
        params: { redirectUri },
      })
      .then(unwrapResponse)
  },

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

  /** 서버 측 무효화 — access token 블랙리스트 등록 + refresh token 전량 삭제 */
  logout() {
    return apiClient.post<ResData<void>>('/auth/logout').then(unwrapResponse)
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

  getAccountDeletionStatus() {
    return apiClient
      .get<ResData<AccountDeletionStatus>>('/auth/account/deletion-status')
      .then(unwrapResponse)
  },

  /** SSE 연결용 단기 토큰 발급 (5분 만료) */
  getSseToken() {
    return apiClient
      .post<ResData<{ token: string }>>('/auth/sse-token')
      .then(unwrapResponse)
  },
}
