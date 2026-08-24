import { beforeEach, describe, expect, it, vi } from 'vitest'
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
  subscriptionApi: { getCurrent: vi.fn(), startTrial: vi.fn() },
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

  it('체험을 시작하면 화면을 떠나지 않고 실행을 만들 수 있다고 알린다', async () => {
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
    expect(wrapper.text()).toContain('실행 한 번을 만들 수 있습니다')
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
})
