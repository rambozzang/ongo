import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import PerformanceView from './PerformanceView.vue'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/analytics', () => ({
  analyticsApi: {
    dashboard: vi.fn(),
    trends: vi.fn(),
    topVideos: vi.fn(),
    avgViewDuration: vi.fn(),
    subscriberConversion: vi.fn(),
  },
}))

async function renderPerformance() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/performance', component: { template: '<div />' } }] })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/performance')
  await router.isReady()
  const wrapper = mount(PerformanceView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        KpiCard: { template: '<div class="kpi"><span>{{ label }}</span><span>{{ value }}</span><span>{{ note }}</span></div>', props: ['label', 'value', 'note'] },
        PlatformChip: { template: '<span><slot /></span>' },
        SectionCard: { template: '<section><h2>{{ title }}</h2><slot /></section>', props: ['title'] },
        ThumbPlaceholder: true,
        ArrowDownTrayIcon: true,
        ChartBarIcon: true,
        ExclamationTriangleIcon: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

const dashboard = () => ({ totalViews: 12500, viewsChangePercent: 12.5, totalSubscribers: 400, subscribersChange: 10, totalLikes: 900, likesChangePercent: 2, creditBalance: 0, creditTotal: 0 })
const trends = () => [{ date: '2026-08-08', totalViews: 9000, platformViews: {} }, { date: '2026-08-09', totalViews: 12500, platformViews: {} }]
const topVideos = () => [{ videoId: 11, title: '성과가 좋은 영상', thumbnailUrl: null, totalViews: 12500, totalLikes: 900, publishedAt: '2026-08-08T00:00:00Z', platforms: ['YOUTUBE'] }]

describe('PerformanceView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(analyticsApi.dashboard).mockResolvedValue(dashboard() as never)
    vi.mocked(analyticsApi.trends).mockResolvedValue(trends() as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue(topVideos() as never)
    vi.mocked(analyticsApi.avgViewDuration).mockResolvedValue({ period: '30일', avgDurationSeconds: 95, data: [] } as never)
    vi.mocked(analyticsApi.subscriberConversion).mockResolvedValue({ period: '30일', totalGained: 24, data: [] } as never)
  })

  it('renders server analytics, top video and changes the requested period', async () => {
    const wrapper = await renderPerformance()

    expect(wrapper.text()).toContain('1.3만')
    expect(wrapper.text()).toContain('성과가 좋은 영상')
    expect(analyticsApi.dashboard).toHaveBeenCalledWith('30d')
    const sevenDays = wrapper.findAll('button').find((button) => button.text() === '7일')
    expect(sevenDays).toBeDefined()
    await sevenDays!.trigger('click')
    await flushPromises()
    expect(analyticsApi.dashboard).toHaveBeenLastCalledWith('7d')
  })

  it('keeps the analytics failure visible and does not enable an empty export', async () => {
    vi.mocked(analyticsApi.dashboard).mockRejectedValueOnce(new Error('analytics down'))
    vi.mocked(analyticsApi.trends).mockRejectedValueOnce(new Error('analytics down'))
    vi.mocked(analyticsApi.topVideos).mockRejectedValueOnce(new Error('analytics down'))
    vi.mocked(analyticsApi.avgViewDuration).mockRejectedValueOnce(new Error('analytics down'))
    vi.mocked(analyticsApi.subscriberConversion).mockRejectedValueOnce(new Error('analytics down'))
    const wrapper = await renderPerformance()

    expect(wrapper.text()).toContain('영상을 게시하면 24시간 내에')
    expect(wrapper.find('button[disabled]').text()).toContain('CSV')
    expect(wrapper.text()).toContain('인기 영상 데이터가 없습니다')
  })

  it('exports the loaded trend and top-video data as a CSV action', async () => {
    const createObjectUrl = vi.fn().mockReturnValue('blob:performance')
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectUrl })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const wrapper = await renderPerformance()
    const exportButton = wrapper.findAll('button').find((button) => button.text().includes('CSV'))
    expect(exportButton).toBeDefined()
    expect(exportButton!.attributes('disabled')).toBeUndefined()
    await exportButton!.trigger('click')
    expect(createObjectUrl).toHaveBeenCalledOnce()
    expect(click).toHaveBeenCalled()
    click.mockRestore()
  })
})
