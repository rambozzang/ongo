import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import { INBOX_BATCH_MAX, INBOX_PAGE_SIZE, useRedesignInboxStore } from './redesignInbox'

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn(),
    hide: vi.fn(),
    reply: vi.fn(),
    batchHide: vi.fn(),
    batchReply: vi.fn(),
    batchAiDraft: vi.fn(),
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

  // ── 일괄 처리 · 검색 · 초안 ─────────────────────────────────────────

  function commentWith(id: number, extra: Record<string, unknown> = {}) {
    return { ...(comment as object), id, content: `댓글 ${id}`, ...extra } as never
  }

  async function loadedStore(ids: number[], total = ids.length) {
    vi.mocked(commentsApi.list).mockResolvedValue({ comments: ids.map((id) => commentWith(id)), totalCount: total } as never)
    vi.mocked(inboxApi.listMessages).mockResolvedValue({ messages: [] } as never)
    const store = useRedesignInboxStore()
    await store.fetchAll()
    return store
  }

  /** 서버는 감정을 NEGATIVE 처럼 대문자로 준다. 예전에는 부정 필터가 한 번도 맞지 않았다. */
  it('counts upper-case NEGATIVE sentiment as negative', async () => {
    vi.mocked(commentsApi.list).mockResolvedValue({ comments: [commentWith(1, { sentiment: 'NEGATIVE' })], totalCount: 1 } as never)
    vi.mocked(inboxApi.listMessages).mockResolvedValue({ messages: [] } as never)
    const store = useRedesignInboxStore()

    await store.fetchAll()

    expect(store.filterCounts.negative).toBe(1)
  })

  /** 예전에는 일부가 실패해도 선택한 전부를 처리 완료로 표시했다(가짜 성공). */
  it('marks only the succeeded comments as done and keeps failures checked with a reason', async () => {
    const store = await loadedStore([1, 2, 3])
    store.toggleCheckAll()
    vi.mocked(commentsApi.batchHide).mockResolvedValue({
      successCount: 1, failCount: 2, totalRequested: 3,
      items: [
        { commentId: 1, success: true, errorCode: null, message: null, retryable: false },
        { commentId: 2, success: false, errorCode: 'PLATFORM_API_FAILED', message: '일시 오류', retryable: true },
        { commentId: 3, success: false, errorCode: 'FORBIDDEN', message: '권한 없음', retryable: false },
      ],
    })

    const outcome = await store.batchHideChecked()

    expect(outcome).toEqual({ succeeded: 1, failed: 2 })
    expect(store.threads.find((t) => t.id === 'c-1')?.answered).toBe(true)
    expect(store.threads.find((t) => t.id === 'c-2')?.answered).toBe(false)
    expect([...store.checkedIds].sort()).toEqual(['c-2', 'c-3'])
    expect(store.failures['c-2']).toMatchObject({ errorCode: 'PLATFORM_API_FAILED', retryable: true })
    expect(store.retryableFailures.map((t) => t.id)).toEqual(['c-2'])
  })

  it('retries only the retryable failures with the same reply text', async () => {
    const store = await loadedStore([1, 2, 3])
    store.toggleCheckAll()
    vi.mocked(commentsApi.batchReply).mockResolvedValueOnce({
      successCount: 0, failCount: 3, totalRequested: 3,
      items: [
        { commentId: 1, success: false, errorCode: 'PLATFORM_API_FAILED', message: '일시', retryable: true },
        { commentId: 2, success: false, errorCode: 'RATE_LIMITED', message: '속도', retryable: true },
        { commentId: 3, success: false, errorCode: 'COMMENT_REPLY_UNSUPPORTED', message: '미지원', retryable: false },
      ],
    })
    await store.batchReplyChecked('  고마워요  ')
    vi.mocked(commentsApi.batchReply).mockResolvedValueOnce({
      successCount: 2, failCount: 0, totalRequested: 2,
      items: [
        { commentId: 1, success: true, errorCode: null, message: null, retryable: false },
        { commentId: 2, success: true, errorCode: null, message: null, retryable: false },
      ],
    })

    const outcome = await store.retryFailed()

    expect(commentsApi.batchReply).toHaveBeenLastCalledWith([1, 2], '고마워요')
    expect(outcome).toEqual({ succeeded: 2, failed: 0 })
    expect(store.threads.find((t) => t.id === 'c-1')?.replyContent).toBe('고마워요')
    expect(store.failures['c-3']?.retryable).toBe(false)
  })

  it('refuses an empty batch reply and more than the server batch limit without calling the server', async () => {
    const store = await loadedStore(Array.from({ length: INBOX_BATCH_MAX + 1 }, (_, i) => i + 1))
    store.toggleCheckAll()

    await expect(store.batchReplyChecked('   ')).rejects.toThrow('답변')
    await expect(store.batchHideChecked()).rejects.toThrow(`${INBOX_BATCH_MAX}`)
    expect(commentsApi.batchReply).not.toHaveBeenCalled()
    expect(commentsApi.batchHide).not.toHaveBeenCalled()
  })

  /** 처리 완료는 숨김으로 정한다 — 토글이면 이미 숨긴 댓글이 도로 보인다. */
  it('hides a single thread explicitly instead of toggling', async () => {
    const store = await loadedStore([1])
    vi.mocked(commentsApi.hide).mockResolvedValue({} as never)

    await store.hideThread(store.threads[0])

    expect(commentsApi.hide).toHaveBeenCalledWith(1, true)
  })

  /** AI 초안은 검토 대기열에만 들어간다. 사용자가 고쳐서 보낸 것만 나가고, 실패한 초안은 사유와 함께 남는다. */
  it('queues AI drafts for review and sends only what the user keeps, keeping failed drafts', async () => {
    const store = await loadedStore([1, 2])
    store.toggleCheckAll()
    vi.mocked(commentsApi.batchAiDraft).mockResolvedValue({
      drafts: [
        { commentId: 1, commentContent: '댓글 1', draftReply: '초안 1', tone: 'FRIENDLY' },
        { commentId: 2, commentContent: '댓글 2', draftReply: '초안 2', tone: 'FRIENDLY' },
      ],
      totalCreditsUsed: 6,
    })

    const created = await store.generateDrafts()
    expect(created).toEqual({ created: 2, requested: 2, creditsUsed: 6 })
    expect(commentsApi.reply).not.toHaveBeenCalled()

    store.updateDraft('c-1', '고친 초안 1')
    vi.mocked(commentsApi.reply)
      .mockResolvedValueOnce({} as never)
      .mockRejectedValueOnce({ response: { status: 502, data: { error: 'PLATFORM_API_FAILED', message: '플랫폼 오류' } } })

    const outcome = await store.sendDrafts()

    expect(commentsApi.reply).toHaveBeenNthCalledWith(1, 1, '고친 초안 1')
    expect(outcome).toEqual({ succeeded: 1, failed: 1 })
    expect(store.pendingDrafts.map((d) => d.thread.id)).toEqual(['c-2'])
    expect(store.failures['c-2']).toMatchObject({ errorCode: 'PLATFORM_API_FAILED', message: '플랫폼 오류', retryable: true })
  })

  it('searches on the server and appends the next page without duplicates', async () => {
    const store = await loadedStore([1, 2], 3)
    expect(store.hasMoreComments).toBe(true)

    vi.mocked(commentsApi.list).mockResolvedValueOnce({ comments: [commentWith(2), commentWith(3)], totalCount: 3 } as never)
    await store.loadMoreComments()
    expect(store.threads.map((t) => t.id)).toEqual(['c-1', 'c-2', 'c-3'])
    expect(store.hasMoreComments).toBe(false)

    vi.mocked(commentsApi.list).mockResolvedValueOnce({ comments: [commentWith(9)], totalCount: 1 } as never)
    await store.setQuery({ search: ' 환불 ', platform: 'YOUTUBE' })
    expect(commentsApi.list).toHaveBeenLastCalledWith({ page: 0, size: INBOX_PAGE_SIZE, searchText: '환불', platform: 'YOUTUBE' })
    expect(store.threads.map((t) => t.id)).toEqual(['c-9'])
  })

  // ── 혼합 동작에서 재시도가 다른 동작을 보내지 않는다 ────────────────

  const ok = (commentId: number) => ({ commentId, success: true, errorCode: null, message: null, retryable: false })
  const transient = (commentId: number) => ({ commentId, success: false, errorCode: 'PLATFORM_API_FAILED', message: '일시', retryable: true })

  /**
   * 회귀: 일괄 숨김에서 실패한 A 가 남은 채 B 에 일괄 답변이 성공하면, 예전 재시도는 lastBatch(답변)로
   * A 에게 B 의 답변을 **실제로 게시**했다. 이전 동작의 실패는 재시도 대상이 아니다.
   */
  it('never sends a later reply to a comment that failed an earlier hide', async () => {
    const store = await loadedStore([1, 2])
    store.toggleChecked('c-1')
    vi.mocked(commentsApi.batchHide).mockResolvedValueOnce({ successCount: 0, failCount: 1, totalRequested: 1, items: [transient(1)] })
    await store.batchHideChecked()

    store.toggleChecked('c-1')
    store.toggleChecked('c-2')
    vi.mocked(commentsApi.batchReply).mockResolvedValueOnce({ successCount: 1, failCount: 0, totalRequested: 1, items: [ok(2)] })
    await store.batchReplyChecked('B 에게만 보낸 답변')

    expect(store.retryableFailures).toEqual([])
    const outcome = await store.retryFailed()

    expect(outcome).toEqual({ succeeded: 0, failed: 0 })
    expect(commentsApi.batchReply).toHaveBeenCalledTimes(1)
    expect(commentsApi.batchHide).toHaveBeenCalledTimes(1)
    // 이전 실패는 사유로 남는다 — 사라지지 않는다.
    expect(store.failures['c-1']?.batch.kind).toBe('hide')
  })

  it('retries only the last batch failures with that batch action and text', async () => {
    const store = await loadedStore([1, 2])
    store.toggleChecked('c-1')
    vi.mocked(commentsApi.batchHide).mockResolvedValueOnce({ successCount: 0, failCount: 1, totalRequested: 1, items: [transient(1)] })
    await store.batchHideChecked()

    store.toggleChecked('c-1')
    store.toggleChecked('c-2')
    vi.mocked(commentsApi.batchReply)
      .mockResolvedValueOnce({ successCount: 0, failCount: 1, totalRequested: 1, items: [transient(2)] })
      .mockResolvedValueOnce({ successCount: 1, failCount: 0, totalRequested: 1, items: [ok(2)] })
    await store.batchReplyChecked('B 답변')

    expect(store.retryableFailures.map((t) => t.id)).toEqual(['c-2'])
    await store.retryFailed()

    expect(commentsApi.batchReply).toHaveBeenLastCalledWith([2], 'B 답변')
    expect(commentsApi.batchReply).not.toHaveBeenCalledWith(expect.arrayContaining([1]), expect.anything())
    expect(commentsApi.batchHide).toHaveBeenCalledTimes(1)
    expect(store.threads.find((t) => t.id === 'c-1')?.replyContent).toBeNull()
  })

  it('retries a failed draft with its own draft text, not a batch reply', async () => {
    const store = await loadedStore([1])
    store.toggleCheckAll()
    vi.mocked(commentsApi.batchAiDraft).mockResolvedValue({
      drafts: [{ commentId: 1, commentContent: 'c', draftReply: '초안 1', tone: 'FRIENDLY' }], totalCreditsUsed: 3,
    })
    await store.generateDrafts()
    vi.mocked(commentsApi.reply)
      .mockRejectedValueOnce({ response: { status: 503, data: { error: 'PLATFORM_API_FAILED', message: '지연' } } })
      .mockResolvedValueOnce({} as never)
    await store.sendDrafts()

    await store.retryFailed()

    expect(commentsApi.reply).toHaveBeenLastCalledWith(1, '초안 1')
    expect(commentsApi.batchReply).not.toHaveBeenCalled()
    expect(store.pendingDrafts).toEqual([])
  })

  // ── 서버 감정 필터 · 늦게 온 응답 ────────────────────────────────────

  /** 회귀: 부정 탭이 불러온 50개만 걸러, 아직 불러오지 않은 부정 댓글은 보이지 않았다. */
  it('finds not-yet-loaded negative comments through the server sentiment filter and clears it on leaving', async () => {
    const store = await loadedStore([1, 2])
    vi.mocked(commentsApi.list).mockResolvedValueOnce({
      comments: [commentWith(77, { sentiment: 'NEGATIVE' })], totalCount: 1,
    } as never)

    await store.setFilter('negative')

    expect(commentsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ sentiment: 'NEGATIVE', page: 0 }))
    expect(store.visibleThreads.map((t) => t.id)).toEqual(['c-77'])

    vi.mocked(commentsApi.list).mockResolvedValueOnce({ comments: [commentWith(1), commentWith(2)], totalCount: 2 } as never)
    await store.setFilter('all')

    expect(vi.mocked(commentsApi.list).mock.lastCall?.[0]).toMatchObject({ sentiment: undefined })
    expect(store.threads.map((t) => t.id).sort()).toEqual(['c-1', 'c-2'])
  })

  it('does not refetch when switching between client-side filters', async () => {
    const store = await loadedStore([1])
    const calls = vi.mocked(commentsApi.list).mock.calls.length

    await store.setFilter('question')
    await store.setFilter('collab')

    expect(vi.mocked(commentsApi.list).mock.calls.length).toBe(calls)
  })

  /** 검색을 빠르게 바꾸면 늦게 온 예전 응답이 새 결과를 덮지 않는다. */
  it('drops a stale search response that arrives after a newer one', async () => {
    const store = await loadedStore([1])
    let resolveOld!: (value: unknown) => void
    vi.mocked(commentsApi.list)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ comments: [commentWith(2)], totalCount: 1 } as never)

    const older = store.setQuery({ search: '예전' })
    await store.setQuery({ search: '최신' })
    resolveOld({ comments: [commentWith(9)], totalCount: 1 })
    await older

    expect(store.threads.map((t) => t.id)).toEqual(['c-2'])
  })

  it('does not append the next page of an old query after the query changed', async () => {
    const store = await loadedStore([1, 2], 5)
    let resolvePage!: (value: unknown) => void
    vi.mocked(commentsApi.list)
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePage = resolve }) as never)
      .mockResolvedValueOnce({ comments: [commentWith(8)], totalCount: 1 } as never)

    const more = store.loadMoreComments()
    await store.setQuery({ search: '새 검색' })
    resolvePage({ comments: [commentWith(3), commentWith(4)], totalCount: 5 })
    await more

    expect(store.threads.map((t) => t.id)).toEqual(['c-8'])
  })
})
