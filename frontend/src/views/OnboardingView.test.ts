import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import OnboardingView from './OnboardingView.vue'
import { aiApi } from '@/api/ai'
import { authApi } from '@/api/auth'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import { capabilitiesApi } from '@/api/capabilities'
import { subscriptionApi } from '@/api/subscription'
import { useAuthStore } from '@/stores/auth'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/auth', () => ({
  authApi: {
    updateProfile: vi.fn(),
    completeOnboarding: vi.fn(),
    // 결제 성공 뒤 플랜 갱신은 authStore.fetchProfile() → authApi.getProfile() 경로다.
    getProfile: vi.fn(),
  },
}))
vi.mock('@/api/ai', () => ({ aiApi: { generateMeta: vi.fn(), getFeatures: vi.fn() } }))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn(), disconnect: vi.fn(), authorizationUrl: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { getUploadCapabilities: vi.fn() } }))
vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))
/*
 * 온보딩 플랜 카드는 **서버가 준 목록**으로 그린다. 클라이언트 상수를 쓰면 결제 금액은
 * 서버 값인데 옆의 가격·한도는 상수가 되어, 같은 화면에서 두 숫자가 갈린다.
 * 그래서 테스트도 서버 응답을 세워 준다.
 */
vi.mock('@/api/subscription', () => ({
  subscriptionApi: { getPlans: vi.fn() },
}))

function planInfo(planType: string, price: number) {
  return {
    planType,
    price,
    yearlyPrice: price * 10,
    recommended: planType === 'STARTER',
    features: {
      maxPlatforms: 3,
      monthlyUploads: 30,
      scheduleDays: 7,
      analyticsDays: 30,
      storageGB: 10,
      freeCredits: 100,
      maxTeamMembers: 0,
    },
  }
}

/** 서버가 실제로 돌려주는 네 플랜. 온보딩은 이 중 앞 세 개만 보여 준다. */
const SERVER_PLANS = {
  plans: [planInfo('FREE', 0), planInfo('STARTER', 9900), planInfo('PRO', 19900), planInfo('BUSINESS', 49900)],
  currentPlan: 'FREE' as const,
}

const COMPLETE_FAILED = koMessages.onboarding.completeFailed

function buttonWith(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((b) => b.text().includes(text))
  if (!button) throw new Error(`"${text}" 버튼을 찾지 못했습니다`)
  return button
}

