import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface AppCapability {
  key: string
  enabled: boolean
  reason?: string | null
}

let cachedCapabilities: AppCapability[] | null = null
let pendingRequest: Promise<AppCapability[]> | null = null

export const capabilitiesApi = {
  list(options?: { force?: boolean }) {
    if (!options?.force && cachedCapabilities) return Promise.resolve(cachedCapabilities)
    if (!options?.force && pendingRequest) return pendingRequest

    pendingRequest = apiClient.get<ResData<AppCapability[]>>('/capabilities')
      .then(unwrapResponse)
      .then((items) => {
        cachedCapabilities = items
        return items
      })
      .finally(() => {
        pendingRequest = null
      })

    return pendingRequest
  },

  clearCache() {
    cachedCapabilities = null
  },
}
