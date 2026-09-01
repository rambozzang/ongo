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

  it('loads dead-letter webhooks from the admin-only endpoint with a bounded limit', async () => {
    await adminApi.getDeadLetterWebhooks()

    expect(get).toHaveBeenCalledWith('/admin/webhooks/dead-letters', { params: { limit: 50 } })
  })

  /**
   * 요청 본문을 보내면 임의 상태 전이 경로가 열린다. 허용되는 전이는 서버가 정한
   * DEAD_LETTER → FAILED 하나뿐이므로 대상만 지정한다.
   */
  it('requeues one dead-letter webhook by surrogate id and sends no body', async () => {
    const post = vi
      .spyOn(apiClient, 'post')
      .mockResolvedValue({ data: { success: true, data: { id: 7, status: 'FAILED' } } } as never)

    await adminApi.requeueDeadLetterWebhook(7)

    expect(post).toHaveBeenCalledWith('/admin/webhooks/dead-letters/7/requeue')
    expect(post.mock.calls[0]).toHaveLength(1)
  })
})
