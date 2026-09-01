import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import SubscriptionView from './SubscriptionView.vue'
import { capabilitiesApi } from '@/api/capabilities'
import { subscriptionApi } from '@/api/subscription'
import { creditApi } from '@/api/credit'
import { channelApi } from '@/api/channel'
import { paymentApi } from '@/api/payment'
import koMessages from '@/locales/ko/common.json'

/**
 * 결제가 막혔을 때 사용자가 **직접 다시 확인할 수 있는지**, 그리고 그 버튼이
 * **결제를 열어 주지는 않는지** 실제 렌더 결과로 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * capability 응답은 캐시된다. 운영자가 결제 설정을 켠 뒤에도 **이미 열려 있던 탭**은
 * 캐시가 만료될 때까지 계속 "결제 사용 불가"를 보여줬다. 사용자는 설정이 켜졌다는 사실도,
 * 새로고침이 필요하다는 사실도 알 수 없다 — 살 수 있는데 못 사는 상태가 조용히 이어진다.
 *
 * ## 반대 방향도 막는다
 *
 * 그렇다고 버튼을 눌렀다는 이유로 결제를 열면 안 된다. 서버가 여전히 비활성이라고 답하면
 * 그대로 비활성이다. 열어 봐야 사용자는 결제창에서 원인을 알 수 없는 오류를 보고, 서버에는
 * 아무도 정리하지 않는 대기 결제가 남는다.
 *
 * 그래서 `usePaymentAvailability` 는 **목으로 대체하지 않는다.** capability 응답만 세우고
 * 화면까지 실제로 흐르게 한다.
 */

vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))
vi.mock('@/api/subscription', () => ({
  subscriptionApi: {
    getCurrent: vi.fn(),
    getPlans: vi.fn(),
    getUsage: vi.fn(),
    getUsageAlerts: vi.fn(),
    updateUsageAlert: vi.fn(),
    deleteUsageAlert: vi.fn(),
  },
}))
vi.mock('@/api/credit', () => ({
  creditApi: { getBalance: vi.fn(), getTransactions: vi.fn(), getPackages: vi.fn() },
}))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn() } }))
vi.mock('@/api/payment', () => ({ paymentApi: { getHistory: vi.fn() } }))
vi.mock('@/composables/usePortOne', () => ({
  usePortOne: () => ({
    initialized: ref(false),
    loading: ref(false),
    ensureInitialized: vi.fn().mockResolvedValue(undefined),
    openSubscriptionCheckout: vi.fn(),
    openCreditCheckout: vi.fn(),
  }),
}))

function paymentCapability(enabled: boolean, reason: string | null = null) {
  vi.mocked(capabilitiesApi.list).mockResolvedValue([
    { key: 'payment', enabled, reason },
  ] as never)
}