async function renderOnboarding(overrides: { planType?: string } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = {
    id: 7,
    email: 'creator@example.com',
    name: '크리에이터',
    nickname: '온고',
    profileImageUrl: null,
    category: null,
    planType: overrides.planType ?? 'FREE',
    role: 'USER',
    onboardingCompleted: false,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  } as never

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/onboarding', component: { template: '<div />' } },
      { path: '/dashboard', component: { template: '<div />' } },
      { path: '/upload', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/onboarding')
  await router.isReady()

  const wrapper = mount(OnboardingView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        OnGoLogo: true,
        OnboardingStepIndicator: true,
        PlanSelectionCard: {
          props: ['plan', 'isSelected'],
          emits: ['select'],
          template: '<button type="button" :data-plan="plan.type" :data-selected="isSelected" @click="$emit(\'select\', plan.type)">{{ plan.name }}</button>',
        },
        PaymentModal: {
          props: ['modelValue', 'targetPlan', 'price'],
          emits: ['update:modelValue', 'confirm'],
          template: '<div v-if="modelValue" data-testid="onboarding-payment" :data-target-plan="targetPlan"><button type="button" @click="$emit(\'confirm\')">결제 완료</button></div>',
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

/**
 * 채널 연동은 선택이므로 "나중에 연결"로 건너뛴다.
 * 이 경로가 막히면 채널 없이 온보딩을 끝낼 수 없다는 뜻이라 테스트가 먼저 깨져야 한다.
 */
async function advanceToFinalStep(wrapper: ReturnType<typeof mount>) {
  await buttonWith(wrapper, '시작하기').trigger('click')
  await flushPromises()

  await wrapper.find('#nickname').setValue('온고크리에이터')
  await buttonWith(wrapper, '💻').trigger('click') // 카테고리: IT
  await buttonWith(wrapper, '다음').trigger('click')
  await flushPromises()

  await buttonWith(wrapper, '나중에 연결').trigger('click') // 채널 연동 건너뛰기
  await flushPromises()

  await buttonWith(wrapper, '다음').trigger('click') // 플랜 선택 → AI 체험
  await flushPromises()
}

async function advanceToPlanSelection(wrapper: ReturnType<typeof mount>) {
  await buttonWith(wrapper, '시작하기').trigger('click')
  await flushPromises()

  await wrapper.find('#nickname').setValue('온고크리에이터')
  await buttonWith(wrapper, '💻').trigger('click')
  await buttonWith(wrapper, '다음').trigger('click')
  await flushPromises()

  await buttonWith(wrapper, '나중에 연결').trigger('click')
  await flushPromises()
}

/**
 * 결제 가능 여부는 서버가 정한다. 온보딩이 따로 판단하면 구독 화면과 어긋나고,
 * 결제 설정은 배포 환경에 있어 클라이언트가 볼 수 없다.
 */
function paymentCapability(options: { enabled: boolean; reason?: string | null }) {
  vi.mocked(capabilitiesApi.list).mockResolvedValue([
    { key: 'payment', enabled: options.enabled, reason: options.reason ?? null },
  ] as never)
}

describe('OnboardingView 완료 처리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [] } as never)
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue([] as never)
    vi.mocked(authApi.updateProfile).mockResolvedValue(undefined as never)
    // 유료 흐름은 서버가 결제 가능이라고 알려줄 때만 열린다. 기본은 준비된 상태.
    paymentCapability({ enabled: true })
    vi.mocked(subscriptionApi.getPlans).mockResolvedValue(SERVER_PLANS as never)
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 5 },
    ] as never)
  })

  it('완료 API가 실패하면 완료 화면으로 넘어가지 않고 사유를 보여준다', async () => {
    vi.mocked(authApi.completeOnboarding).mockRejectedValue(new Error('서버가 응답하지 않습니다'))

    const wrapper = await renderOnboarding()
    await advanceToFinalStep(wrapper)

    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    // 서버가 onboarding_completed 를 기록하지 못했으므로 완료 화면이 나오면 안 된다.
    expect(wrapper.text()).not.toContain('환영합니다')
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('서버가 응답하지 않습니다')
    // 재시도할 수 있도록 완료 버튼이 남아 있어야 한다.
    expect(wrapper.findAll('button').some((b) => b.text().includes('완료'))).toBe(true)
  })

  it('사유를 알 수 없는 실패에는 안내 문구를 보여준다', async () => {
    vi.mocked(authApi.completeOnboarding).mockRejectedValue({})

    const wrapper = await renderOnboarding()
    await advanceToFinalStep(wrapper)

    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('환영합니다')
    expect(wrapper.text()).toContain(COMPLETE_FAILED)
  })

  it('완료 API가 성공하면 완료 화면으로 넘어간다', async () => {
    vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)

    const wrapper = await renderOnboarding()
    await advanceToFinalStep(wrapper)

    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    expect(authApi.completeOnboarding).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('환영합니다')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('운영 설정이 없는 플랫폼은 연결 버튼 없이 사유를 보여준다', async () => {
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue([
      {
        platform: 'YOUTUBE',
        directVideoUpload: true,
        cloudVideoUpload: true,
        scheduling: true,
        maxFileSizeBytes: 2_000_000_000,
        maxTitleLength: 100,
        maxDescriptionLength: 5_000,
        maxTagCount: 500,
        acceptedExtensions: ['mp4'],
        unavailableReason: null,
        configurationAvailable: true,
      },
      {
        platform: 'TIKTOK',
        directVideoUpload: true,
        cloudVideoUpload: false,
        scheduling: false,
        maxFileSizeBytes: 2_000_000_000,
        maxTitleLength: 2_000,
        maxDescriptionLength: 0,
        maxTagCount: 30,
        acceptedExtensions: ['mp4'],
        unavailableReason: null,
        configurationAvailable: false,
      },
    ] as never)

    const wrapper = await renderOnboarding()
    await buttonWith(wrapper, '시작하기').trigger('click')
    await wrapper.find('#nickname').setValue('온고크리에이터')
    await buttonWith(wrapper, '💻').trigger('click')
    await buttonWith(wrapper, '다음').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('현재 연결할 수 없는 플랫폼')
    expect(wrapper.text()).toContain('TikTok')
    expect(wrapper.findAll('button').filter((button) => button.text().includes('연동하기'))).toHaveLength(1)
  })

  /*
   * 플랜 카드의 가격·한도는 **서버가 준 값이다.** 상수로 그리면 서버에서 요금을 바꾼 날
   * 온보딩만 옛 가격을 광고하고, 결제는 서버 값으로 된다.
   */
  it('플랜 카드를 서버 응답으로 그린다', async () => {
    const wrapper = await renderOnboarding()
    await advanceToPlanSelection(wrapper)

    expect(subscriptionApi.getPlans).toHaveBeenCalled()
    // 노출 범위(앞 세 개)는 종전과 같다. BUSINESS 추가 여부는 별도 판단이라 바꾸지 않는다.
    expect(wrapper.findAll('[data-plan]').map((c) => c.attributes('data-plan')))
      .toEqual(['FREE', 'STARTER', 'PRO'])
  })

  /** **핵심.** 못 받았으면 그리지 않는다 — 오래된 가격을 대신 보여 주지 않는다. */
  it('플랜 조회에 실패하면 가격 카드를 그리지 않고 사유를 알린다', async () => {
    vi.mocked(subscriptionApi.getPlans).mockRejectedValue(new Error('플랜 서버가 응답하지 않습니다'))

    const wrapper = await renderOnboarding()
    await advanceToPlanSelection(wrapper)

    expect(wrapper.findAll('[data-plan]')).toHaveLength(0)
    expect(wrapper.find('[data-testid="onboarding-plans-error"]').text())
      .toContain('플랜 서버가 응답하지 않습니다')
  })

  /** 조회 실패로 온보딩 자체가 막히면 안 된다 — 무료 가입은 계속 가능해야 한다. */
  it('플랜 조회에 실패해도 무료로 온보딩을 마칠 수 있다', async () => {
    vi.mocked(subscriptionApi.getPlans).mockRejectedValue(new Error('플랜 서버가 응답하지 않습니다'))
    vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)

    const wrapper = await renderOnboarding()
    await advanceToFinalStep(wrapper)
    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('환영합니다')
  })

  /** 재시도로 성공하면 사유가 사라지고 서버 카드가 나타난다. */
  it('다시 시도하면 서버 목록을 다시 받는다', async () => {
    vi.mocked(subscriptionApi.getPlans).mockRejectedValue(new Error('플랜 서버가 응답하지 않습니다'))
    const wrapper = await renderOnboarding()
    await advanceToPlanSelection(wrapper)

    vi.mocked(subscriptionApi.getPlans).mockResolvedValue(SERVER_PLANS as never)
    await buttonWith(wrapper, '다시 시도').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="onboarding-plans-error"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-plan]')).toHaveLength(3)
  })

  it('유료 플랜은 선택만으로 완료하지 않고 결제 확인 뒤 다음 단계로 진행한다', async () => {
    const wrapper = await renderOnboarding()
    await advanceToPlanSelection(wrapper)

    await wrapper.find('[data-plan="STARTER"]').trigger('click')
    await buttonWith(wrapper, '다음').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="onboarding-payment"]').attributes('data-target-plan')).toBe('STARTER')
    expect(wrapper.text()).not.toContain('AI 체험')

    await buttonWith(wrapper, '결제 완료').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('AI 체험')
  })

  /**
   * 결제 직후 authStore.user.planType 이 갱신되지 않으면, planType 을 읽는 19개 화면이
   * 방금 결제한 사용자를 세션 내내 FREE 로 표시한다. 서버가 한도를 강제하므로 권한 누수는
   * 아니지만, 구매 직후 UX 가 깨지는 자리라 갱신 여부를 고정해 둔다.
   */
  async function payForStarter(wrapper: ReturnType<typeof mount>) {
    await advanceToPlanSelection(wrapper)
    await wrapper.find('[data-plan="STARTER"]').trigger('click')
    await buttonWith(wrapper, '다음').trigger('click')
    await flushPromises()

    await buttonWith(wrapper, '결제 완료').trigger('click')
    await flushPromises()
  }

  it('결제 성공 뒤 프로필을 재조회해 플랜 상태를 갱신한다', async () => {
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    // fetchProfile 은 accessToken 이 없으면 즉시 반환하므로 인증된 상태를 만들어 준다.
    auth.accessToken = 'access-token'
    vi.mocked(authApi.getProfile).mockResolvedValue({ ...auth.user, planType: 'STARTER' } as never)

    await payForStarter(wrapper)

    expect(authApi.getProfile).toHaveBeenCalledTimes(1)
    expect(auth.user?.planType).toBe('STARTER')
    expect(wrapper.text()).toContain('AI 체험')
  })

  /*
   * 아래 두 건은 "결제는 이미 서버 검증까지 끝났다"는 사실을 지킨다.
   * 갱신 실패로 되돌릴 수 있는 결제가 아니므로 사용자를 결제 화면에 가두면 안 된다.
   * 다음 단계 도달 자체가 예외를 삼켰다는 증거다 — 잡지 않으면 currentStep 에 닿지 못한다.
   */
  it('프로필 재조회 API가 실패해도 결제한 사용자를 결제 화면에 가두지 않는다', async () => {
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    auth.accessToken = 'access-token'
    vi.mocked(authApi.getProfile).mockRejectedValue(new Error('프로필 조회 실패'))

    await payForStarter(wrapper)

    expect(authApi.getProfile).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('AI 체험')
    expect(wrapper.find('[data-testid="onboarding-payment"]').exists()).toBe(false)
  })

  /*
   * 중복 청구 방어. 결제를 마친 뒤 '이전'으로 3단계에 돌아가 '다음'을 누르면 같은 구독을
   * 다시 결제할 수 있었다 — 서버 complete 의 멱등성은 paymentId 단위라 새 체크아웃은 별건으로
   * 통과하고 카드가 두 번 청구된다. 화면 상태는 새로고침에 사라지므로 서버 가드가 정본이고,
   * 여기서는 정상 조작으로 결제창이 다시 열리지 않는 것까지만 고정한다.
   */
  async function goBackToPlanStep(wrapper: ReturnType<typeof mount>) {
    await buttonWith(wrapper, '이전').trigger('click')
    await flushPromises()
  }

  it('결제한 플랜으로 다시 진행하면 결제창을 열지 않고 바로 다음 단계로 간다', async () => {
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    auth.accessToken = 'access-token'
    vi.mocked(authApi.getProfile).mockResolvedValue({ ...auth.user, planType: 'STARTER' } as never)

    await payForStarter(wrapper)
    expect(wrapper.text()).toContain('AI 체험')

    await goBackToPlanStep(wrapper)
    expect(wrapper.text()).not.toContain('AI 체험')

    await buttonWith(wrapper, '다음').trigger('click')
    await flushPromises()

    // 결제창이 다시 뜨면 그대로 두 번째 청구로 이어진다.
    expect(wrapper.find('[data-testid="onboarding-payment"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('AI 체험')
  })

  it('결제 뒤 다른 유료 플랜으로 바꾸면 그 플랜의 결제창은 다시 연다', async () => {
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    auth.accessToken = 'access-token'
    vi.mocked(authApi.getProfile).mockResolvedValue({ ...auth.user, planType: 'STARTER' } as never)

    await payForStarter(wrapper)
    await goBackToPlanStep(wrapper)

    // 업그레이드는 실제 결제가 필요하다 — 결제 표식이 플랜 단위여야 하는 이유다.
    await wrapper.find('[data-plan="PRO"]').trigger('click')
    await buttonWith(wrapper, '다음').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="onboarding-payment"]').attributes('data-target-plan')).toBe('PRO')
    expect(wrapper.text()).not.toContain('AI 체험')
  })

  /*
   * 새로고침·이탈 뒤 재진입. 결제는 서버에 남아 있는데 화면 상태는 전부 초기화되고,
   * 라우터 가드가 onboardingCompleted=false 사용자를 이 화면으로 다시 보낸다.
   * 프로필의 planType 을 초기 상태로 복원하지 않으면 같은 플랜을 다시 고르게 되고,
   * 서버 중복 결제 가드가 400 으로 막아 결제한 사용자가 온보딩을 끝내지 못한다.
   */
  describe('결제를 마친 프로필로 재진입', () => {
    it('이미 결제한 플랜이 선택된 채로 시작한다', async () => {
      const wrapper = await renderOnboarding({ planType: 'STARTER' })
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="STARTER"]').attributes('data-selected')).toBe('true')
      expect(wrapper.find('[data-plan="FREE"]').attributes('data-selected')).toBe('false')
    })

    it('같은 유료 플랜을 다시 골라도 결제창을 열지 않고 다음 단계로 진행한다', async () => {
      const wrapper = await renderOnboarding({ planType: 'STARTER' })
      await advanceToPlanSelection(wrapper)

      // 이미 결제한 플랜을 사용자가 다시 고르는 상황이 P1 의 재현 경로다.
      // FREE 는 원래 결제창 없이 통과하므로, 유료 플랜을 명시적으로 눌러야 가드를 검증한다.
      await wrapper.find('[data-plan="STARTER"]').trigger('click')
      await buttonWith(wrapper, '다음').trigger('click')
      await flushPromises()

      // 결제창이 열리면 서버 중복 가드의 400 에 막혀 여기서 진행이 끊긴다.
      expect(wrapper.find('[data-testid="onboarding-payment"]').exists()).toBe(false)
      expect(wrapper.text()).toContain('AI 체험')
    })

    it('다른 유료 플랜을 고르면 그 플랜의 결제창은 연다', async () => {
      const wrapper = await renderOnboarding({ planType: 'STARTER' })
      await advanceToPlanSelection(wrapper)

      // 업그레이드는 실제 결제가 필요하다 — 서버 가드도 상위 등급은 통과시킨다.
      await wrapper.find('[data-plan="PRO"]').trigger('click')
      await buttonWith(wrapper, '다음').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="onboarding-payment"]').attributes('data-target-plan')).toBe('PRO')
      expect(wrapper.text()).not.toContain('AI 체험')
    })

    it('무료 프로필은 기존대로 FREE 로 시작하고 유료 선택 시 결제창을 연다', async () => {
      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="FREE"]').attributes('data-selected')).toBe('true')

      await wrapper.find('[data-plan="STARTER"]').trigger('click')
      await buttonWith(wrapper, '다음').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="onboarding-payment"]').attributes('data-target-plan')).toBe('STARTER')
    })
  })

  // ---- 결제를 시작할 수 없을 때 ----

  describe('결제 불가 상태', () => {
    /**
     * 서버가 결제 불가라고 알려주면 유료 카드를 고를 수 없어야 한다.
     *
     * 고른 뒤 '다음'에서 막으면 사용자는 이미 결정을 내린 뒤에 되돌려지고, 왜인지도 모른다.
     * 네이티브 `disabled` 라 탭 순서에서도 빠지고 보조기술이 사용 불가로 읽는다.
     */
    it('결제가 불가하면 유료 카드가 비활성이고 사유를 먼저 알린다', async () => {
      paymentCapability({ enabled: false, reason: '온라인 결제를 일시적으로 사용할 수 없습니다.' })

      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="STARTER"]').attributes('disabled')).toBeDefined()
      expect(wrapper.find('[data-plan="PRO"]').attributes('disabled')).toBeDefined()
      // 무료는 언제나 고를 수 있다. 결제가 막혔다고 가입을 막을 이유가 없다.
      expect(wrapper.find('[data-plan="FREE"]').attributes('disabled')).toBeUndefined()
      expect(wrapper.text()).toContain('온라인 결제를 일시적으로 사용할 수 없습니다')
    })

    it('결제가 불가해도 무료 온보딩은 그대로 진행된다', async () => {
      paymentCapability({ enabled: false })

      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="FREE"]').attributes('data-selected')).toBe('true')
      await buttonWith(wrapper, '다음').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('AI 체험')
      expect(wrapper.find('[data-testid="onboarding-payment"]').exists()).toBe(false)
    })

    /*
     * 비활성 카드는 클릭해도 선택이 바뀌지 않는다. 그래서 '다음'을 눌러도 결제창이 아니라
     * 무료 경로로 간다.
     *
     * nextStep 안에도 같은 뜻의 2차 가드가 있지만, 카드가 비활성인 한 UI 로는 도달할 수
     * 없다. 그 가드는 두 판정(isPlanUnavailable / paymentEnabled)이 나중에 어긋날 때를
     * 위한 것이며 이 테스트가 그것을 증명하지는 않는다.
     */
    it('비활성 유료 카드는 클릭해도 선택되지 않아 결제창이 열리지 않는다', async () => {
      paymentCapability({ enabled: false })

      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)
      await wrapper.find('[data-plan="STARTER"]').trigger('click')

      expect(wrapper.find('[data-plan="FREE"]').attributes('data-selected')).toBe('true')

      await buttonWith(wrapper, '다음').trigger('click')
      await flushPromises()

      expect(wrapper.find('[data-testid="onboarding-payment"]').exists()).toBe(false)
    })

    /* 결제 가능 여부를 모르는 채 결제창을 여는 것보다 잠시 막는 편이 낫다. */
    it('capability 조회가 실패하면 사용 불가로 본다', async () => {
      vi.mocked(capabilitiesApi.list).mockRejectedValue(new Error('Network Error'))

      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="STARTER"]').attributes('disabled')).toBeDefined()
      expect(wrapper.find('[data-plan="FREE"]').attributes('disabled')).toBeUndefined()
    })

    /* 서버가 키를 안 내려주면 판단 근거가 없다. 열지 않는다. */
    it('payment 키가 없으면 사용 불가로 본다', async () => {
      vi.mocked(capabilitiesApi.list).mockResolvedValue([
        { key: 'subscription', enabled: true, reason: null },
      ] as never)

      const wrapper = await renderOnboarding()
      await advanceToPlanSelection(wrapper)

      expect(wrapper.find('[data-plan="PRO"]').attributes('disabled')).toBeDefined()
    })
  })

  it('fetchProfile 이 reject 해도 unhandled rejection 없이 다음 단계로 진행한다', async () => {
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    // 스토어가 현재는 예외를 삼키지만, 그 구현에 기대지 않고 컴포넌트 자체의 방어를 검증한다.
    auth.fetchProfile = vi.fn().mockRejectedValue(new Error('네트워크 장애'))

    await payForStarter(wrapper)

    expect(auth.fetchProfile).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('AI 체험')
  })

  /*
   * 온보딩을 막 끝낸 사용자는 채널이 0개다. 채널 연결은 플랫폼 심사에 묶여 있어
   * /compose 로 보내면 첫 화면이 막다른 길이 된다. AI 메타 생성은 스크립트만 있으면 되고
   * 영상·채널·플랫폼 승인이 전부 불필요해서, 지금 실제로 동작하는 유일한 첫 가치다.
   */
  it('완료 후 기본 착지는 AI 로 보낸다', async () => {
    vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)
    const wrapper = await renderOnboarding()
    const router = wrapper.vm.$router
    const push = vi.spyOn(router, 'push')
    await advanceToFinalStep(wrapper)
    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    await buttonWith(wrapper, koMessages.onboarding.complete.goToFirstValue).trigger('click')

    expect(push).toHaveBeenCalledWith('/ai')
  })

  /*
   * 사용자가 가려던 곳을 우리 판단으로 덮어쓰면 안 된다. 딥링크나 보호된 경로에서
   * 튕겨 온 경우의 목적지가 기본값보다 우선한다.
   */
  it('명시적으로 요청된 목적지가 기본 착지보다 우선한다', async () => {
    vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)
    const wrapper = await renderOnboarding()
    const auth = useAuthStore()
    vi.spyOn(auth, 'consumePostLoginRedirect').mockReturnValue('/videos/42')
    const push = vi.spyOn(wrapper.vm.$router, 'push')
    await advanceToFinalStep(wrapper)
    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    await buttonWith(wrapper, koMessages.onboarding.complete.goToFirstValue).trigger('click')

    expect(push).toHaveBeenCalledWith('/videos/42')
    expect(push).not.toHaveBeenCalledWith('/ai')
  })

  /*
   * 완료 화면 문구가 실제 착지와 어긋나면 사용자를 오도한다. 예전 hint 는 "영상을 업로드하고
   * 모든 플랫폼에 게시해보세요" 였는데, 방금 온보딩을 끝낸 사용자는 채널이 0개라 그게 불가능했다.
   * ko/en 양쪽이 같은 키를 갖는지도 함께 본다 — 한쪽만 고치면 다른 언어에서 키가 그대로 노출된다.
   */
  it('완료 화면 문구와 착지가 어긋나지 않는다', async () => {
    vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)
    const wrapper = await renderOnboarding()
    await advanceToFinalStep(wrapper)
    await buttonWith(wrapper, '완료').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain(koMessages.onboarding.complete.hint)
    // 업로드를 권하던 옛 문구가 남아 있으면 안 된다.
    expect(wrapper.text()).not.toContain('영상을 업로드하고 모든 플랫폼에')
    // 렌더된 문구가 키 이름 그대로면 번역이 빠진 것이다.
    expect(wrapper.text()).not.toContain('onboarding.complete')
  })

  it('완료 화면 번역 키가 ko/en 양쪽에 모두 있다', async () => {
    const en = (await import('@/locales/en/common.json')).default as typeof koMessages
    const ko = koMessages.onboarding.complete as Record<string, unknown>
    const enComplete = en.onboarding.complete as Record<string, unknown>

    expect(Object.keys(ko).sort()).toEqual(Object.keys(enComplete).sort())
    // 사문화된 키가 되살아나면 잡는다.
    expect(ko).not.toHaveProperty('goToUpload')
    expect(enComplete).not.toHaveProperty('goToUpload')
  })

  /*
   * 온보딩 AI 체험은 이제 사용자 자기 스크립트로 **정식 경로**를 부른다. 예전 데모는
   * 하드코딩 샘플이라 AI 가 돈다는 증명일 뿐 첫 가치가 되지 못했고, 인증 없는 공개
   * LLM 엔드포인트라 남용 표면이기도 했다.
   */
  describe('AI 첫 가치 체험', () => {
    const META_RESULT = {
      platforms: [
        {
          platform: 'YOUTUBE',
          titleCandidates: ['5분 김치볶음밥', '자취 필수 레시피'],
          description: '설명',
          hashtags: ['#자취요리', '#김치볶음밥'],
        },
      ],
    }

    async function reachAiStep() {
      const wrapper = await renderOnboarding()
      await advanceToFinalStep(wrapper)
      return wrapper
    }

    it('빈/공백 스크립트로는 AI 를 호출하지 않고 안내한다', async () => {
      const wrapper = await reachAiStep()
      await wrapper.find('#ai-trial-script').setValue('   ')

      // 버튼이 잠겨 호출 자체가 불가능해야 한다 — 빈 입력은 크레딧만 쓴다.
      const button = buttonWith(wrapper, koMessages.onboarding.aiTrial.tryIt)
      expect(button.attributes('disabled')).toBeDefined()
      expect(aiApi.generateMeta).not.toHaveBeenCalled()
    })

    it('사용자 스크립트를 정식 경로로 보내고 제목·해시태그를 보여준다', async () => {
      vi.mocked(aiApi.generateMeta).mockResolvedValue(META_RESULT as never)
      const wrapper = await reachAiStep()
      await wrapper.find('#ai-trial-script').setValue('오늘은 김치볶음밥을 만듭니다')
      await buttonWith(wrapper, koMessages.onboarding.aiTrial.tryIt).trigger('click')
      await flushPromises()

      expect(aiApi.generateMeta).toHaveBeenCalledTimes(1)
      const sent = vi.mocked(aiApi.generateMeta).mock.calls[0][0]
      expect(sent.script).toBe('오늘은 김치볶음밥을 만듭니다')
      expect(sent.useStt).toBe(false)

      expect(wrapper.text()).toContain('5분 김치볶음밥')
      expect(wrapper.text()).toContain('#자취요리')
    })

    it('차감되는 크레딧을 정확히 고지한다', async () => {
      const wrapper = await reachAiStep()

      // 실제로 5크레딧이 차감되므로 문구가 그 사실을 말해야 한다.
      expect(wrapper.text()).toContain(
        koMessages.onboarding.aiTrial.creditCost.replace('{count}', '5'),
      )
    })

    /*
     * AI 가 안 되는 것과 온보딩을 못 끝내는 것은 다른 문제다. 실패해도 완료 경로가 살아야 한다.
     */
    it.each([
      ['AI 호출 실패', 'AI 호출에 실패했습니다'],
      ['크레딧 부족', '크레딧이 부족합니다. 필요: 5, 잔여: 0'],
      ['응답 파싱 실패', 'AI 응답을 파싱할 수 없습니다'],
    ])('%s 여도 사유를 보여주고 온보딩을 완료할 수 있다', async (_label, reason) => {
      vi.mocked(aiApi.generateMeta).mockRejectedValue(new Error(reason))
      vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)
      const wrapper = await reachAiStep()
      await wrapper.find('#ai-trial-script').setValue('스크립트')
      await buttonWith(wrapper, koMessages.onboarding.aiTrial.tryIt).trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain(reason)
      expect(wrapper.text()).toContain(koMessages.onboarding.aiTrial.failureHint)
      // 재시도가 가능해야 한다.
      expect(buttonWith(wrapper, koMessages.onboarding.aiTrial.tryIt).exists()).toBe(true)

      // 그리고 완료로 갈 수 있어야 한다.
      await buttonWith(wrapper, '완료').trigger('click')
      await flushPromises()
      expect(wrapper.text()).toContain('환영합니다')
    })

    it('AI 를 건너뛰어도 온보딩이 완료된다', async () => {
      vi.mocked(authApi.completeOnboarding).mockResolvedValue(undefined as never)
      const wrapper = await reachAiStep()

      await buttonWith(wrapper, '건너뛰기').trigger('click')
      await flushPromises()

      expect(aiApi.generateMeta).not.toHaveBeenCalled()
      expect(wrapper.text()).toContain('환영합니다')
    })
  })
})
