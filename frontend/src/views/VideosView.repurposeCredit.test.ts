import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import VideosView from './VideosView.vue'
import { repurposeApi } from '@/api/repurpose'
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
const creditCtaInBody = () => document.querySelector('[data-testid="repurpose-credit-cta"]')

interface RepurposeVm {
  openRepurposeModal: (item: unknown) => void
  runRepurpose: () => Promise<void>
  repurposeCreditBlocked: boolean
  repurposeModal: { error: string | null; clips: unknown[] }
}

describe('VideosView 리퍼포즈 크레딧 차단 CTA', () => {
  let pinia: ReturnType<typeof createPinia>
  let wrapper: ReturnType<typeof mountView>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.mocked(videoApi.feed).mockResolvedValue({ items: [internalVideo], platforms: [], errors: [] } as never)
    vi.mocked(repurposeApi.analyzeForRepurpose).mockResolvedValue({
      id: 1,
      videoId: 123,
      videoTitle: '내 영상',
      status: 'COMPLETED',
      clips: [],
      createdAt: '',
      completedAt: null,
      errorMessage: null,
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
    vi.mocked(repurposeApi.analyzeForRepurpose).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RepurposeVm
    vm.openRepurposeModal(internalVideo as never)
    await flushPromises()
    await vm.runRepurpose()
    await flushPromises()

    // 안정 코드로만 막힘 상태가 켜진다.
    expect(vm.repurposeCreditBlocked).toBe(true)
    // 모달 바디에 실제 CTA가 렌더된다.
    const cta = creditCtaInBody()
    expect(cta).not.toBeNull()
    expect(cta!.textContent).toContain('크레딧 충전하기')
    // 아직 구매 모달은 닫혀 있다.
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    // CTA 클릭 → 실제 CreditPurchaseModal 이 열린다.
    cta!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('일반 Error/네트워크 오류엔 CTA가 없고 기존 오류 문구만 보인다', async () => {
    vi.mocked(repurposeApi.analyzeForRepurpose).mockRejectedValue(new Error('network fail'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RepurposeVm
    vm.openRepurposeModal(internalVideo as never)
    await flushPromises()
    await vm.runRepurpose()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.repurposeCreditBlocked).toBe(false)
    expect(vm.repurposeModal.error).toBe('network fail')
  })

  it('정상 분석은 clips를 표시하고 CTA가 없다', async () => {
    vi.mocked(repurposeApi.analyzeForRepurpose).mockResolvedValue({
      id: 1,
      videoId: 123,
      videoTitle: '내 영상',
      status: 'COMPLETED',
      clips: [{ startSeconds: 1, endSeconds: 2, title: '클립A', viralScore: 9, reason: 'r' }],
      createdAt: '',
      completedAt: null,
      errorMessage: null,
    })

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RepurposeVm
    vm.openRepurposeModal(internalVideo as never)
    await flushPromises()
    await vm.runRepurpose()
    await flushPromises()

    expect(creditCtaInBody()).toBeNull()
    expect(vm.repurposeCreditBlocked).toBe(false)
    expect(vm.repurposeModal.clips.length).toBeGreaterThan(0)
    expect(document.body.textContent).toContain('클립A')
  })

  it('purchase 이벤트 뒤 creditStore.fetchBalance 호출 + 재시도 상태가 정리된다(자동 재실행 없음)', async () => {
    vi.mocked(repurposeApi.analyzeForRepurpose).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountView()
    const vm = wrapper.vm as unknown as RepurposeVm
    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi.spyOn(creditStore, 'fetchBalance').mockResolvedValue(undefined as never)

    vm.openRepurposeModal(internalVideo as never)
    await flushPromises()
    await vm.runRepurpose()
    await flushPromises()
    expect(vm.repurposeCreditBlocked).toBe(true)

    await wrapper.findComponent(CreditPurchaseModal).vm.$emit('purchase', {
      key: 'STARTER',
      name: 'Starter',
      pricePerCredit: 100,
      validDays: 30,
    })
    await flushPromises()

    // 결제 완료 뒤 잔액을 서버에서 새로 받는다.
    expect(fetchBalanceSpy).toHaveBeenCalled()
    // 막힘 상태가 풀려 CTA가 사라지고(=같은 모달에서 재시도 동선 확보) 자동 재실행은 하지 않는다.
    expect(vm.repurposeCreditBlocked).toBe(false)
    expect(creditCtaInBody()).toBeNull()
  })
})
