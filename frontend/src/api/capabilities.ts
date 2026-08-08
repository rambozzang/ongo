import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface AppCapability {
  key: string
  enabled: boolean
}

export const capabilitiesApi = {
  list() {
    return apiClient.get<ResData<AppCapability[]>>('/capabilities').then(unwrapResponse)
  },
}
