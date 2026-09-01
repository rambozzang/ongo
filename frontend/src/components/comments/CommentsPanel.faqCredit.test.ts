import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import CommentsPanel from '@/components/comments/CommentsPanel.vue'
import { commentsApi } from '@/api/comments'
import { useCommentsStore } from '@/stores/comments'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

const notify = {
  error: vi.fn(),
  success: vi.fn(),
  info: vi.fn(),
  warning: vi.fn(),
}

vi.mock('@/api/comments', () => ({
  commentsApi: {
    list: vi.fn().mockResolvedValue({
      comments: [],
      totalCount: 0,
      stats: { total: 0, positive: 0, neutral: 0, negative: 0 },
      capabilities: {},
    }),
    syncAll: vi.fn(),
    syncVideo: vi.fn(),
    reply: vi.fn(),
    pin: vi.fn(),
    hide: vi.fn(),
    delete: vi.fn(),
    getCapabilities: vi.fn(),
    sentimentTrend: vi.fn().mockResolvedValue({}),
    faqClusters: vi.fn(),
    batchAiDraft: vi.fn(),
    aiReplyGenerate: vi.fn(),
    batchReply: vi.fn(),
    batchHide: vi.fn(),
    crisisDetection: vi.fn().mockResolvedValue({}),
    keywordCloud: vi.fn().mockResolvedValue({}),
  },
}))

vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => notify,
}))

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(CommentsPanel, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        CommentCrisisBanner: { template: '<div />' },
        CommentSentimentPanel: { template: '<div />' },
        CommentBatchDraftList: { template: '<div />' },
        CommentFilterBar: { template: '<div />' },
        CommentList: { template: '<div />' },
        CommentPagination: { template: '<div />' },
        CommentBatchBar: { template: '<div />' },
        KeywordCloudSection: { template: '<div />' },
        EmptyState: { template: '<div />' },
      },
    },
  })
  return { wrapper, router }
}

const findFaqButton = (wrapper: ReturnType<typeof mount>) =>
  wrapper.find('[data-testid="faq-analyze-button"]')

describe('CommentsPanel FAQ 크레딧 차단 CTA (FAQ_CLUSTERING)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('CREDIT_INSUFFICIENT면 FAQ CTA 노출·분석 버튼 disabled·CTA 클릭 이벤트', async () => {
    vi.mocked(commentsApi.faqClusters).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const commentsStore = useCommentsStore()
    await flushPromises()

    await findFaqButton(wrapper).trigger('click')
    await flushPromises()

    expect(commentsStore.faqCreditBlocked).toBe(true)
    const cta = wrapper.find('[data-testid="faq-credit-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('크레딧 충전하기')
    // 차단 상태에서 분석 버튼은 disabled 로 유지된다.
    expect((findFaqButton(wrapper).element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    // CTA 클릭 → 실제 구매 모달 열림(부모로 올린 이벤트가 모달을 연다).
    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('일반 오류(비 안정 코드)면 FAQ 크레딧 CTA 없이 기존 실패 안내(토스트) 유지', async () => {
    vi.mocked(commentsApi.faqClusters).mockRejectedValue({ notAnError: true })

    const { wrapper } = render()
    const commentsStore = useCommentsStore()
    await flushPromises()

    await findFaqButton(wrapper).trigger('click')
    await flushPromises()

    expect(commentsStore.faqCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="faq-credit-cta"]').exists()).toBe(false)
    expect(notify.error).toHaveBeenCalled()
  })

  it('PLAN_LIMIT_EXCEEDED는 FAQ 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(commentsApi.faqClusters).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    const { wrapper } = render()
    const commentsStore = useCommentsStore()
    await flushPromises()

    await findFaqButton(wrapper).trigger('click')
    await flushPromises()

    expect(commentsStore.faqCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="faq-credit-cta"]').exists()).toBe(false)
  })

  it('403은 FAQ 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(commentsApi.faqClusters).mockRejectedValue({
      response: { status: 403, data: { success: false, message: 'forbidden' } },
    })

    const { wrapper } = render()
    const commentsStore = useCommentsStore()
    await flushPromises()

    await findFaqButton(wrapper).trigger('click')
    await flushPromises()

    expect(commentsStore.faqCreditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="faq-credit-cta"]').exists()).toBe(false)
  })

  it('purchase 뒤 fetchBalance 호출 + FAQ 차단 해제, 자동 faq API 재호출 0회', async () => {
    vi.mocked(commentsApi.faqClusters).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const commentsStore = useCommentsStore()
    await flushPromises()

    await findFaqButton(wrapper).trigger('click')
    await flushPromises()
    expect(commentsStore.faqCreditBlocked).toBe(true)

    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi
      .spyOn(creditStore, 'fetchBalance')
      .mockResolvedValue(undefined as never)
    const callsBefore = vi.mocked(commentsApi.faqClusters).mock.calls.length

    // 실제 CTA 클릭 → 모달 열림 확인 후 purchase 를 emit 한다.
    const cta = wrapper.find('[data-testid="faq-credit-cta"]')
    expect(cta.exists()).toBe(true)
    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)

    await wrapper
      .findComponent(CreditPurchaseModal)
      .vm.$emit('purchase', { key: 'STARTER', name: 'Starter', pricePerCredit: 100, validDays: 30 })
    await flushPromises()

    expect(fetchBalanceSpy).toHaveBeenCalled()
    expect(commentsStore.faqCreditBlocked).toBe(false)
    // 자동 재실행 없음 — purchase 전후 faq API 호출 횟수 동일.
    expect(vi.mocked(commentsApi.faqClusters).mock.calls.length).toBe(callsBefore)
  })
})
