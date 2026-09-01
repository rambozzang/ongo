import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import SubscriptionView from './SubscriptionView.vue'
import UsageProgressBar from '@/components/subscription/UsageProgressBar.vue'
import { subscriptionApi } from '@/api/subscription'
import { creditApi } from '@/api/credit'
import { channelApi } from '@/api/channel'
import { paymentApi } from '@/api/payment'
import koMessages from '@/locales/ko/common.json'

/**
 * 사용 현황 카드가 **재지 못한 값을 0으로 그리지 않는지**, 그리고 저장 한도를
 * **서버가 준 실효값**으로 쓰는지 실제 렌더 결과로 고정한다.
 *
 * ## 왜 이 화면인가
 *
 * 결제 판단이 일어나는 자리다. 여기서 "저장공간 0%"가 보이면 사용자는 업그레이드가
 * 필요 없다고 읽는다. 그 0이 측정값이 아니라 초기값이면 우리가 잘못된 판단을 만든 것이다.
 *
 * ## 왜 소스 문자열이 아니라 마운트인가
 *
 * 같은 폴더의 다른 `SubscriptionView.*.test.ts`는 소스를 읽어 계약을 고정한다. 그 방식은
 * 값이 **화면에 무엇으로 찍히는지**는 말해 주지 못한다 — `v-if` 하나가 빠져도 소스에는
 * 여전히 그 문자열이 있다. 여기서 막으려는 회귀가 정확히 그것이라 실제로 그려 본다.
 */

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
vi.mock('@/api/channel', () => ({
  channelApi: { list: vi.fn() },
}))
vi.mock('@/api/payment', () => ({
  paymentApi: { getHistory: vi.fn() },
}))
/* 결제 SDK와 가용성 조회는 이 테스트의 관심사가 아니다 — 실제 구현의 반환 형태만 맞춘다. */
vi.mock('@/composables/usePortOne', () => ({
  usePortOne: () => ({
    initialized: ref(false),
    loading: ref(false),
    ensureInitialized: vi.fn().mockResolvedValue(undefined),
    openSubscriptionCheckout: vi.fn(),
    openCreditCheckout: vi.fn(),
  }),
}))
vi.mock('@/composables/usePaymentAvailability', () => ({
  usePaymentAvailability: () => ({
    paymentEnabled: ref(false),
    paymentDisabledReason: ref(null),
    paymentChecked: ref(true),
    paymentChecking: ref(false),
    paymentCheckFailed: ref(false),
    loadPaymentAvailability: vi.fn().mockResolvedValue(undefined),
    recheckPaymentAvailability: vi.fn().mockResolvedValue(undefined),
  }),
}))

/** PRO 플랜의 표 값. 서버 실효 한도와 다르게 두어 어느 쪽을 썼는지 구분한다. */
const PRO_PLAN_STORAGE_GB = 50

