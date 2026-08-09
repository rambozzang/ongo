import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface PublicOAuthApp {
  id: number
  clientId: string
  name: string
  description: string | null
  profilePictureUrl: string | null
  redirectUri: string
  revokedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface PublicOAuthAppCreated {
  app: PublicOAuthApp
  clientSecret: string
}

export interface PublicOAuthTokenSummary {
  id: number
  appId: number
  tokenPrefix: string
  createdAt: string | null
  revokedAt: string | null
}

export interface PublicOAuthAuthorizationRequest {
  clientId: string
  name: string
  description: string | null
  profilePictureUrl: string | null
  redirectUri: string
}

export const oauthApi = {
  listApps() {
    return apiClient.get<ResData<PublicOAuthApp[]>>('/settings/oauth-apps').then(unwrapResponse)
  },

  createApp(request: { name: string; description?: string; profilePictureUrl?: string; redirectUri: string }) {
    return apiClient.post<ResData<PublicOAuthAppCreated>>('/settings/oauth-apps', request).then(unwrapResponse)
  },

  rotateSecret(id: number) {
    return apiClient.post<ResData<PublicOAuthAppCreated>>(`/settings/oauth-apps/${id}/rotate-secret`).then(unwrapResponse)
  },

  deleteApp(id: number) {
    return apiClient.delete<ResData<void>>(`/settings/oauth-apps/${id}`).then(unwrapResponse)
  },

  listTokens() {
    return apiClient.get<ResData<PublicOAuthTokenSummary[]>>('/settings/oauth-apps/tokens').then(unwrapResponse)
  },

  revokeToken(id: number) {
    return apiClient.delete<ResData<void>>(`/settings/oauth-apps/tokens/${id}`).then(unwrapResponse)
  },

  getAuthorizationRequest(params: { clientId: string; responseType: string }) {
    return apiClient.get<PublicOAuthAuthorizationRequest>('/oauth/authorize/request', { params }).then((response) => response.data)
  },

  decideAuthorization(request: { clientId: string; responseType: string; state?: string; approved: boolean }) {
    return apiClient.post<{ redirectUrl: string }>('/oauth/authorize/decision', request).then((response) => response.data)
  },
}
