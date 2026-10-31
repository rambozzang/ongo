import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import InboxView from './InboxView.vue'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'
import { aiApi } from '@/api/ai'

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn(),
    reply: vi.fn(),
    pin: vi.fn(),
    hide: vi.fn(),
    aiReplyGenerate: vi.fn(),
    batchHide: vi.fn(),
    batchReply: vi.fn(),
    batchAiDraft: vi.fn(),
  },
}))
vi.mock('@/api/ai', () => ({ aiApi: { getFeatures: vi.fn() } }))
vi.mock('@/api/inbox', () => ({
  inboxApi: { listMessages: vi.fn(), toggleStar: vi.fn(), markAsRead: vi.fn() },
}))
vi.mock('@/api/video', () => ({ videoApi: { get: vi.fn() } }))

const comment = (overrides: Record<string, unknown> = {}) => ({
  id: 11,
  videoId: 22,
  platform: 'YOUTUBE',
  platformCommentId: 'comment-11',
  authorName: '시청자',
  authorAvatarUrl: null,
  authorChannelUrl: null,
  content: '이 영상 다음 편도 있나요?',
  sentiment: 'neutral',
  likeCount: 3,
  replyCount: 0,
  isReplied: false,
  isHidden: false,
  isPinned: false,
  replyContent: null,
  repliedAt: null,
  publishedAt: '2026-08-09T09:00:00Z',
  syncedAt: null,
  createdAt: '2026-08-09T09:00:00Z',
  ...overrides,
})