function planResponse() {
  return {
    currentPlan: 'PRO',
    plans: [
      {
        planType: 'PRO',
        price: 19900,
        yearlyPrice: 199000,
        recommended: true,
        features: {
          maxPlatforms: 4,
          monthlyUploads: 100,
          scheduleDays: 30,
          analyticsDays: 365,
          storageGB: PRO_PLAN_STORAGE_GB,
          freeCredits: 300,
          maxTeamMembers: 2,
        },
      },
    ],
  }
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

/** 라벨로 막대를 찾는다 — 세 막대가 같은 컴포넌트라 인덱스로 찾으면 순서 변경에 깨진다. */
function barByLabel(wrapper: Awaited<ReturnType<typeof renderSubscription>>, label: string) {
  return wrapper
    .findAllComponents(UsageProgressBar)
    .find((bar) => bar.props('label') === label)
}

describe('SubscriptionView 사용 현황', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(subscriptionApi.getPlans).mockResolvedValue(planResponse() as never)
    vi.mocked(subscriptionApi.getCurrent).mockResolvedValue({
      planType: 'PRO',
      status: 'ACTIVE',
      price: 19900,
      billingCycle: 'MONTHLY',
      currentPeriodEnd: null,
      nextBillingDate: null,
      features: [],
    } as never)
    vi.mocked(paymentApi.getHistory).mockResolvedValue({
      content: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
    } as never)
    vi.mocked(subscriptionApi.getUsageAlerts).mockResolvedValue([] as never)
    vi.mocked(creditApi.getBalance).mockResolvedValue({
      totalBalance: 300, freeMonthly: 300, freeRemaining: 300, purchasedBalance: 0,
      freeResetDate: '2026-09-01T00:00:00Z',
    } as never)
    vi.mocked(creditApi.getTransactions).mockResolvedValue({
      content: [], totalElements: 0, page: 0, size: 20, totalPages: 0,
    } as never)
    vi.mocked(channelApi.list).mockResolvedValue({
      channels: [], maxAllowed: 4, currentCount: 0,
    } as never)
  })

  /* ── (a) 실패 ─────────────────────────────────────────────────────── */

  /**
   * **핵심 회귀.** 예전에는 `usageData`가 `{0, 0}`으로 시작하고 실패해도 그대로 남아
   * "0 / 50 GB"가 그려졌다. 결제 페이지에서 아무것도 안 쓴 것처럼 보인다.
   */
  it('사용량 조회가 실패하면 사용량 막대에 0을 그리지 않는다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockRejectedValue(new Error('usage unavailable'))

    const wrapper = await renderSubscription()

    expect(barByLabel(wrapper, '월간 업로드')).toBeUndefined()
    expect(barByLabel(wrapper, '스토리지')).toBeUndefined()
    expect(wrapper.text()).toContain('사용량을 확인할 수 없습니다')
    // 재지 못한 값을 숫자처럼 보여주는 흔적이 남으면 안 된다.
    expect(wrapper.text()).not.toContain('0 / 50 GB')
    expect(wrapper.text()).not.toContain('0 / 100 회')
  })

  /** 실패는 숨기지 않는다 — 사용자가 다시 시도할 수 있어야 한다. */
  it('사용량 조회 실패를 배너와 재시도 버튼으로 알린다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockRejectedValue(new Error('usage unavailable'))

    const wrapper = await renderSubscription()

    expect(wrapper.text()).toContain('usage unavailable')
    expect(wrapper.text()).toContain('사용량 다시 시도')
  })

  /**
   * 재시도가 성공하면 그때부터는 잰 값을 보여 준다 — 실패 상태에 갇히지 않는다.
   *
   * `fetchUsage` 는 마운트와 이 버튼에서만 불린다. 그래서 "성공 뒤 재조회 실패" 로 낡은
   * 숫자가 남는 경로는 지금 UI 에서는 만들 수 없다(그 대비는 `fetchUsage` 의
   * `usageData.value = null` 로 남겨 두었고, 재조회 지점이 늘면 그때 검증 대상이 된다).
   */
  it('재시도가 성공하면 그때부터 잰 값을 보여 준다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockRejectedValueOnce(new Error('usage unavailable'))

    const wrapper = await renderSubscription()
    expect(barByLabel(wrapper, '스토리지')).toBeUndefined()

    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 4,
      storageUsedMb: 2048,
      storageLimitBytes: 10 * 1024 ** 3,
    } as never)
    const retry = wrapper.findAll('button').find((b) => b.text().includes('사용량 다시 시도'))
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    expect(barByLabel(wrapper, '월간 업로드')?.props('current')).toBe(4)
    expect(barByLabel(wrapper, '스토리지')?.props('max')).toBe(10)
    expect(wrapper.text()).not.toContain('사용량을 확인할 수 없습니다')
  })

  /* ── (b) 응답 전 ──────────────────────────────────────────────────── */

  /**
   * 응답이 오기 전 구간도 같다. 플랜 정보가 먼저 도착하면 카드가 열리는데, 그때
   * 막대가 0으로 그려지면 잠깐이라도 거짓 측정을 보여 준 것이다.
   */
  it('응답이 오기 전에는 사용량 막대를 그리지 않는다', async () => {
    let resolveUsage: (value: unknown) => void = () => {}
    vi.mocked(subscriptionApi.getUsage).mockReturnValue(
      new Promise((resolve) => { resolveUsage = resolve }) as never,
    )

    const wrapper = await renderSubscription()

    expect(barByLabel(wrapper, '월간 업로드')).toBeUndefined()
    expect(barByLabel(wrapper, '스토리지')).toBeUndefined()
    expect(wrapper.text()).toContain('사용량을 불러오는 중…')

    // 도착하면 그때 그린다.
    resolveUsage({ uploadsThisMonth: 7, storageUsedMb: 1024, storageLimitBytes: 50 * 1024 ** 3 })
    await flushPromises()

    expect(barByLabel(wrapper, '월간 업로드')?.props('current')).toBe(7)
    expect(wrapper.text()).not.toContain('사용량을 불러오는 중…')
  })

  /* ── (c) 서버 한도 ────────────────────────────────────────────────── */

  /**
   * **관리자가 올려 준 한도를 그대로 써야 한다.**
   *
   * 실효 한도는 `StorageQuotaUseCase.getEffectiveLimit`가 정하고, 오버라이드가 있으면
   * 플랜 상수와 다르다. 상수표를 쓰면 같은 사용자가 에셋 화면과 이 화면에서 서로 다른
   * 한도를 본다.
   */
  it('서버 한도가 플랜 기본값과 달라도 서버값을 쓴다', async () => {
    const overrideGb = 200
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 12,
      storageUsedMb: 100 * 1024,
      storageLimitBytes: overrideGb * 1024 ** 3,
    } as never)

    const wrapper = await renderSubscription()
    const storage = barByLabel(wrapper, '스토리지')

    expect(storage).toBeDefined()
    expect(storage?.props('max')).toBe(overrideGb)
    expect(storage?.props('max')).not.toBe(PRO_PLAN_STORAGE_GB)
    expect(storage?.props('current')).toBe(100)
    expect(storage?.props('unit')).toBe('GB')
  })

  /** 서버 한도가 플랜보다 **작은** 경우도 서버를 따른다 — 방향에 관계없이 서버가 권위다. */
  it('서버 한도가 플랜보다 작아도 서버값을 쓴다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 0,
      storageUsedMb: 512,
      storageLimitBytes: 1024 ** 3,
    } as never)

    const wrapper = await renderSubscription()

    expect(barByLabel(wrapper, '스토리지')?.props('max')).toBe(1)
  })

  /**
   * 한도가 0이면 `UsageProgressBar`가 100%를 그린다(`max === 0` 분기). 멀쩡한 사용자에게
   * "가득 찼다"고 말하느니 모른다고 말한다.
   */
  it('서버 한도가 0이면 저장공간을 100%로 그리지 않는다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockResolvedValue({
      uploadsThisMonth: 3,
      storageUsedMb: 0,
      storageLimitBytes: 0,
    } as never)

    const wrapper = await renderSubscription()

    expect(barByLabel(wrapper, '스토리지')).toBeUndefined()
    expect(wrapper.text()).toContain('저장공간 한도를 확인할 수 없습니다')
    // 업로드 사용량은 잰 값이므로 그대로 보여 준다.
    expect(barByLabel(wrapper, '월간 업로드')?.props('current')).toBe(3)
  })

  /* ── 무관한 지표는 영향을 받지 않는다 ─────────────────────────────── */

  /**
   * 연동 채널 수는 채널 목록에서 직접 센다. 사용량 조회가 실패해도 이 막대는 남아야
   * 한다 — 그 0은 "채널이 없다"는 실측이다.
   */
  it('사용량 조회 실패가 연동 채널 막대를 지우지 않는다', async () => {
    vi.mocked(subscriptionApi.getUsage).mockRejectedValue(new Error('usage unavailable'))

    const wrapper = await renderSubscription()

    expect(barByLabel(wrapper, '연동 채널')).toBeDefined()
  })
})
