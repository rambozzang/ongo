import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import WeeklyDigestCard from './WeeklyDigestCard.vue'
import { aiApi } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ai', () => ({
  aiApi: { getLatestWeeklyDigest: vi.fn() },
}))

const digest = {
  id: 1,
  weekRange: '2026-08-17 ~ 2026-08-23',
  summary: '이번 주에는 짧은 영상의 완주율이 가장 높았습니다.',
  topVideos: ['1. 아침 루틴: 첫 3초 후킹이 좋았습니다.'],
  anomalies: ['평소보다 저장 수가 25% 증가했습니다.'],
  actionItems: ['첫 장면의 자막을 더 크게 유지하세요.', '금요일에 후속 영상을 예약하세요.'],
  generatedAt: '2026-08-24T09:00:00',
}

function forbidden() {
  return Object.assign(new Error('forbidden'), { response: { status: 403 } })
}

function serverError() {
  return Object.assign(new Error('server error'), { response: { status: 500 } })
}

async function render(planType: 'FREE' | 'STARTER' | 'PRO' | 'BUSINESS' = 'FREE') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { planType } as never

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/today', component: { template: '<div />' } },
      { path: '/subscription', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/today')
  await router.isReady()

  const wrapper = mount(WeeklyDigestCard, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        SectionCard: {
          template: '<section data-testid="digest-card"><header>{{ title }}<slot name="action" /></header><slot /></section>',
          props: ['title', 'meta'],
        },
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('WeeklyDigestCard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('404 상태는 빈 성공으로 숨기지 않고 무료 사용자에게 실제 플랜 이동 CTA를 보여준다', async () => {
    vi.mocked(aiApi.getLatestWeeklyDigest).mockRejectedValue(forbidden())
    const { wrapper, router } = await render('PRO')

    expect(wrapper.text()).toContain('매주 성과를 정리해 드립니다')
    const cta = wrapper.get('[data-testid="weekly-digest-upgrade"]')
    expect(cta.attributes('href')).toBe('/subscription')
    expect(wrapper.text()).not.toContain('이번 주 다이제스트를 준비 중입니다')
    expect(router.currentRoute.value.path).toBe('/today')
  })

  it('유료 사용자의 실제 다이제스트는 요약과 상세 내용을 그대로 보여준다', async () => {
    vi.mocked(aiApi.getLatestWeeklyDigest).mockResolvedValue(digest)
    const { wrapper } = await render('PRO')

    expect(wrapper.text()).toContain(digest.summary)
    expect(wrapper.text()).toContain(digest.topVideos[0])
    expect(wrapper.text()).not.toContain('플랜 살펴보기')

    await wrapper.get('button').trigger('click')
    expect(document.body.querySelector('[role="dialog"]')?.textContent).toContain(digest.summary)
  })

  it('404 이외의 장애는 재시도 상태로 보여주고 다시 누르면 API를 재호출한다', async () => {
    vi.mocked(aiApi.getLatestWeeklyDigest)
      .mockRejectedValueOnce(serverError())
      .mockResolvedValueOnce(digest)
    const { wrapper } = await render('BUSINESS')

    expect(wrapper.text()).toContain('다이제스트를 불러오지 못했습니다.')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(aiApi.getLatestWeeklyDigest).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain(digest.summary)
  })
})
