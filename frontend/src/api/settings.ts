import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface NotificationSettings {
  uploadEmail: boolean
  commentFrequency: string
  creditThreshold: number
  scheduleReminderMinutes: 0 | 30 | 60
}

export interface DefaultSettings {
  visibility: string
  platforms: string[]
  aiTone: string
  aiProvider: string
}

export interface UserSettingsResponse {
  defaultVisibility: string
  defaultPlatforms: string[]
  defaultAiTone: string
  defaultAiProvider: string
  notificationUpload: boolean
  notificationComment: string
  notificationCreditThreshold: number
  notificationScheduleReminder: number
}

export interface ApiKey {
  id: number
  name: string
  keyPrefix: string
  token?: string | null
  lastUsedAt: string | null
  expiresAt: string | null
  revokedAt: string | null
  createdAt: string | null
}

export const settingsApi = {
  getSettings() {
    return apiClient
      .get<ResData<UserSettingsResponse>>('/settings')
      .then(unwrapResponse)
  },

  updateNotifications(settings: NotificationSettings) {
    return apiClient
      .put<ResData<UserSettingsResponse>>('/settings/notifications', {
        uploadEmail: settings.uploadEmail,
        // Push delivery is not configured by this endpoint yet; do not expose
        // a control that would appear persisted while being ignored by the API.
        uploadPush: true,
        commentFrequency: settings.commentFrequency,
        creditThreshold: settings.creditThreshold,
        scheduleReminder1h: settings.scheduleReminderMinutes === 60,
        scheduleReminder30m: settings.scheduleReminderMinutes === 30,
      })
      .then(unwrapResponse)
  },

  updateDefaults(settings: DefaultSettings) {
    return apiClient
      .put<ResData<void>>('/settings/defaults', settings)
      .then(unwrapResponse)
  },

  listApiKeys() {
    return apiClient
      .get<ResData<ApiKey[]>>('/settings/api-keys')
      .then(unwrapResponse)
  },

  createApiKey(request: { name: string; expiresAt?: string }) {
    return apiClient
      .post<ResData<ApiKey>>('/settings/api-keys', request)
      .then(unwrapResponse)
  },

  revokeApiKey(id: number) {
    return apiClient
      .delete<ResData<void>>(`/settings/api-keys/${id}`)
      .then(unwrapResponse)
  },
}