async function renderInbox() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(InboxView, {
    global: {
      plugins: [pinia, i18n],
      stubs: {
        PlatformChip: { template: '<span><slot /></span>' },
        StatusPill: { template: '<span><slot /></span>' },
        ThumbPlaceholder: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('InboxView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(commentsApi.list).mockResolvedValue({ comments: [comment()], totalCount: 1, stats: { total: 1, positive: 0, neutral: 1, negative: 0 }, capabilities: {} } as never)
    vi.mocked(inboxApi.listMessages).mockResolvedValue({ messages: [], totalElements: 0, page: 0, size: 100 } as never)
    vi.mocked(videoApi.get).mockResolvedValue({ title: '질문 영상' } as never)
    vi.mocked(aiApi.getFeatures).mockResolvedValue([{ key: 'BATCH_REPLY_DRAFT', displayName: '배치 답변 초안', creditCost: 3 }] as never)
  })

  it('loads a comment thread, generates a reply suggestion and sends it', async () => {
    vi.mocked(commentsApi.aiReplyGenerate).mockResolvedValue({ candidates: [{ generatedReply: '다음 편도 준비 중이에요!' }] } as never)
    vi.mocked(commentsApi.reply).mockResolvedValue(comment({ isReplied: true }) as never)
    const wrapper = await renderInbox()

    expect(wrapper.text()).toContain('시청자')
    expect(wrapper.text()).toContain('질문 영상')
    const suggest = wrapper.findAll('button').find((button) => button.text() === 'AI 답변 제안')
    expect(suggest).toBeDefined()
    await suggest!.trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').element.value).toBe('다음 편도 준비 중이에요!')

    await wrapper.find('textarea').setValue('감사합니다. 다음 편도 준비 중이에요!')
    const send = wrapper.findAll('button').find((button) => button.text() === '전송')
    expect(send).toBeDefined()
    await send!.trigger('click')
    await flushPromises()
    expect(commentsApi.reply).toHaveBeenCalledWith(11, '감사합니다. 다음 편도 준비 중이에요!')
  })

  it('keeps partial source failures visible and prevents DM reply calls', async () => {
    vi.mocked(inboxApi.listMessages).mockRejectedValueOnce(new Error('DM source down'))
    const wrapper = await renderInbox()

    expect(wrapper.text()).toContain('일부 소스만 불러왔습니다')
    expect(wrapper.text()).toContain('시청자')
    await wrapper.find('textarea').setValue('댓글 답변')
    expect(wrapper.find('textarea').attributes('disabled')).toBeUndefined()

    const dmFilter = wrapper.findAll('button').find((button) => button.text().startsWith('DM'))
    expect(dmFilter).toBeDefined()
    await dmFilter!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('표시할 스레드가 없습니다')
    expect(inboxApi.listMessages).toHaveBeenCalledWith({ size: 100 })
  })

  // ── 일괄 처리 · 초안 · 검색 ─────────────────────────────────────────

  function twoComments() {
    vi.mocked(commentsApi.list).mockResolvedValue({
      comments: [comment(), comment({ id: 12, content: '두 번째 질문?', publishedAt: '2026-08-09T08:00:00Z' })],
      totalCount: 2, stats: { total: 2, positive: 0, neutral: 2, negative: 0 }, capabilities: {},
    } as never)
  }

  async function selectAll(wrapper: Awaited<ReturnType<typeof renderInbox>>) {
    const button = wrapper.findAll('button').find((b) => b.text() === koMessages.redesign.inbox.selectAll)!
    await button.trigger('click')
  }

  /** 일부가 실패하면 성공처럼 말하지 않고, 사유를 보이고, 다시 시도할 수 있는 것만 다시 보낸다. */
  it('shows per-comment failure reasons and retries only the retryable ones', async () => {
    twoComments()
    const wrapper = await renderInbox()
    await selectAll(wrapper)
    vi.mocked(commentsApi.batchHide)
      .mockResolvedValueOnce({
        successCount: 1, failCount: 1, totalRequested: 2,
        items: [
          { commentId: 11, success: true, errorCode: null, message: null, retryable: false },
          { commentId: 12, success: false, errorCode: 'PLATFORM_API_FAILED', message: '플랫폼 응답 지연', retryable: true },
        ],
      })
      .mockResolvedValueOnce({
        successCount: 1, failCount: 0, totalRequested: 1,
        items: [{ commentId: 12, success: true, errorCode: null, message: null, retryable: false }],
      })

    await wrapper.get('[data-testid="inbox-batch-hide"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="inbox-failure-c-12"]').text()).toContain('플랫폼 응답 지연')
    expect(wrapper.find('[data-testid="inbox-failure-c-11"]').exists()).toBe(false)

    await wrapper.get('[data-testid="inbox-retry"]').trigger('click')
    await flushPromises()

    expect(commentsApi.batchHide).toHaveBeenLastCalledWith([12])
    expect(wrapper.find('[data-testid="inbox-retry-bar"]').exists()).toBe(false)
  })

  /** AI 초안은 비용을 먼저 보이고, 검토 화면에서 고친 문구만 보낸다. 만들기만 해서는 아무것도 게시되지 않는다. */
  it('shows the draft cost, lets the user edit drafts and sends only the edited text', async () => {
    twoComments()
    const wrapper = await renderInbox()
    await selectAll(wrapper)
    expect(wrapper.get('[data-testid="inbox-ai-draft"]').text()).toContain('6')
    vi.mocked(commentsApi.batchAiDraft).mockResolvedValue({
      drafts: [
        { commentId: 11, commentContent: 'a', draftReply: '다음 편 준비 중이에요', tone: 'FRIENDLY' },
        { commentId: 12, commentContent: 'b', draftReply: '좋은 질문이에요', tone: 'FRIENDLY' },
      ],
      totalCreditsUsed: 6,
    })

    await wrapper.get('[data-testid="inbox-ai-draft"]').trigger('click')
    await flushPromises()

    expect(commentsApi.reply).not.toHaveBeenCalled()
    const review = wrapper.get('[data-testid="inbox-draft-review"]')
    await review.get('[data-testid="inbox-draft-c-11"] textarea').setValue('다음 편은 금요일에 올라가요!')
    vi.mocked(commentsApi.reply).mockResolvedValue(comment({ isReplied: true }) as never)

    await wrapper.get('[data-testid="inbox-drafts-send-all"]').trigger('click')
    await flushPromises()

    expect(commentsApi.reply).toHaveBeenCalledWith(11, '다음 편은 금요일에 올라가요!')
    expect(commentsApi.reply).toHaveBeenCalledWith(12, '좋은 질문이에요')
    expect(wrapper.find('[data-testid="inbox-draft-review"]').exists()).toBe(false)
  })

  /** 가격을 모르면 화면이 금액을 지어내지 않는다 — 버튼을 열지 않는다. */
  it('keeps AI drafts locked while the server price is unknown', async () => {
    vi.mocked(aiApi.getFeatures).mockRejectedValue(new Error('offline'))
    twoComments()
    const wrapper = await renderInbox()
    await selectAll(wrapper)

    expect(wrapper.get('[data-testid="inbox-ai-draft"]').attributes('disabled')).toBeDefined()
  })

  it('searches comments on the server after the user stops typing', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = await renderInbox()
      await wrapper.get('[data-testid="inbox-search"]').setValue('환불')
      expect(commentsApi.list).toHaveBeenCalledTimes(1)

      await vi.advanceTimersByTimeAsync(350)
      await flushPromises()

      expect(commentsApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ searchText: '환불', page: 0 }))
    } finally {
      vi.useRealTimers()
    }
  })
})
