import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import koMessages from '@/locales/ko/common.json'
import NextActionsCard from './NextActionsCard.vue'
import { analyticsApi } from '@/api/analytics'

vi.mock('@/api/analytics', () => ({ analyticsApi: { nextActions: vi.fn() } }))

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div />' } }] })
  await router.push('/')
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(NextActionsCard, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return wrapper
}

describe('NextActionsCard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows a measured recommendation with its reason and links to shorts creation with the video preselected', async () => {
    vi.mocked(analyticsApi.nextActions).mockResolvedValue([
      { type: 'MAKE_SHORTS', videoId: 42, title: '김치찌개 레시피', reason: '최근 14일 조회수 1위 영상이고 아직 쇼츠로 만들지 않았습니다' },
    ])
    const wrapper = await render()

    expect(wrapper.text()).toContain('김치찌개 레시피')
    expect(wrapper.text()).toContain('조회수 1위')
    expect(wrapper.get('a').attributes('href')).toBe('/ugc/shorts/runs?videoId=42')
  })

  it('renders nothing when there is no evidence or loading fails', async () => {
    vi.mocked(analyticsApi.nextActions).mockResolvedValue([])
    expect((await render()).find('[data-testid="next-actions"]').exists()).toBe(false)

    vi.mocked(analyticsApi.nextActions).mockRejectedValue(new Error('x'))
    expect((await render()).find('[data-testid="next-actions"]').exists()).toBe(false)
  })
})
