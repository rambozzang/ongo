import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import OnboardingView from './OnboardingView.vue'
import { authApi } from '@/api/auth'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import { subscriptionApi } from '@/api/subscription'
import { useAuthStore } from '@/stores/auth'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/auth', () => ({
  authApi: {
    updateProfile: vi.fn(),
    completeOnboarding: vi.fn(),
  },
}))
vi.mock('@/api/ai', () => ({ aiApi: { demoGenerate: vi.fn() } }))
vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn(), disconnect: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { getUploadCapabilities: vi.fn() } }))
vi.mock('@/api/subscription', () => ({ subscriptionApi: { changePlan: vi.fn() } }))

const COMPLETE_FAILED = koMessages.onboarding.completeFailed

function buttonWith(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((b) => b.text().includes(text))
  if (!button) throw new Error(`"${text}" 버튼을 찾지 못했습니다`)
  return button
}

async function renderOnboarding() {
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
    planType: 'FREE',
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
        PlanSelectionCard: true,
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

describe('OnboardingView 완료 처리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [] } as never)
    vi.mocked(videoApi.getUploadCapabilities).mockResolvedValue([] as never)
    vi.mocked(authApi.updateProfile).mockResolvedValue(undefined as never)
    vi.mocked(subscriptionApi.changePlan).mockResolvedValue(undefined as never)
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
})
