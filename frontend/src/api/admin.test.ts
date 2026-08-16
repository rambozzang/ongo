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

  it('loads account deletion jobs and posts an explicit retry command', async () => {
    await adminApi.getAccountDeletionJobs()
    expect(get).toHaveBeenCalledWith('/admin/account-deletion/jobs', { params: { limit: 100 } })

    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { success: true, data: {} } } as never)
    await adminApi.retryAccountDeletion(7)
    expect(post).toHaveBeenCalledWith('/admin/account-deletion/jobs/7/retry')
  })
})
