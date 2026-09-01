import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import VideosView from './VideosView.vue'
import { videoSeoApi } from '@/api/videoSeo'
import { videoApi } from '@/api/video'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/repurpose', () => ({ repurposeApi: { analyzeForRepurpose: vi.fn() } }))
vi.mock('@/api/metaRewrite', () => ({ metaRewriteApi: { rewrite: vi.fn() } }))
vi.mock('@/api/publishChecklist', () => ({ publishChecklistApi: { get: vi.fn() } }))
vi.mock('@/api/videoSeo', () => ({ videoSeoApi: { analyze: vi.fn() } }))
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
const creditCtaInBody = () => document.querySelector('[data-testid="seo-credit-cta"]')

interface SeoVm {
  openSeoModal: (item: unknown) => void
  runSeoAnalysis: () => Promise<void>
  seoCreditBlocked: boolean
  seoModal: { error: string | null; result: unknown }
}

describe('VideosView SEO 점수 크레딧 차단 CTA', () => {
  let pinia: ReturnType<typeof createPinia>
  let wrapper: ReturnType<typeof mountView>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(videoApi.feed).mockResolvedValue({ items: [internalVideo], platforms: [], errors: [] } as never)
    vi.mocked(videoSeoApi.analyze).mockResolvedValue({
      videoId: 123,
      overallScore: 80,
      categories: [],
      suggestions: [],
      creditsUsed: 1,
    })
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

  it('CREDIT_INSUFFICIENT면 충전 CTA가 노출되고 클릭하면 실제 구매 모달이 열린다', async () => {
    vi.mocked(videoSeoApi.analyze).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as SeoVm
    vm.openSeoModal(internalVideo as never)
    await flushPromises()
    await vm.runSeoAnalysis()
    await flushPromises()

    // 안정 코드로만 막힘 상태가 켜진다.
    expect(vm.seoCreditBlocked).toBe(true)
    // 모달 바디에 실제 CTA가 렌더된다.
    const cta = creditCtaInBody()
    expect(cta).not.toBeNull()
    expect(cta!.textContent).toContain('크레딧 충전')
    // 아직 구매 모달은 닫혀 있다.
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    // CTA 클릭 → 실제 CreditPurchaseModal 이 열린다.
    cta!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('일반 Error/네트워크 오류엔 CTA가 없고 기존 오류 문구만 보인다', async () => {
    vi.mocked(videoSeoApi.analyze).mockRejectedValue(new Error('network fail'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as SeoVm
    vm.openSeoModal(internalVideo as never)
    await flushPromises()
    await vm.runSeoAnalysis()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.seoCreditBlocked).toBe(false)
    expect(vm.seoModal.error).toBe('network fail')
  })

  it('정상 SEO 분석은 결과를 표시하고 CTA가 없다', async () => {
    wrapper = mountView()
    const vm = wrapper.vm as unknown as SeoVm
    vm.openSeoModal(internalVideo as never)
    await flushPromises()
    await vm.runSeoAnalysis()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.seoCreditBlocked).toBe(false)
    expect(vm.seoModal.result).not.toBeNull()
    expect(document.body.textContent).toContain('80')
  })

  it('purchase 이벤트 뒤 creditStore.fetchBalance 호출 + SEO 차단 상태 정리(자동 재실행 없음)', async () => {
    vi.mocked(videoSeoApi.analyze).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as SeoVm
    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi.spyOn(creditStore, 'fetchBalance').mockResolvedValue(undefined as never)

    vm.openSeoModal(internalVideo as never)
    await flushPromises()
    await vm.runSeoAnalysis()
    await flushPromises()
    expect(vm.seoCreditBlocked).toBe(true)

    // 실제 흐름: SEO CTA 클릭으로 컨텍스트('seo')가 설정되고 구매 모달이 열린다.
    const cta = creditCtaInBody()
    expect(cta).not.toBeNull()
    cta!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)

    // 구매 이벤트 전 analyze 호출 횟수(중복 과금 방지 검증용).
    const callsAfterBlock = vi.mocked(videoSeoApi.analyze).mock.calls.length

    await wrapper.findComponent(CreditPurchaseModal).vm.$emit('purchase', {
      key: 'STARTER',
      name: 'Starter',
      pricePerCredit: 100,
      validDays: 30,
    })
    await flushPromises()

    // 결제 완료 뒤 잔액을 서버에서 새로 받는다.
    expect(fetchBalanceSpy).toHaveBeenCalled()
    // SEO 막힘 상태가 풀려 CTA가 사라지고(=같은 모달에서 재시도 동선 확보) 자동 재실행은 하지 않는다.
    expect(vm.seoCreditBlocked).toBe(false)
    expect(creditCtaInBody()).toBeNull()
    // analyze 가 실제로 다시 호출되지 않는다(중복 과금 방지). 구매 이벤트 전후 호출 횟수가 같아야 한다.
    expect(vi.mocked(videoSeoApi.analyze).mock.calls.length).toBe(callsAfterBlock)
  })
})
