import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import PerformanceView from './PerformanceView.vue'
import { analyticsApi } from '@/api/analytics'
import { scheduleApi } from '@/api/schedule'
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
vi.mock('@/api/schedule', () => ({ scheduleApi: { list: vi.fn() } }))

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
    vi.mocked(scheduleApi.list).mockResolvedValue([{ status: 'PUBLISHED' }, { status: 'FAILED' }] as never)
  })

  it('renders server analytics, top video and changes the requested period', async () => {
    const wrapper = await renderPerformance()

    expect(wrapper.text()).toContain('1.3만')
    expect(wrapper.text()).toContain('1')
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
    vi.mocked(scheduleApi.list).mockRejectedValueOnce(new Error('schedule down'))
    const wrapper = await renderPerformance()

    expect(wrapper.text()).toContain('영상을 게시하면 24시간 내에')
    expect(wrapper.find('button[disabled]').text()).toContain('CSV')
    expect(wrapper.text()).toContain('인기 영상 데이터가 없습니다')
  })

  /**
   * **`String(null)` 은 문자열 `"null"` 을 만든다.**
   *
   * 서버는 그 지표를 주는 업로드가 없거나 기간에 집계 행이 없으면 `totalViews = null` 을
   * 준다. 예전 내보내기는 그 자리에 `"null"` 을 찍었고, 스프레드시트에서 그 열을 합계
   * 내면 조용히 빠지거나 오류가 됐다. `?? 0` 도 안 된다 — 재지 않은 것이 "0회" 가 된다.
   */
  it('내보내기에 null 을 문자열 null 이나 0 으로 쓰지 않는다', async () => {
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([
      { ...topVideos()[0], totalViews: null },
    ] as never)
    const blobs: Blob[] = []
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: (blob: Blob) => {
        blobs.push(blob)
        return 'blob:performance'
      },
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const wrapper = await renderPerformance()

    await wrapper.findAll('button').find((button) => button.text().includes('CSV'))!.trigger('click')
    const csv = await blobs[0].text()

    expect(csv).toContain(koMessages.analyticsView.notMeasured)
    expect(csv).not.toContain('null')
    // 영상 행이 `"제목","0"` 으로 나가면 실측 0 과 구분되지 않는다.
    expect(csv).not.toContain('"성과가 좋은 영상","0"')
    click.mockRestore()
  })

  /** **측정된 0 은 관측이다.** 내보내기에서도 숫자 0 으로 남아야 한다. */
  it('내보내기에서 실측 0 은 0 으로 남는다', async () => {
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([
      { ...topVideos()[0], totalViews: 0 },
    ] as never)
    const blobs: Blob[] = []
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: (blob: Blob) => {
        blobs.push(blob)
        return 'blob:performance'
      },
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const wrapper = await renderPerformance()

    await wrapper.findAll('button').find((button) => button.text().includes('CSV'))!.trigger('click')
    const csv = await blobs[0].text()

    expect(csv).toContain('"성과가 좋은 영상","0"')
    expect(csv).not.toContain(koMessages.analyticsView.notMeasured)
    click.mockRestore()
  })

  /** 화면에서도 미측정은 0 이 아니라 문구다. */
  it('미측정 조회수를 표에서 0 으로 그리지 않는다', async () => {
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([
      { ...topVideos()[0], totalViews: null },
    ] as never)

    const wrapper = await renderPerformance()

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
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
