import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ShortsPipelineView from './ShortsPipelineView.vue'
import { ugcShortsPipelineApi } from '@/api/ugcShortsPipeline'
import { ugcShortsTemplateApi } from '@/api/ugcShortsTemplate'
import { videoApi } from '@/api/video'
import { subscriptionApi } from '@/api/subscription'
import { creditApi } from '@/api/credit'
import { useWorkspaceStore } from '@/stores/workspace'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ugcShortsPipeline', () => ({ ugcShortsPipelineApi: { list: vi.fn(), create: vi.fn() } }))
vi.mock('@/api/ugcShortsTemplate', () => ({ ugcShortsTemplateApi: { list: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { list: vi.fn() } }))
vi.mock('@/api/workspace', () => ({ workspaceApi: { list: vi.fn() } }))
vi.mock('@/api/subscription', () => ({
  subscriptionApi: { getCurrent: vi.fn(), startTrial: vi.fn(), getPlans: vi.fn() },
}))
vi.mock('@/api/credit', () => ({
  creditApi: { getBalance: vi.fn(), getTransactions: vi.fn() },
}))

const run = (overrides: Record<string, unknown> = {}) => ({
  id: 17,
  sourceVideoId: 4,
  sourceVideoTitle: '롱폼 원본 영상',
  templateId: null,
  status: 'PARTIALLY_COMPLETED',
  currentStage: 'SCHEDULE',
  clipCount: 3,
  errorMessage: '한 클립은 확인이 필요합니다',
  createdAt: '2026-08-09T10:20:00Z',
  updatedAt: '2026-08-09T10:20:00Z',
  ...overrides,
})

async function renderPipeline() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.workspaces = [{ id: 2, ownerId: 1, name: '내 작업공간', slug: 'mine', description: null, logoUrl: null, memberCount: 1, createdAt: null }]
  workspace.activeWorkspaceId = 2
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ugc/shorts/runs', component: { template: '<div />' } },
      { path: '/ugc/shorts/runs/:id', component: { template: '<div />' } },
      { path: '/ugc/shorts/prompts', component: { template: '<div />' } },
      { path: '/ugc/shorts/templates', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/ugc/shorts/runs')
  await router.isReady()
  const wrapper = mount(ShortsPipelineView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PageHeader: { template: '<header><h1>{{ title }}</h1><slot name="actions" /></header>', props: ['title'] },
        BaseModal: { template: '<div v-if="modelValue" role="dialog"><slot /><slot name="footer" /></div>', props: ['modelValue'] },
        EmptyState: true,
        LoadingSpinner: true,
        ChatBubbleLeftRightIcon: true,
        ChevronRightIcon: true,
        FilmIcon: true,
        PlusIcon: true,
        Square2StackIcon: true,
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('ShortsPipelineView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcShortsPipelineApi.list).mockResolvedValue({ content: [run()], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false } as never)
    vi.mocked(ugcShortsTemplateApi.list).mockResolvedValue([{ id: 5, name: '세로 기본', description: null, aspectRatio: '9:16', width: 1080, height: 1920, backgroundStyle: 'BLACK_BARS', hookFontFamily: null, hookFontSize: null, hookFontColor: null, hookStrokeColor: null, hookPosition: 'TOP', captionFontFamily: null, captionFontSize: null, captionFontColor: null, captionStrokeColor: null, captionPosition: 'BOTTOM', safeAreaTop: 0, safeAreaBottom: 0, referenceImageUrl: null, isDefault: true, createdAt: null, updatedAt: null }] as never)
    vi.mocked(videoApi.list).mockResolvedValue({ content: [{ id: 4, title: '롱폼 원본 영상', mediaType: 'VIDEO' }, { id: 5, title: '이미지', mediaType: 'IMAGE' }] } as never)
  })

  it('renders a partial result with its error and opens its detail route', async () => {
    const { wrapper, router } = await renderPipeline()
    expect(wrapper.text()).toContain('롱폼 원본 영상')
    expect(wrapper.text()).toContain('한 클립은 확인이 필요합니다')
    await wrapper.find('button.card').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/ugc/shorts/runs/17')
  })

  it('creates a pipeline only after selecting a real video and routes to the new run', async () => {
    vi.mocked(ugcShortsPipelineApi.create).mockResolvedValue(run({ id: 18, status: 'RUNNING' }) as never)
    const { wrapper, router } = await renderPipeline()
    const newRun = wrapper.findAll('button').find((button) => button.text().includes('새 실행'))
    expect(newRun).toBeDefined()
    await newRun!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="dialog"]').text()).toContain('롱폼 원본 영상')
    await wrapper.get('#shorts-run-video').setValue('4')
    const submit = wrapper.findAll('button').find((button) => button.text() === '실행 시작')
    expect(submit).toBeDefined()
    await submit!.trigger('click')
    await flushPromises()
    expect(ugcShortsPipelineApi.create).toHaveBeenCalledWith(2, { sourceVideoId: 4, templateId: null })
    expect(router.currentRoute.value.fullPath).toBe('/ugc/shorts/runs/18')
  })

  // ---- 완주 크레딧 부족 ----

  /** 코드는 안정 코드로만 판단한다. 서버 문구로 분기하면 번역·수정에 조용히 깨진다. */
  function rejectCreateWith(code: string, message = '서버가 준 문구') {
    const error = Object.assign(new Error(message), { response: { data: { error: code } } })
    vi.mocked(ugcShortsPipelineApi.create).mockRejectedValue(error)
  }

  async function submitCreate() {
    const { wrapper, router } = await renderPipeline()
    await wrapper.findAll('button').find((b) => b.text().includes('새 실행'))!.trigger('click')
    await flushPromises()
    await wrapper.get('#shorts-run-video').setValue('4')
    await wrapper.findAll('button').find((b) => b.text() === '실행 시작')!.trigger('click')
    await flushPromises()
    return { wrapper, router }
  }

  /*
   * 서버가 생성 전에 "이 잔액으로는 완주 불가"라고 판정한 경우다. 이때는 업그레이드가
   * 실제 해법이므로 결제 경로를 안내한다.
   */
  it('완주 크레딧이 모자라면 모달 안에서 요금제 CTA 를 보여준다', async () => {
    rejectCreateWith('SHORTS_INSUFFICIENT_CREDIT_FOR_RUN', '크레딧 37개가 필요합니다. 현재 잔여는 30개입니다.')

    const { wrapper } = await submitCreate()

    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('크레딧 37개가 필요합니다')
    // 모달과 선택한 영상이 남아야 업그레이드 후 그대로 이어서 시도할 수 있다.
    expect((wrapper.get('#shorts-run-video').element as HTMLSelectElement).value).toBe('4')
    expect(dialog.text()).toContain('요금제 보기')
  })

  it('요금제 CTA 를 누르면 구독 화면으로 이동한다', async () => {
    rejectCreateWith('SHORTS_INSUFFICIENT_CREDIT_FOR_RUN')

    const { wrapper, router } = await submitCreate()
    await wrapper.findAll('button').find((b) => b.text().includes('요금제 보기'))!.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/subscription')
  })

  /*
   * 일반 크레딧 부족은 작업 도중 어느 시점에서든 나고 원인도 여러 가지다. 여기에 결제를
   * 권하면 돈을 내도 안 풀리는 문제에 결제를 유도하는 것이고, 한 번 그러면 정작 필요한
   * 순간의 안내도 믿지 않게 된다.
   */
  it.each([
    ['CREDIT_INSUFFICIENT'],
    ['SHORTS_SOURCE_VIDEO_TOO_LARGE'],
    ['INTERNAL_SERVER_ERROR'],
  ])('%s 에는 요금제 CTA 를 붙이지 않는다', async (code) => {
    rejectCreateWith(code)

    const { wrapper } = await submitCreate()

    expect(wrapper.text()).not.toContain('요금제 보기')
  })

  /* 안정 코드가 아예 없는 오류(네트워크 등)도 기존 동작 그대로다. */
  it('코드 없는 오류에는 요금제 CTA 를 붙이지 않는다', async () => {
    vi.mocked(ugcShortsPipelineApi.create).mockRejectedValue(new Error('Network Error'))

    const { wrapper } = await submitCreate()

    expect(wrapper.text()).not.toContain('요금제 보기')
  })

  // ---- 막힌 자리에서 무료 체험 ----

  /** 서버가 내려주는 구독 상태. 자격 판정의 유일한 근거다. */
  function subscriptionState(overrides: Record<string, unknown> = {}) {
    vi.mocked(subscriptionApi.getCurrent).mockResolvedValue({
      planType: 'FREE',
      status: 'FREE',
      price: 0,
      billingCycle: 'MONTHLY',
      currentPeriodEnd: null,
      nextBillingDate: null,
      features: [],
      trialEnd: null,
      ...overrides,
    } as never)
  }

  async function blockedOnCredit() {
    rejectCreateWith('SHORTS_INSUFFICIENT_CREDIT_FOR_RUN', '크레딧 37개가 필요합니다. 현재 잔여는 30개입니다.')
    return submitCreate()
  }

  /*
   * 요금제 화면으로 보내면 그 화면에서 무료 출구가 유료 버튼 옆 보조 버튼이라
   * 결제가 유일한 해법처럼 보인다. 막힌 자리에서 무료 길을 먼저 보여준다.
   */
  it('체험 자격이 있는 무료 사용자에게 체험 시작을 먼저 보여준다', async () => {
    subscriptionState()

    const { wrapper } = await blockedOnCredit()

    expect(wrapper.text()).toContain('Starter 7일 무료 체험 시작')
    // 요금제 경로는 2차로 남는다.
    expect(wrapper.text()).toContain('요금제 보기')
  })

  /**
   * 길이를 재지 못한 영상이다. 예상치가 없으니 **부족을 확인할 수 없다** — 그런데 그건
   * 충분하다는 뜻이 아니다. 그래서 "한 번 만들 수 있다"고 단정하지 않고 다시 눌러보라는
   * 안내와 서버가 최종 판정한다는 사실만 남긴다.
   */
  it('체험을 시작하면 화면을 떠나지 않고 다시 실행하라고 안내한다', async () => {
    subscriptionState()
    vi.mocked(subscriptionApi.startTrial).mockResolvedValue({
      planType: 'STARTER', status: 'TRIALING', price: 0, billingCycle: 'MONTHLY',
      currentPeriodEnd: null, nextBillingDate: null, features: [],
    } as never)
    vi.mocked(creditApi.getBalance).mockResolvedValue({
      totalBalance: 100, freeRemaining: 100, freeMonthly: 100, purchasedBalance: 0,
      freeResetDate: '2026-09-01',
    } as never)

    const { wrapper, router } = await blockedOnCredit()
    await wrapper.findAll('button').find((b) => b.text().includes('무료 체험 시작'))!.trigger('click')
    await flushPromises()

    expect(subscriptionApi.startTrial).toHaveBeenCalledWith('STARTER')
    // 재시도에 필요한 잔액만 다시 읽는다.
    expect(creditApi.getBalance).toHaveBeenCalled()
    expect(wrapper.text()).toContain('무료 체험이 시작됐습니다')
    expect(wrapper.text()).toContain('서버가 최종 판정합니다')
    // 길이를 모르는 상태를 "충분하다" 로 바꿔 말하지 않는다.
    expect(wrapper.text()).not.toContain('실행 한 번을 만들 수 있습니다')
    // 사용자는 이미 영상을 골랐다. 요금제 화면으로 보내면 그 선택을 다시 하게 만든다.
    expect(router.currentRoute.value.fullPath).toBe('/ugc/shorts/runs')
    expect((wrapper.get('#shorts-run-video').element as HTMLSelectElement).value).toBe('4')
  })

  /* 성공한 척하지 않는다. 서버가 자격을 거절하면 차단 안내가 그대로 남아야 한다. */
  it('체험 시작이 거절되면 성공 문구 없이 차단 안내를 유지한다', async () => {
    subscriptionState()
    vi.mocked(subscriptionApi.startTrial).mockRejectedValue(new Error('이미 트라이얼을 사용한 적이 있습니다'))

    const { wrapper } = await blockedOnCredit()
    await wrapper.findAll('button').find((b) => b.text().includes('무료 체험 시작'))!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('실행 한 번을 만들 수 있습니다')
    expect(wrapper.text()).toContain('크레딧 37개가 필요합니다')
  })

  /* 이미 체험을 쓴 사용자에게 보이면 눌러도 거절되는 버튼이 된다. */
  it.each([
    ['이미 체험한 무료 사용자', { trialEnd: '2026-08-01T00:00:00Z' }],
    ['유료 사용자', { planType: 'STARTER', status: 'ACTIVE' }],
    ['체험 중인 사용자', { planType: 'STARTER', status: 'TRIALING' }],
  ])('%s 에게는 체험 시작을 보여주지 않는다', async (_label, overrides) => {
    subscriptionState(overrides)

    const { wrapper } = await blockedOnCredit()

    expect(wrapper.text()).not.toContain('무료 체험 시작')
    // 기존 요금제 경로는 그대로 남는다.
    expect(wrapper.text()).toContain('요금제 보기')
  })

  /* 구독 상태를 못 읽으면 눌러도 되는지 모른다. 보수적으로 감춘다. */
  it('구독 상태 조회가 실패하면 체험 시작을 보여주지 않는다', async () => {
    vi.mocked(subscriptionApi.getCurrent).mockRejectedValue(new Error('Network Error'))

    const { wrapper } = await blockedOnCredit()

    expect(wrapper.text()).not.toContain('무료 체험 시작')
    expect(wrapper.text()).toContain('요금제 보기')
  })

  /*
   * ── 클릭 전 잔액·부족 안내 ────────────────────────────────────────────────
   *
   * 예전에는 "예상 37 크레딧" 만 보여주고 잔액은 어디에도 없었다. 무료 사용자(30)는
   * 자기가 모자란다는 사실을 **'실행 시작' 을 누른 뒤에야** 알았다. 틀린 숫자는 없었지만
   * 판단에 필요한 정보의 절반만 준 것이다.
   *
   * 그렇다고 화면이 대신 판정하면 안 된다. 잔액·예상치 중 하나라도 모르면 아무 말도
   * 하지 않고, 알 때도 버튼을 막지 않는다 — 최종 판정은 서버가 한다.
   */
  describe('클릭 전 잔액·부족 안내', () => {
    let originalCreateElement: typeof document.createElement

    /**
     * 길이를 아는 영상을 고른 상태를 만든다.
     *
     * `preload="metadata"` 는 jsdom 에서 이벤트를 내지 않으므로, 기존 composable 테스트와
     * **같은 방식**으로 생성되는 video 요소에 길이를 심고 로드 완료를 알린다.
     * 과금 규칙 자체는 손대지 않는다 — 실제 `shortsCreditsForDuration` 이 계산한다.
     */
    function stubVideoDuration(seconds: number) {
      originalCreateElement = document.createElement.bind(document)
      vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
        const element = originalCreateElement(tag)
        if (tag === 'video') {
          const video = element as HTMLVideoElement
          Object.defineProperty(video, 'src', {
            configurable: true,
            set() {
              Object.defineProperty(video, 'duration', { configurable: true, value: seconds })
              video.onloadedmetadata?.(new Event('loadedmetadata'))
            },
            get: () => '',
          })
        }
        return element
      })
    }

    function balanceOf(totalBalance: number) {
      vi.mocked(creditApi.getBalance).mockResolvedValue({
        totalBalance, freeRemaining: totalBalance, freeMonthly: 30, purchasedBalance: 0,
        freeResetDate: '2026-09-01',
      } as never)
    }

    /** 길이를 읽을 수 있는 원본. 기본 목록은 fileUrl 이 없어 예상치가 나오지 않는다. */
    function videoWithFile() {
      vi.mocked(videoApi.list).mockResolvedValue({
        content: [{ id: 4, title: '롱폼 원본 영상', mediaType: 'VIDEO', fileUrl: 'https://cdn.example/v4.mp4' }],
      } as never)
    }

    /** 영상만 고르고 **제출하지는 않는다.** 클릭 전 상태를 본다. */
    async function selectVideo() {
      const { wrapper } = await renderPipeline()
      await wrapper.findAll('button').find((b) => b.text().includes('새 실행'))!.trigger('click')
      await flushPromises()
      await wrapper.get('#shorts-run-video').setValue('4')
      await flushPromises()
      return wrapper
    }

    afterEach(() => {
      vi.mocked(document.createElement).mockRestore?.()
    })

    it('잔액과 예상치를 모두 알면 현재 잔액과 부족분을 함께 보여준다', async () => {
      subscriptionState()
      balanceOf(30)
      videoWithFile()
      stubVideoDuration(240) // 4분 → 27 + 10 = 37

      const wrapper = await selectVideo()

      expect(wrapper.get('[data-testid="shorts-credit-estimate"]').text()).toContain('37')
      expect(wrapper.get('[data-testid="shorts-credit-balance"]').text()).toContain('30')
      // 37 - 30 = 7
      expect(wrapper.get('[data-testid="shorts-credit-shortfall"]').text()).toContain('7')
    })

    /** 잔액이 충분하면 부족을 말하지 않는다. 잔액 자체는 계속 보여준다. */
    it('잔액이 충분하면 부족 문구를 붙이지 않는다', async () => {
      subscriptionState()
      balanceOf(100)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      expect(wrapper.get('[data-testid="shorts-credit-balance"]').text()).toContain('100')
      expect(wrapper.find('[data-testid="shorts-credit-shortfall"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(false)
    })

    /** 잔액을 못 읽으면 0 으로 단언하지 않는다 — 잔여도 부족도 말하지 않는다. */
    it('잔액 조회가 실패하면 잔여도 부족도 표시하지 않는다', async () => {
      subscriptionState()
      vi.mocked(creditApi.getBalance).mockRejectedValue(new Error('Network Error'))
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-credit-balance"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shorts-credit-shortfall"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(false)
      // 규칙 안내는 그대로 남는다.
      expect(wrapper.get('[data-testid="shorts-credit-notice"]').text()).toContain('서버가')
    })

    /** 길이를 못 재면 예상치가 없다. 잔액은 알아도 부족을 판정하지 않는다. */
    it('예상치를 못 내면 잔여만 보여주고 부족은 판정하지 않는다', async () => {
      subscriptionState()
      balanceOf(30)
      // fileUrl 이 없는 기본 목록 → measure 가 아무것도 재지 않는다.

      const wrapper = await selectVideo()

      expect(wrapper.get('[data-testid="shorts-credit-balance"]').text()).toContain('30')
      expect(wrapper.find('[data-testid="shorts-credit-rule"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="shorts-credit-shortfall"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(false)
    })

    it('부족이 확실하고 체험 자격이 있으면 누르기 전에 체험을 권한다', async () => {
      subscriptionState()
      balanceOf(30)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      expect(wrapper.get('[data-testid="shorts-credit-preemptive-trial"]').text()).toContain('무료 체험 시작')
    })

    /** 이미 체험을 쓴 사용자에게는 눌러도 거절되는 버튼이 된다. */
    it('체험 자격이 없으면 사전 체험 CTA 를 보여주지 않는다', async () => {
      subscriptionState({ trialEnd: '2026-08-01T00:00:00Z' })
      balanceOf(30)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      // 부족 사실은 그대로 알린다. 감추는 것은 눌러도 소용없는 버튼뿐이다.
      expect(wrapper.find('[data-testid="shorts-credit-shortfall"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(false)
    })

    /**
     * **실행 버튼을 막지 않는다.**
     *
     * 이건 브라우저가 읽은 길이에 기반한 예상이고, 그 사이 다른 요청이 잔액을 바꿀 수도
     * 있다. 화면이 앞질러 막으면 멀쩡한 실행까지 막힌다.
     */
    it('부족이 예상돼도 실행 버튼을 비활성화하지 않는다', async () => {
      subscriptionState()
      balanceOf(30)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      const submit = wrapper.findAll('button').find((b) => b.text() === '실행 시작')!
      expect(submit.attributes('disabled')).toBeUndefined()
    })

    /*
     * ── 체험 시작 후 안내가 사실인가 ────────────────────────────────────────
     *
     * 체험은 잔액을 Starter 기준 100 으로 올린다. 그런데 완주 비용은 길이에 비례해
     * `27 + ceil(분/10) × 10` 이고 상한이 180 분이라 최대 207 까지 간다 — 100 은 70 분
     * 까지만 커버한다. 그보다 긴 영상에서 "한 번 만들 수 있다"고 하면 다시 눌렀을 때
     * 서버가 거절한다. 그 자리에서 우리가 거짓말을 한 것이 된다.
     */

    /** 체험 전 30 → 체험 후 100. 첫 조회와 재조회를 나눠야 사전 CTA 가 뜬다. */
    function balanceBeforeAndAfterTrial(before: number, after: number) {
      const of = (totalBalance: number) => ({
        totalBalance, freeRemaining: totalBalance, freeMonthly: 30, purchasedBalance: 0,
        freeResetDate: '2026-09-01',
      })
      vi.mocked(creditApi.getBalance)
        .mockResolvedValueOnce(of(before) as never)
        .mockResolvedValue(of(after) as never)
      vi.mocked(subscriptionApi.startTrial).mockResolvedValue({
        planType: 'STARTER', status: 'TRIALING', price: 0, billingCycle: 'MONTHLY',
        currentPeriodEnd: null, nextBillingDate: null, features: [],
      } as never)
    }

    async function startTrialFrom(wrapper: Awaited<ReturnType<typeof selectVideo>>) {
      await wrapper.get('[data-testid="shorts-credit-preemptive-trial"]').trigger('click')
      await flushPromises()
      return wrapper
    }

    /** 4분(37) < 100 이므로 부족이 사라진다. 그래도 "충분" 이라 단정하지는 않는다. */
    it('짧은 영상은 체험 후 부족이 사라지고 일반 재시도 안내를 보여준다', async () => {
      subscriptionState()
      balanceBeforeAndAfterTrial(30, 100)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await startTrialFrom(await selectVideo())

      expect(wrapper.text()).toContain('무료 체험이 시작됐습니다')
      expect(wrapper.text()).toContain('서버가 최종 판정합니다')
      expect(wrapper.find('[data-testid="shorts-credit-shortfall"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('더 필요합니다')
    })

    /**
     * **이 케이스가 거짓 안내가 나던 자리다.**
     *
     * 90분 → 27 + 9×10 = 117. 체험 후 잔액 100 이라 17 이 모자란다.
     */
    it('90분 영상은 체험 후에도 부족액을 알리고 충분하다고 말하지 않는다', async () => {
      subscriptionState()
      balanceBeforeAndAfterTrial(30, 100)
      videoWithFile()
      stubVideoDuration(5400) // 90분 → 117 크레딧

      const wrapper = await selectVideo()
      expect(wrapper.get('[data-testid="shorts-credit-estimate"]').text()).toContain('117')

      await startTrialFrom(wrapper)

      // 117 - 100 = 17
      expect(wrapper.text()).toContain('17')
      expect(wrapper.text()).toContain('더 필요합니다')
      expect(wrapper.text()).not.toContain('실행 한 번을 만들 수 있습니다')
      // 체험이 시작됐다는 사실만으로 "다시 누르면 된다" 고 하지 않는다.
      expect(wrapper.text()).not.toContain('서버가 최종 판정합니다')
      // 부족 표시도 새 잔액 기준으로 갱신된다.
      expect(wrapper.get('[data-testid="shorts-credit-shortfall"]').text()).toContain('17')
    })

    /*
     * ── 체험이 이 영상을 커버하는가 ─────────────────────────────────────────
     *
     * 체험은 1회성이다. 커버하지 못하는 영상에서 쓰게 두면 결과물 없이 사라진다.
     * 판정 기준은 **서버가 내려준 STARTER 크레딧**이며 화면에 박아 두지 않는다 —
     * 아래 `STARTER 가 150 이면…` 테스트가 그 사실을 값으로 증명한다.
     */

    /** `/subscriptions/plans` 응답. STARTER 의 freeCredits 만 바꿔 가며 쓴다. */
    function plansWithStarterCredits(freeCredits: number) {
      const features = (c: number) => ({
        maxPlatforms: 3, monthlyUploads: 30, scheduleDays: 7,
        analyticsDays: 30, storageGB: 10, freeCredits: c, maxTeamMembers: 0,
      })
      vi.mocked(subscriptionApi.getPlans).mockResolvedValue({
        plans: [
          { planType: 'FREE', price: 0, yearlyPrice: 0, features: features(30), recommended: false },
          { planType: 'STARTER', price: 9900, yearlyPrice: 99000, features: features(freeCredits), recommended: false },
        ],
        currentPlan: 'FREE',
      } as never)
    }

    /** 90분 → 27 + 9×10 = 117 크레딧. */
    const NINETY_MINUTES = 5400

    it('STARTER 100 으로 커버 못 하는 90분 영상은 체험 CTA 대신 대체 안내를 보여준다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(100)
      videoWithFile()
      stubVideoDuration(NINETY_MINUTES)

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(false)
      const notice = wrapper.get('[data-testid="shorts-trial-not-enough"]')
      expect(notice.text()).toContain('117')  // 이 영상 비용
      expect(notice.text()).toContain('100')  // 체험 크레딧 (서버 값)
      expect(notice.text()).toContain('70')   // (100-27)/10 → 7구간 → 70분
      expect(wrapper.find('[data-testid="shorts-pick-shorter-video"]').exists()).toBe(true)
      expect(notice.text()).toContain('요금제 보기')
    })

    it('더 짧은 영상 고르기를 누르면 선택만 비우고 모달은 남는다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(100)
      videoWithFile()
      stubVideoDuration(NINETY_MINUTES)

      const wrapper = await selectVideo()
      await wrapper.get('[data-testid="shorts-pick-shorter-video"]').trigger('click')
      await flushPromises()

      expect((wrapper.get('#shorts-run-video').element as HTMLSelectElement).value).toBe('0')
      // 모달을 닫으면 사용자가 처음부터 다시 열어야 한다.
      expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    })

    it('체험으로 커버되는 4분 영상은 체험 CTA 를 그대로 보여준다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(100)
      videoWithFile()
      stubVideoDuration(240)

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="shorts-trial-not-enough"]').exists()).toBe(false)
    })

    /**
     * **체험 크레딧을 화면에 박지 않았다는 증거.**
     *
     * 같은 90분 영상(117)인데 서버가 150 을 주면 커버되므로 CTA 가 살아나야 한다.
     * 100 이 하드코딩돼 있으면 이 테스트가 깨진다.
     */
    it('STARTER 가 150 이면 같은 90분 영상에도 체험 CTA 를 보여준다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(150)
      videoWithFile()
      stubVideoDuration(NINETY_MINUTES)

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="shorts-trial-not-enough"]').exists()).toBe(false)
    })

    /** 모른다는 이유로 무료 경로를 없애지 않는다. */
    it('플랜 조회가 실패하면 체험 CTA 를 종전대로 보여준다', async () => {
      subscriptionState()
      balanceOf(30)
      vi.mocked(subscriptionApi.getPlans).mockRejectedValue(new Error('Network Error'))
      videoWithFile()
      stubVideoDuration(NINETY_MINUTES)

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-credit-preemptive-trial"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="shorts-trial-not-enough"]').exists()).toBe(false)
    })

    /** 길이를 모르면 비용도 모른다. 커버 여부를 판정하지 않는다. */
    it('길이를 재지 못하면 커버 여부를 판정하지 않는다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(100)
      // fileUrl 없는 기본 목록 → 예상치 없음

      const wrapper = await selectVideo()

      expect(wrapper.find('[data-testid="shorts-trial-not-enough"]').exists()).toBe(false)
    })

    /**
     * 서버가 거절한 뒤의 CTA 에도 같은 조건이 걸려야 한다.
     * 여기만 열어 두면 사전 안내로 막은 소진이 한 단계 뒤에서 그대로 일어난다.
     */
    it('서버가 거절한 뒤에도 커버 못 하는 영상에는 체험 CTA 를 열지 않는다', async () => {
      subscriptionState()
      balanceOf(30)
      plansWithStarterCredits(100)
      videoWithFile()
      stubVideoDuration(NINETY_MINUTES)
      rejectCreateWith('SHORTS_INSUFFICIENT_CREDIT_FOR_RUN', '크레딧 117개가 필요합니다.')

      const wrapper = await selectVideo()
      await wrapper.findAll('button').find((b) => b.text() === '실행 시작')!.trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('크레딧 117개가 필요합니다')
      expect(wrapper.text()).not.toContain('무료 체험 시작')
      // 결제 경로는 남는다.
      expect(wrapper.text()).toContain('요금제 보기')
    })

    /** 체험 자체가 거절되면 성공 문구를 어느 쪽도 띄우지 않는다. */
    it('체험 시작이 실패하면 성공 문구를 보여주지 않는다', async () => {
      subscriptionState()
      balanceOf(30)
      videoWithFile()
      stubVideoDuration(240)
      vi.mocked(subscriptionApi.startTrial).mockRejectedValue(new Error('이미 트라이얼을 사용한 적이 있습니다'))

      const wrapper = await startTrialFrom(await selectVideo())

      expect(wrapper.text()).not.toContain('무료 체험이 시작됐습니다')
      expect(wrapper.text()).not.toContain('더 필요합니다')
      // 부족 사실은 체험 전 잔액 기준으로 그대로 남는다.
      expect(wrapper.get('[data-testid="shorts-credit-shortfall"]').text()).toContain('7')
    })
  })
})
