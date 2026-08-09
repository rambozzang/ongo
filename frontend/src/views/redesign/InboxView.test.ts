import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import InboxView from './InboxView.vue'
import { commentsApi } from '@/api/comments'
import { inboxApi } from '@/api/inbox'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn(),
    reply: vi.fn(),
    pin: vi.fn(),
    hide: vi.fn(),
    aiReplyGenerate: vi.fn(),
    batchHide: vi.fn(),
  },
}))
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
})