async function renderSubscription() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/subscription', component: { template: '<div />' } }],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/subscription')
  await router.isReady()

  const wrapper = mount(SubscriptionView, {
    global: {
      plugins: [createPinia(), router, i18n],
      stubs: {
        PageHeader: true,
        PageGuide: true,
        PlanComparisonTable: true,
        CreditPurchaseModal: true,
        PaymentModal: true,
        ConfirmModal: true,
        LoadingSpinner: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof renderSubscription>>

const recheckButtons = (wrapper: Wrapper) => wrapper.findAll('[data-testid="payment-recheck"]')
const chargeButton = (wrapper: Wrapper) =>
  wrapper.findAll('button').find((b) => b.text().includes('크레딧 충전'))

describe('SubscriptionView 결제 상태 재확인', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    paymentCapability(false, '결제 설정이 없습니다')
    vi.mocked(subscriptionApi.getPlans).mockResolvedValue({
      currentPlan: 'FREE',
      plans: [{
        planType: 'FREE', price: 0, yearlyPrice: 0, recommended: false,
        features: {
          maxPlatforms: 1, monthlyUploads: 5, scheduleDays: 0, analyticsDays: 7,
          storageGB: 1, freeCredits: 30, maxTeamMembers: 0,
        },
      }],
    } as never)
    vi.mocked(subscriptionApi.getCurrent).mockResolvedValue({
      planType: 'FREE', status: 'FREE', price: 0, billingCycle: 'MONTHLY',
      currentPeriodEnd: null, nextBillingDate: null, features: [],
    } as never)
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 0, storageUsedBytes: 0, storageLimitBytes: 1024 * 1024 * 1024,
    } as never)
    vi.mocked(subscriptionApi.getUsageAlerts).mockResolvedValue([] as never)
    vi.mocked(paymentApi.getHistory).mockResolvedValue({
      content: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
    } as never)
    vi.mocked(creditApi.getBalance).mockResolvedValue({
      totalBalance: 30, freeMonthly: 30, freeRemaining: 30, purchasedBalance: 0,
      freeResetDate: '2026-09-01T00:00:00Z',
    } as never)
    vi.mocked(creditApi.getTransactions).mockResolvedValue({
      content: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
    } as never)
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [], maxAllowed: 1, currentCount: 0,
    } as never)
  })

  /** **핵심.** 재확인 수단이 없으면 사용자는 새로고침이 필요하다는 것조차 모른다. */
  it('결제가 막혀 있으면 다시 확인할 버튼을 보여준다', async () => {
    const wrapper = await renderSubscription()

    expect(recheckButtons(wrapper).length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('결제 설정이 없습니다')
  })

  /** 결제가 가능하면 안내도 버튼도 필요 없다. */
  it('결제가 가능하면 안내와 버튼을 띄우지 않는다', async () => {
    paymentCapability(true)

    const wrapper = await renderSubscription()

    expect(wrapper.find('[data-testid="payment-unavailable-notice"]').exists()).toBe(false)
    expect(chargeButton(wrapper)!.attributes('disabled')).toBeUndefined()
  })

  /** 운영자가 방금 켠 설정이 **새로고침 없이** 이 탭에 반영된다. */
  it('재확인으로 결제가 열리면 안내가 사라지고 충전 버튼이 풀린다', async () => {
    const wrapper = await renderSubscription()
    expect(chargeButton(wrapper)!.attributes('disabled')).toBeDefined()

    paymentCapability(true)
    await recheckButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="payment-unavailable-notice"]').exists()).toBe(false)
    expect(chargeButton(wrapper)!.attributes('disabled')).toBeUndefined()
  })

  /** 캐시를 건너뛰지 않으면 재확인이 재확인이 아니다. */
  it('재확인은 캐시를 건너뛴다', async () => {
    const wrapper = await renderSubscription()

    await recheckButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(capabilitiesApi.list).toHaveBeenCalledWith({ force: true })
  })

  /**
   * **핵심.** 서버가 여전히 막고 있으면 버튼은 재확인만 한다. 눌렀다는 이유로 결제가
   * 열리면 사용자는 결제창에서 원인 모를 오류를 보고, 서버에는 대기 결제가 남는다.
   */
  it('서버가 계속 비활성이면 눌러도 결제가 열리지 않는다', async () => {
    const wrapper = await renderSubscription()

    await recheckButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(chargeButton(wrapper)!.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="payment-unavailable-notice"]').exists()).toBe(true)
    // 다시 확인할 수단은 남아 있어야 한다 — 나중에 켜질 수 있다.
    expect(recheckButtons(wrapper).length).toBeGreaterThan(0)
  })

  /** 조회 실패도 fail-closed 다. 모르는 채 여는 것보다 막는 편이 낫다. */
  it('조회에 실패하면 결제를 열지 않고 확인하지 못했다고 알린다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('capability offline'))

    const wrapper = await renderSubscription()

    expect(chargeButton(wrapper)!.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('확인하지 못했습니다')
  })

  /** 실패한 뒤에도 다시 시도할 수 있어야 한다 — 장애는 지나간다. */
  it('조회 실패 뒤 재확인이 성공하면 결제가 열린다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('capability offline'))
    const wrapper = await renderSubscription()

    paymentCapability(true)
    await recheckButtons(wrapper)[0].trigger('click')
    await flushPromises()

    expect(chargeButton(wrapper)!.attributes('disabled')).toBeUndefined()
  })

  /** 진행 중에는 버튼을 잠근다 — 연타로 요청이 늘지 않게 한다. */
  it('확인 중에는 버튼을 잠근다', async () => {
    const wrapper = await renderSubscription()
    let resolve!: (value: unknown) => void
    vi.mocked(capabilitiesApi.list).mockReturnValue(new Promise((r) => { resolve = r }) as never)

    await recheckButtons(wrapper)[0].trigger('click')
    await wrapper.vm.$nextTick()
    expect(recheckButtons(wrapper)[0].attributes('disabled')).toBeDefined()

    resolve([{ key: 'payment', enabled: false, reason: '결제 설정이 없습니다' }])
    await flushPromises()

    expect(recheckButtons(wrapper)[0].attributes('disabled')).toBeUndefined()
  })

  /**
   * **회귀 방지.** 마운트 시 반드시 캐시를 무시하는 강제 조회를 써야 한다.
   * 그래야 부팅 직후의 실패한 capability 캐시나 60초 TTL stale 캐시 때문에
   * 살 수 있는데 못 사는 데드엔드가 생기지 않는다. 문자열이 아니라 실제 호출 계약을 검증한다.
   */
  it('마운트 시 캐시를 무시하는 강제 조회로 결제 가능 여부를 확인한다', async () => {
    paymentCapability(true)

    await renderSubscription()

    expect(capabilitiesApi.list).toHaveBeenCalledWith({ force: true })
  })

  /**
   * **강화.** 강제 조회로 바꿨어도, 서버가 진짜로 막고 있으면 마운트 직후에도
   * 버튼은 비활성이다. 눌러도 결제가 열리지 않는 fail-closed 는 유지된다.
   */
  it('마운트 강제 조회에서도 서버가 막으면 버튼은 비활성이다', async () => {
    paymentCapability(false, '결제 설정이 없습니다')

    const wrapper = await renderSubscription()

    expect(chargeButton(wrapper)!.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="payment-unavailable-notice"]').exists()).toBe(true)
    // 강제 조회 자체는 캐시를 건넜다
    expect(capabilitiesApi.list).toHaveBeenCalledWith({ force: true })
  })

  /**
   * **강화.** 강제 조회가 실패(5xx 등)해도 결제를 열지 않는다 — 모르는 채 여는 것보다 막는다.
   */
  it('마운트 강제 조회가 실패하면 버튼은 비활성이다', async () => {
    vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('capability offline'))

    const wrapper = await renderSubscription()

    expect(chargeButton(wrapper)!.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('확인하지 못했습니다')
  })
})
