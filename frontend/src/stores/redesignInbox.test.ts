import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import { useRedesignInboxStore } from './redesignInbox'

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn(),
  },
}))

vi.mock('@/api/inbox', () => ({
  inboxApi: {
    listMessages: vi.fn(),
  },
}))

const comment = {
  id: 1,
  platform: 'YOUTUBE',
  authorName: '시청자',
  authorAvatarUrl: null,
  content: '질문입니다?',
  videoId: 10,
  sentiment: 'neutral',
  isReplied: false,
  isPinned: false,
  likeCount: 0,
  replyContent: null,
  repliedAt: null,
  publishedAt: '2026-08-10T09:00:00Z',
  createdAt: '2026-08-10T09:00:00Z',
} as never

describe('redesign inbox store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('preserves the last confirmed source when a refresh partially fails', async () => {
    vi.mocked(commentsApi.list).mockResolvedValueOnce({ comments: [comment] } as never).mockRejectedValueOnce(new Error('offline'))
    vi.mocked(inboxApi.listMessages).mockResolvedValue({ messages: [] } as never)
    const store = useRedesignInboxStore()

    await store.fetchAll()
    await store.fetchAll()

    expect(store.threads).toHaveLength(1)
    expect(store.threads[0].id).toBe('c-1')
    expect(store.loadError).toBe('loadPartial')
  })
})
