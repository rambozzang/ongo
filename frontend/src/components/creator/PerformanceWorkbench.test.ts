import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'
import PerformanceWorkbench from './PerformanceWorkbench.vue'
import { useRedesignPerformanceStore } from '@/stores/redesignPerformance'
import { analyticsApi } from '@/api/analytics'
import ko from '@/locales/ko/common.json'

vi.mock('@/api/analytics', () => ({ analyticsApi: { videoCompare: vi.fn() } }))
async function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useRedesignPerformanceStore()
  store.platformComparison = [
    { platform: 'YOUTUBE', views: 100, likes: 0, comments: 5, shares: null },
  ]
  store.topVideos = [1, 2, 3].map((id) => ({
    videoId: id,
    title: `영상 ${id}`,
    totalViews: 100 * id,
    totalLikes: 5,
    thumbnailUrl: null,
    publishedAt: null,
    platforms: ['YOUTUBE'],
  }))
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/compose', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(PerformanceWorkbench, {
    global: {
      plugins: [pinia, router, createI18n({ legacy: false, locale: 'ko', messages: { ko } })],
    },
  })
  return { wrapper, store, router }
}
describe('performance workbench', () => {
  beforeEach(() => vi.clearAllMocks())
  it('uses actual platform data and keeps measured zero different from unavailable shares', async () => {
    const { wrapper } = await render()
    const platform = wrapper.findAll('tr').find((row) => row.text().includes('YOUTUBE'))!
    expect(platform.findAll('td').map((cell) => cell.text())).toEqual([
      '100',
      '0',
      '5',
      ko.analyticsView.notMeasured,
    ])
  })
  it('compares the selected ids using the currently selected period', async () => {
    vi.mocked(analyticsApi.videoCompare).mockResolvedValue({
      videos: [
        {
          videoId: 1,
          title: '비교 결과',
          totalViews: 30,
          avgDailyViews: 1,
          totalLikes: 0,
          totalComments: null,
          totalShares: null,
          totalWatchTimeSeconds: null,
          engagementRate: null,
          unavailableMetrics: ['totalComments'],
        },
      ],
    })
    const { wrapper, store } = await render()
    store.range = '7d'
    await flushPromises()
    const checkboxes = wrapper.findAll('input[type=checkbox]')
    await checkboxes[0].setValue(true)
    await checkboxes[1].setValue(true)
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('선택한 2개 비교'))!
      .trigger('click')
    await flushPromises()
    expect(analyticsApi.videoCompare).toHaveBeenCalledWith([1, 2], '7d')
    expect(wrapper.text()).toContain('비교 결과')
  })
  it('does not show a late comparison result after the period changes', async () => {
    let resolve!: (value: never) => void
    vi.mocked(analyticsApi.videoCompare).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { wrapper, store } = await render()
    await wrapper.findAll('input[type=checkbox]')[0].setValue(true)
    await wrapper.findAll('input[type=checkbox]')[1].setValue(true)
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('선택한 2개 비교'))!
      .trigger('click')
    store.range = '7d'
    await flushPromises()
    resolve({ videos: [{ videoId: 1, title: '오래된 결과' }] } as never)
    await flushPromises()
    expect(wrapper.text()).not.toContain('오래된 결과')
    expect(
      wrapper
        .findAll('input[type=checkbox]')
        .every((box) => !(box.element as HTMLInputElement).checked),
    ).toBe(true)
  })
  it('prefills a future Korean publishing time from a historical recommendation', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-09T12:00:00+09:00'))
    const { wrapper, store, router } = await render()
    store.optimalTimes = [
      {
        dayOfWeek: 5,
        dayLabel: 'FRI',
        hour: 9,
        timeLabel: '09:00',
        expectedViews: 100,
        confidenceScore: 80,
        engagementRate: null,
        score: 100,
      },
    ]
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '이 시간에 만들기')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.at).toBe('2026-10-16T09:00')
    vi.useRealTimers()
  })
})
