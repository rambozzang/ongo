import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import TodayView from './TodayView.vue'
import { channelApi } from '@/api/channel'
import { scheduleApi } from '@/api/schedule'
import { inboxApi } from '@/api/inbox'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/channel', () => ({ channelApi: { list: vi.fn() } }))
vi.mock('@/api/schedule', () => ({ scheduleApi: { list: vi.fn() } }))
vi.mock('@/api/inbox', () => ({ inboxApi: { getUnreadCount: vi.fn() } }))
vi.mock('@/api/analytics', () => ({ analyticsApi: { dashboard: vi.fn() } }))

const schedule = (id: number, status: string = 'SCHEDULED') => ({
  id,
  videoId: id + 100,
  videoTitle: `오늘 영상 ${id}`,
  thumbnailUrl: null,
  scheduledAt: '2026-08-09T09:00:00',
  platforms: [{ platform: 'YOUTUBE', scheduledAt: '2026-08-09T09:00:00', status }],
  status,
  createdAt: '2026-08-01T00:00:00',
  updatedAt: '2026-08-01T00:00:00',
})

const channel = (overrides: Record<string, unknown> = {}) => ({
  id: 3,
  userId: 1,
  platform: 'YOUTUBE',
  platformChannelId: 'yt-3',
  channelName: '오늘의 채널',
  channelUrl: 'https://youtube.com/@today',
  subscriberCount: 1200,
  tokenStatus: 'ACTIVE',
  lastSyncedAt: '2026-08-09T08:00:00Z',
  ...overrides,
})

async function renderToday() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/today', component: { template: '<div />' } },
      { path: '/videos/:id', component: { template: '<div />' } },
      { path: '/channels-v2', component: { template: '<div />' } },
      { path: '/calendar-v2', component: { template: '<div />' } },
      { path: '/compose', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/today')
  await router.isReady()
  const wrapper = mount(TodayView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        KpiCard: { template: '<div class="kpi"><span>{{ label }}</span><span>{{ value }}</span></div>', props: ['label', 'value'] },
        PlatformChip: { template: '<span><slot /></span>' },
        SectionCard: { template: '<section><h2>{{ title }}</h2><slot name="action" /><slot /></section>', props: ['title'] },
        StatusPill: { template: '<span><slot /></span>' },
        ThumbPlaceholder: true,
        LoadingSpinner: true,
      },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('TodayView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(scheduleApi.list).mockResolvedValue([schedule(1), schedule(2, 'FAILED')] as never)
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel()], maxAllowed: 7, currentCount: 1 } as never)
    vi.mocked(inboxApi.getUnreadCount).mockResolvedValue({ count: 4 } as never)
    vi.mocked(analyticsApi.dashboard).mockResolvedValue({ totalViews: 12345, viewsChangePercent: 12.3 } as never)
  })

  it('renders the server queue, attention item, channel status and KPI count', async () => {
    const { wrapper, router } = await renderToday()

    expect(wrapper.text()).toContain('오늘 영상 1')
    expect(wrapper.text()).toContain('발행 실패 1건')
    expect(wrapper.text()).toContain('오늘의 채널')
    expect(wrapper.text()).toContain('4')

    const queueTitle = wrapper.findAll('p').find((node) => node.text() === '오늘 영상 1')
    expect(queueTitle).toBeDefined()
    await queueTitle!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/videos/101')
  })

  it('shows partial failure and retries against the server without fabricating data', async () => {
    vi.mocked(channelApi.list).mockRejectedValueOnce(new Error('channel down'))
    const { wrapper } = await renderToday()

    expect(wrapper.get('[role="status"]').text()).toContain('일부 오늘 데이터만 불러왔습니다')
    expect(wrapper.text()).toContain('오늘 영상 1')
    expect(wrapper.text()).not.toContain('오늘의 채널')

    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel({ tokenStatus: 'EXPIRED' })], maxAllowed: 7, currentCount: 1 } as never)
    const retry = wrapper.findAll('button').find((button) => button.text().includes('다시 시도'))
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('오늘의 채널')
    expect(wrapper.text()).toContain('연결이 만료되었습니다')
  })

  it('shows a full-load failure instead of presenting an empty successful dashboard', async () => {
    vi.mocked(scheduleApi.list).mockRejectedValueOnce(new Error('schedule down')).mockRejectedValueOnce(new Error('schedule down'))
    vi.mocked(channelApi.list).mockRejectedValueOnce(new Error('channel down'))
    vi.mocked(inboxApi.getUnreadCount).mockRejectedValueOnce(new Error('inbox down'))
    vi.mocked(analyticsApi.dashboard).mockRejectedValueOnce(new Error('analytics down'))
    const { wrapper } = await renderToday()

    expect(wrapper.get('[role="status"]').text()).toContain('오늘 데이터를 불러오지 못했습니다')
    expect(wrapper.text()).toContain('오늘 예약된 발행이 없습니다')
    expect(wrapper.text()).toContain('연결된 채널이 없습니다')
  })

  it('turns an empty day into an actionable first-post flow', async () => {
    vi.mocked(scheduleApi.list).mockResolvedValue([] as never)
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [], maxAllowed: 7, currentCount: 0 } as never)

    const { wrapper } = await renderToday()

    expect(wrapper.text()).toContain('오늘 예약된 발행이 없습니다')
    expect(wrapper.text()).toContain('첫 콘텐츠 예약하기')
    expect(wrapper.text()).toContain('채널 연결하기')
    expect(wrapper.text()).toContain('오늘 발행 0건 · 확인 필요 0건')
  })
})
