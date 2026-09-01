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

  it('clears the stale upgrade state when a later fetch succeeds', async () => {
    vi.mocked(commentsApi.list).mockResolvedValue({
      comments: [],
      totalCount: 0,
      stats: { total: 0, positive: 0, neutral: 0, negative: 0 },
      capabilities: {},
    })

    const store = useCommentsStore()
    store.featureUnavailable = true

    await store.fetchComments()

    expect(store.featureUnavailable).toBe(false)
  })
})
