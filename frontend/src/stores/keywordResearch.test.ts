import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useKeywordResearchStore } from './keywordResearch'
import { keywordResearchApi } from '@/api/keywordResearch'
import { useNotificationStore } from '@/stores/notification'

vi.mock('@/api/keywordResearch', () => ({
  keywordResearchApi: { research: vi.fn(), getHistory: vi.fn() },
}))

vi.mock('@/stores/notification', () => ({
  useNotificationStore: vi.fn(() => ({ error: vi.fn() })),
}))

describe('keyword research store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('stores server research results and resets the researching state', async () => {
    const result = { keyword: '브이로그', platforms: ['YOUTUBE'], relatedKeywords: ['일상'] }
    vi.mocked(keywordResearchApi.research).mockResolvedValue(result as never)
    const store = useKeywordResearchStore()

    await expect(store.research('브이로그', ['YOUTUBE'] as never)).resolves.toEqual(result)
    expect(store.currentResult).toEqual(result)
    expect(store.researching).toBe(false)
  })

  it('notifies on research failure without leaving stale results', async () => {
    const notify = vi.fn()
    vi.mocked(useNotificationStore).mockReturnValue({ error: notify } as never)
    vi.mocked(keywordResearchApi.research).mockRejectedValue({ response: { data: { message: '분석 장애' } } })
    const store = useKeywordResearchStore()

    await expect(store.research('실패', [] as never)).resolves.toBeNull()
    expect(notify).toHaveBeenCalledWith('분석 장애')
    expect(store.currentResult).toBeNull()
    expect(store.researching).toBe(false)
  })

  it('loads history, computes pagination, and handles a missing history endpoint', async () => {
    vi.mocked(keywordResearchApi.getHistory).mockResolvedValue({
      items: [{ id: 1, keyword: '브이로그' }], totalCount: 25,
    } as never)
    const store = useKeywordResearchStore()
    await store.fetchHistory()

    expect(keywordResearchApi.getHistory).toHaveBeenCalledWith(1, 10)
    expect(store.history).toHaveLength(1)
    expect(store.totalPages).toBe(3)
    expect(store.hasNextPage).toBe(true)
    expect(store.hasPrevPage).toBe(true)
    expect(store.loading).toBe(false)

    vi.mocked(keywordResearchApi.getHistory).mockRejectedValueOnce({ response: { status: 404 } })
    await store.fetchHistory()
    expect(store.history).toEqual([])
    expect(store.totalCount).toBe(0)
    expect(store.historyError).toBeNull()
  })

  it('reports history failures and only moves within available pages', async () => {
    const notify = vi.fn()
    vi.mocked(useNotificationStore).mockReturnValue({ error: notify } as never)
    vi.mocked(keywordResearchApi.getHistory).mockRejectedValue({ message: '이력 장애' })
    const store = useKeywordResearchStore()
    await store.fetchHistory()
    expect(store.historyError).toBe('이력 장애')
    expect(notify).toHaveBeenCalledWith('이력 장애')

    store.totalCount = 30
    store.page = 1
    vi.mocked(keywordResearchApi.getHistory).mockResolvedValue({ items: [], totalCount: 30 } as never)
    store.nextPage()
    await vi.waitFor(() => expect(store.page).toBe(2))
    store.prevPage()
    await vi.waitFor(() => expect(store.page).toBe(1))
  })
})
