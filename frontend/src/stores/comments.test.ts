import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { commentsApi } from '@/api/comments'
import { useCommentsStore } from './comments'

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn(),
    syncAll: vi.fn(),
  },
}))

describe('comments store plan access handling', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('opens the upgrade state when the API returns the stable plan-limit code', async () => {
    vi.mocked(commentsApi.list).mockRejectedValue({
      response: {
        status: 400,
        data: {
          error: 'PLAN_LIMIT_EXCEEDED',
          message: '댓글 한도를 초과했습니다.',
        },
      },
    })

    const store = useCommentsStore()
    await store.fetchComments()

    expect(store.featureUnavailable).toBe(true)
  })
})
