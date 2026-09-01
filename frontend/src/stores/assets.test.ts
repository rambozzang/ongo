import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { assetsApi } from '@/api/assets'
import { subscriptionApi } from '@/api/subscription'
import { useAssetsStore } from './assets'

vi.mock('@/api/assets', () => ({
  assetsApi: {
    list: vi.fn(),
    upload: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('@/api/subscription', () => ({
  subscriptionApi: {
    getUsage: vi.fn(),
  },
}))

describe('assets storage usage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(assetsApi.list).mockResolvedValue({ assets: [], totalCount: 0 })
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 0,
      storageUsedMb: 512,
      storageLimitBytes: 50 * 1024 * 1024 * 1024,
    })
  })

  it('uses the server-measured usage and plan limit instead of a local asset-page estimate', async () => {
    const store = useAssetsStore()
    await store.fetchStorageUsage()

    expect(store.storageUsed).toBe(512 * 1024 * 1024)
    expect(store.storageLimit).toBe(50 * 1024 * 1024 * 1024)
    expect(store.storageUsageError).toBeNull()
  })

  it('does not display a guessed zero usage when the measurement request fails', async () => {
    vi.mocked(subscriptionApi.getUsage).mockRejectedValue(new Error('usage unavailable'))
    const store = useAssetsStore()

    await store.fetchStorageUsage()

    expect(store.storageUsed).toBeNull()
    expect(store.storageLimit).toBeNull()
    expect(store.storageUsageError).toBe('usage unavailable')
    expect(store.storageUsageLoading).toBe(false)
  })
})
