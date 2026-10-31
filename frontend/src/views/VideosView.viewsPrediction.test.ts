import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import VideosView from './VideosView.vue'
import { analyticsApi } from '@/api/analytics'
import { videoApi } from '@/api/video'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/repurpose', () => ({ repurposeApi: { analyzeForRepurpose: vi.fn() } }))
vi.mock('@/api/metaRewrite', () => ({ metaRewriteApi: { rewrite: vi.fn() } }))
vi.mock('@/api/publishChecklist', () => ({ publishChecklistApi: { get: vi.fn() } }))
vi.mock('@/api/videoSeo', () => ({ videoSeoApi: { analyze: vi.fn() } }))
vi.mock('@/api/analytics', () => ({ analyticsApi: { performanceScore: vi.fn() } }))
vi.mock('@/api/video', () => ({ videoApi: { feed: vi.fn(), list: vi.fn() } }))
vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/compose', component: { template: '<div />' } },
    { path: '/subscription', component: { template: '<div />' } },
  ],
})

const internalVideo = {
  videoId: 123,
  platformVideoId: 'pv-1',
  platform: 'YT',
  channelName: 'ch',
  title: '내 영상',
  description: null,
  thumbnailUrl: null,
  platformUrl: null,
  viewCount: 100,
  likeCount: null,
  commentCount: null,
  shareCount: null,
  publishedAt: null,
} as const

interface PredictVm {
  openPredictModal: (item: unknown) => void
  runPrediction: () => Promise<void>
}

/**
 * 조회수 예측 버튼은 백엔드에 없는 주소(/videos/{id}/predict-views)를 불러 늘 실패했다.
 * 이제 실측 일별 조회수 추세(성과 점수의 prediction7d)를 보여 주고, 부족하면 이유를 말한다.
 */
describe('VideosView 조회수 예측 — 실측 추세', () => {
  let wrapper: ReturnType<typeof mount>

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(videoApi.feed).mockResolvedValue({ items: [internalVideo], platforms: [], errors: [] } as never)
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
    vi.clearAllMocks()
  })

  function mountView() {
    return mount(VideosView, {
      global: {
        plugins: [createPinia(), router, i18n],
        stubs: { PageHeader: true, LoadingSpinner: true, EmptyState: true, SectionCard: true, ThumbPlaceholder: true, PlatformChip: true, VideoDetailPanel: true },
      },
    })
  }

  async function run() {
    wrapper = mountView()
    const vm = wrapper.vm as unknown as PredictVm
    vm.openPredictModal(internalVideo as never)
    await flushPromises()
    await vm.runPrediction()
    await flushPromises()
  }

  it('측정 추세가 있으면 7일 예측과 근거를 보여 준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue({ videoId: 123, prediction7d: 4321, trend: 'up', unavailableMetrics: {} } as never)

    await run()

    expect(analyticsApi.performanceScore).toHaveBeenCalledWith(123)
    const result = document.querySelector('[data-testid="views-prediction-result"]')!
    expect(result.textContent).toContain('4.3K')
    expect(result.textContent).toContain('최근 조회수가 늘고 있습니다')
    expect(result.textContent).toContain('실측 일별 조회수')
  })

  it('데이터가 부족하면 숫자를 지어내지 않고 서버가 준 이유를 보여 준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue({
      videoId: 123, prediction7d: null, trend: null, unavailableMetrics: { prediction7d: '일별 조회수가 4일 이상 쌓여야 예측합니다' },
    } as never)

    await run()

    const box = document.querySelector('[data-testid="views-prediction-unavailable"]')!
    expect(box.textContent).toContain('4일 이상')
  })
})
