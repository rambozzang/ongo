import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { adminApi } from './admin'

describe('admin operations contracts', () => {
  const get = vi.spyOn(apiClient, 'get')

  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({ data: { success: true, data: { totalPending: 0, items: [] } } } as never)
  })

  it('loads the publish worker queue from the protected server endpoint', async () => {
    await adminApi.getPublishQueue()

    expect(get).toHaveBeenCalledWith('/admin/publish-queue')
  })
})
