import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import VideosView from './VideosView.vue'
import { metaRewriteApi } from '@/api/metaRewrite'
import { videoApi } from '@/api/video'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/repurpose', () => ({ repurposeApi: { analyzeForRepurpose: vi.fn() } }))
vi.mock('@/api/metaRewrite', () => ({ metaRewriteApi: { rewrite: vi.fn() } }))
vi.mock('@/api/publishChecklist', () => ({ publishChecklistApi: { get: vi.fn() } }))
vi.mock('@/api/videoSeo', () => ({ videoSeoApi: { score: vi.fn() } }))
vi.mock('@/api/viewsPrediction', () => ({ viewsPredictionApi: { predict: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { feed: vi.fn(), list: vi.fn() } }))
vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/compose', component: { template: '<div />' } },
    { path: '/subscription', component: { template: '<div />' } },
  ],
})

const internalVideo = {
  videoId: 123,
  platformVideoId: 'pv-1',
  platform: 'YT',
  channelName: 'ch',
  title: '내 영상',
  description: null,
  thumbnailUrl: null,
  platformUrl: null,
  viewCount: 100,
  likeCount: null,
  commentCount: null,
  shareCount: null,
  publishedAt: null,
} as const

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

// BaseModal 이 <Teleport to="body"> 로 렌더되므로 모달 내용은 wrapper 가 아닌 document.body 에 있다.
const creditCtaInBody = () => document.querySelector('[data-testid="rewrite-credit-cta"]')

interface RewriteVm {
  openRewriteModal: (item: unknown) => void
  runRewrite: () => Promise<void>
  rewriteCreditBlocked: boolean
  rewriteModal: { error: string | null; result: unknown }
  creditModalContext: 'repurpose' | 'seo' | 'rewrite' | null
  showCreditModal: boolean
}

describe('VideosView AI 리라이트(META_REWRITE) 크레딧 차단 CTA', () => {
  let pinia: ReturnType<typeof createPinia>
  let wrapper: ReturnType<typeof mount> | null = null

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(videoApi.feed).mockResolvedValue({ items: [internalVideo], platforms: [], errors: [] } as never)
    vi.mocked(metaRewriteApi.rewrite).mockResolvedValue({
      id: 123,
      videoId: 123,
      originalTitle: '원본 제목',
      originalDescription: '원본 설명',
      suggestedTitle: '제안 제목',
      suggestedDescription: '제안 설명',
      suggestedTags: ['태그'],
      reasoning: '이유',
      expectedImpactPercent: 10,
    } as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  function mountView() {
    return mount(VideosView, {
      global: {
        plugins: [pinia, router, i18n],
        stubs: {
          PageHeader: true,
          LoadingSpinner: true,
          EmptyState: true,
          SectionCard: true,
          ThumbPlaceholder: true,
          PlatformChip: true,
          VideoDetailPanel: true,
        },
      },
    })
  }

  it('CREDIT_INSUFFICIENT면 충전 CTA가 노출되고 클릭하면 rewrite context로 실제 구매 모달이 열린다', async () => {
    vi.mocked(metaRewriteApi.rewrite).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RewriteVm
    vm.openRewriteModal(internalVideo as never)
    await flushPromises()
    await vm.runRewrite()
    await flushPromises()

    // 안정 코드로만 막힘 상태가 켜진다.
    expect(vm.rewriteCreditBlocked).toBe(true)
    // 모달 바디에 실제 CTA가 렌더된다.
    const cta = creditCtaInBody()
    expect(cta).not.toBeNull()
    expect(cta!.textContent).toContain('크레딧 충전하기')
    // 아직 구매 모달은 닫혀 있다.
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    // CTA 클릭 → rewrite context 로 실제 CreditPurchaseModal 이 열린다.
    cta!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
    expect(vm.creditModalContext).toBe('rewrite')
    expect(vm.showCreditModal).toBe(true)
  })

  it('일반 Error/네트워크 오류엔 CTA가 없고 기존 오류 문구만 보인다', async () => {
    vi.mocked(metaRewriteApi.rewrite).mockRejectedValue(new Error('network fail'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RewriteVm
    vm.openRewriteModal(internalVideo as never)
    await flushPromises()
    await vm.runRewrite()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.rewriteCreditBlocked).toBe(false)
    expect(vm.rewriteModal.error).toBe('network fail')
  })

  it('정상 리라이트는 결과를 표시하고 CTA가 없다', async () => {
    wrapper = mountView()
    const vm = wrapper.vm as unknown as RewriteVm
    vm.openRewriteModal(internalVideo as never)
    await flushPromises()
    await vm.runRewrite()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.rewriteCreditBlocked).toBe(false)
    expect(vm.rewriteModal.result).not.toBeNull()
    expect(document.body.textContent).toContain('제안 제목')
  })

  it('purchase 이벤트 뒤 fetchBalance 호출 + rewrite 상태 정리(자동 재실행 없음)', async () => {
    vi.mocked(metaRewriteApi.rewrite).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RewriteVm
    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi.spyOn(creditStore, 'fetchBalance').mockResolvedValue(undefined as never)

    vm.openRewriteModal(internalVideo as never)
    await flushPromises()
    await vm.runRewrite()
    await flushPromises()
    expect(vm.rewriteCreditBlocked).toBe(true)
    const callsBefore = vi.mocked(metaRewriteApi.rewrite).mock.calls.length

    // 실제 흐름: CTA 클릭으로 rewrite context 가 설정되고 모달이 열린다.
    const cta = creditCtaInBody()!
    cta.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(vm.creditModalContext).toBe('rewrite')
    expect(vm.showCreditModal).toBe(true)

    await wrapper.findComponent(CreditPurchaseModal).vm.$emit('purchase', {
      key: 'STARTER',
      name: 'Starter',
      pricePerCredit: 100,
      validDays: 30,
    })
    await flushPromises()

    // 결제 완료 뒤 잔액을 서버에서 새로 받는다.
    expect(fetchBalanceSpy).toHaveBeenCalled()
    // 막힘 상태가 풀리고 CTA가 사라진다.
    expect(vm.rewriteCreditBlocked).toBe(false)
    expect(creditCtaInBody()).toBeNull()
    // SEO/repurpose 상태를 지우지 않는다.
    expect(vm.creditModalContext).toBe('rewrite')
    // 자동 재실행 없음 — purchase 전후 rewrite 호출 횟수 동일.
    expect(vi.mocked(metaRewriteApi.rewrite).mock.calls.length).toBe(callsBefore)
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않고 기존 안내를 유지한다', async () => {
    vi.mocked(metaRewriteApi.rewrite).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RewriteVm
    vm.openRewriteModal(internalVideo as never)
    await flushPromises()
    await vm.runRewrite()
    await flushPromises()

    expect(vm.rewriteCreditBlocked).toBe(false)
    expect(creditCtaInBody()).toBeNull()
    // 기존 오류 안내(일반 메시지)는 유지된다.
    expect(vm.rewriteModal.error).toBeTruthy()
  })
})
