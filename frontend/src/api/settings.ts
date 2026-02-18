import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface NotificationSettings {
  uploadEmail: boolean
  uploadPush: boolean
  commentFrequency: string
  creditThreshold: number
  scheduleReminder1h: boolean
  scheduleReminder30m: boolean
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

export const settingsApi = {
  getSettings() {
    return apiClient
      .get<ResData<UserSettingsResponse>>('/settings')
      .then(unwrapResponse)
  },

  updateNotifications(settings: NotificationSettings) {
    return apiClient
      .put<ResData<void>>('/settings/notifications', settings)
      .then(unwrapResponse)
  },

  updateDefaults(settings: DefaultSettings) {
    return apiClient
      .put<ResData<void>>('/settings/defaults', settings)
      .then(unwrapResponse)
  },
}
