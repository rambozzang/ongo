import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import VideoDetailView from './VideoDetailView.vue'
import { videoApi } from '@/api/video'
import { analyticsApi } from '@/api/analytics'
import { useNotificationStore } from '@/stores/notification'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/video', () => ({
  videoApi: {
    get: vi.fn(),
    recheckUpload: vi.fn(),
    retryUpload: vi.fn(),
  },
}))
vi.mock('@/api/analytics', () => ({ analyticsApi: { videoAnalytics: vi.fn() } }))

const video = (uploadStatus: 'PUBLISHED' | 'UNCONFIRMED' = 'UNCONFIRMED') => ({
  id: 42,
  userId: 7,
  title: '게시 확인 테스트',
  description: null,
  tags: [],
  category: null,
  mediaType: 'VIDEO',
  fileUrl: 'https://storage.test/video.mp4',
  thumbnailUrl: null,
  thumbnailCandidates: [],
  fileSize: 4,
  status: uploadStatus,
  visibility: 'PUBLIC',
  createdAt: '2026-08-10T00:00:00Z',
  updatedAt: '2026-08-10T00:00:00Z',
  uploads: [{
    id: 101,
    videoId: 42,
    platform: 'YOUTUBE',
    channelId: 9,
    channelName: '테스트 채널',
    status: uploadStatus,
    platformVideoId: uploadStatus === 'PUBLISHED' ? 'yt-1' : 'publish-1',
    platformUrl: uploadStatus === 'PUBLISHED' ? 'https://youtube.test/watch/yt-1' : null,
    description: null,
    tags: [],
    errorMessage: uploadStatus === 'UNCONFIRMED' ? '응답을 확인하지 못했습니다.' : null,
    publishedAt: uploadStatus === 'PUBLISHED' ? '2026-08-10T00:01:00Z' : null,
    createdAt: '2026-08-10T00:00:00Z',
    meta: null,
  }],
})

async function renderDetail() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/videos/:id', component: { template: '<div />' } }],
  })
  await router.push('/videos/42')
  await router.isReady()
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(VideoDetailView, {
    props: { id: '42' },
    shallow: true,
    global: { plugins: [pinia, router, i18n] },
  })
  await flushPromises()
  return { wrapper, pinia }
}

describe('VideoDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(videoApi.get).mockResolvedValue(video() as never)
    vi.mocked(videoApi.recheckUpload).mockRejectedValue(new Error('게시 상태 API 장애'))
    vi.mocked(videoApi.retryUpload).mockResolvedValue(undefined as never)
    vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([] as never)
  })

  it('shows a toast when publish recovery fails instead of swallowing the error', async () => {
    const { wrapper, pinia } = await renderDetail()
    const recover = wrapper.findAll('button').find((button) => button.text() === '게시 결과 재확인')
    expect(recover).toBeDefined()

    await recover!.trigger('click')
    await flushPromises()

    const notification = useNotificationStore(pinia)
    expect(notification.toasts.some((toast) => toast.message === '게시 상태 API 장애')).toBe(true)
  })

  it('renders a usable provider link only when the server returns one', async () => {
    vi.mocked(videoApi.get).mockResolvedValue(video('PUBLISHED') as never)
    const { wrapper } = await renderDetail()

    const link = wrapper.get('a[href="https://youtube.test/watch/yt-1"]')
    expect(link.text()).toBe('플랫폼에서 열기')
  })

  /**
   * KPI 칸이 **"모름" 과 "0" 을 구분**하는지.
   *
   * 예전에는 `?? 0` 이라 로딩·오류·미집계가 전부 "0" 으로 나왔다. 오류 배너와 "0" 이
   * 동시에 뜨면 크리에이터는 어느 쪽을 믿어야 할지 알 수 없다.
   */
  describe('성과 지표 표시', () => {
    /**
     * 서버는 수집이 없는 업로드에도 **합계 0 인 행**을 만든다(`AnalyticsUseCase`의
     * `uploads.map { }`). 그래서 행 유무는 `hasData`(= dailyData 존재)로만 갈린다.
     */
    const analytics = (views: number, hasData = true) => [{
      platform: 'YOUTUBE',
      views,
      likes: 0,
      comments: 0,
      shares: 0,
      hasData,
      dailyTrend: hasData
        ? [{ date: '2026-08-10', totalViews: views, platformViews: {} }]
        : [],
    }]

    it('조회 실패 시 0 이 아니라 불러오지 못함으로 표시한다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockRejectedValue(new Error('분석 API 장애'))

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('불러오지 못함')
      expect(wrapper.text()).not.toContain('불러오는 중')
    })

    /** 응답에 그 플랫폼 행 자체가 없는 경우. */
    it('플랫폼 행이 없으면 미측정으로 표시한다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('미측정')
      expect(wrapper.text()).not.toContain('불러오지 못함')
    })

    /**
     * **(a) 이번 수정의 핵심.** 서버는 수집이 없어도 합계 0 인 행을 만든다. 행은 있고
     * 값도 숫자 0 이지만 실제로는 아무것도 수집되지 않은 상태다.
     */
    it('집계 행 없이 합계만 0 이면 미측정으로 표시한다', async () => {
      vi.mocked(analyticsApi.videoAnalytics)
        .mockResolvedValue(analytics(0, false) as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('미측정')
      expect(wrapper.text()).not.toContain('불러오지 못함')
    })

    /** **(b) 집계 행이 있는 실제 0 은 0 이다.** 이것까지 가리면 반대 거짓말이 된다. */
    it('집계가 있는 실제 0 은 0 으로 표시한다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue(analytics(0) as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).not.toContain('미측정')
      expect(wrapper.text()).not.toContain('불러오지 못함')
    })

    it('정상 조회한 값은 그대로 표시한다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue(analytics(4200) as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('4.2천')
      expect(wrapper.text()).not.toContain('미측정')
    })

    it('데이터가 없는 상태의 실패에는 오래된 값 안내를 띄우지 않는다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockRejectedValue(new Error('분석 API 장애'))

      const { wrapper } = await renderDetail()

      expect(wrapper.find('[data-testid="stale-analytics-notice"]').exists()).toBe(false)
    })

    /**
     * **(c) 실제 버튼 경로로 검증한다.**
     *
     * 성공한 뒤에도 누를 수 있는 새로고침 버튼이 있어야 "성공 → 재조회 실패" 에 도달한다.
     * 그때 이미 확인한 숫자는 남기고, 최신이 아닐 수 있다는 사실만 따로 알린다.
     */
    it('새로고침이 실패하면 이전 값을 유지하고 최신이 아님을 알린다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue(analytics(4200) as never)
      const { wrapper } = await renderDetail()
      expect(wrapper.text()).toContain('4.2천')
      expect(wrapper.find('[data-testid="stale-analytics-notice"]').exists()).toBe(false)

      vi.mocked(analyticsApi.videoAnalytics).mockRejectedValue(new Error('재조회 실패'))
      await wrapper.get('[data-testid="analytics-refresh"]').trigger('click')
      await flushPromises()

      // 값은 남는다 — 이미 확인한 숫자를 오류 하나로 지우지 않는다.
      expect(wrapper.text()).toContain('4.2천')
      expect(wrapper.text()).not.toContain('불러오지 못함')
      // 신선도는 속이지 않는다.
      expect(wrapper.find('[data-testid="stale-analytics-notice"]').exists()).toBe(true)
    })

    it('새로고침이 성공하면 오래된 값 안내가 사라진다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockRejectedValueOnce(new Error('첫 조회 실패'))
      const { wrapper } = await renderDetail()
      expect(wrapper.text()).toContain('불러오지 못함')

      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue(analytics(4200) as never)
      await wrapper.get('[data-testid="analytics-refresh"]').trigger('click')
      await flushPromises()

      expect(wrapper.text()).toContain('4.2천')
      expect(wrapper.find('[data-testid="stale-analytics-notice"]').exists()).toBe(false)
    })

    /** 진행 중 중복 요청을 막는다. */
    it('조회 중에는 새로고침 버튼이 비활성화된다', async () => {
      let release: (() => void) | undefined
      vi.mocked(analyticsApi.videoAnalytics).mockImplementation(
        () => new Promise((resolve) => { release = () => resolve(analytics(1) as never) }),
      )
      const { wrapper } = await renderDetail()

      expect(wrapper.get('[data-testid="analytics-refresh"]').attributes('disabled')).toBeDefined()
      release?.()
      await flushPromises()
      expect(wrapper.get('[data-testid="analytics-refresh"]').attributes('disabled')).toBeUndefined()
    })

    /**
     * **플랫폼 비교 차트의 가짜 0.**
     *
     * 서버는 수집이 없는 업로드에도 합계 0 인 행을 만든다. 그 행을 그대로 그리면 막대 0 과
     * 숫자 "0" 이 나오고, 크리에이터는 그것을 "조회수 0회" 로 읽는다. 바로 위 KPI 칸은 이미
     * `hasData` 로 둘을 구분하므로, 비교 차트만 예외로 두면 **같은 화면이 서로 다른 답**을
     * 준다.
     */
    it('수집된 적 없는 플랫폼은 비교 차트에 0으로 그리지 않는다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 4200, likes: 10, comments: 2, shares: 1, hasData: true,
          dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }] },
        { platform: 'TIKTOK', views: 0, likes: 0, comments: 0, shares: 0, hasData: false,
          dailyTrend: [] },
      ] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('4.2천')
      // 수집된 플랫폼만 막대를 갖는다. 수집 전 플랫폼의 행이 있으면 그 자리에 "0"이 찍힌다.
      expect(wrapper.find('[data-testid="comparison-row-YOUTUBE"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="comparison-row-TIKTOK"]').exists()).toBe(false)
    })

    /**
     * 조용히 빼면 "그 플랫폼에 안 올렸나?" 로 읽힌다. 올리긴 했고 아직 수집되지 않았다는
     * 사실을 그대로 말해야 한다.
     */
    it('비교에서 제외한 플랫폼을 이름과 함께 알린다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 4200, likes: 0, comments: 0, shares: 0, hasData: true,
          dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }] },
        { platform: 'TIKTOK', views: 0, likes: 0, comments: 0, shares: 0, hasData: false,
          dailyTrend: [] },
      ] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('비교에서 제외')
      expect(wrapper.text()).toContain('TikTok')
    })

    /** 전부 수집 전이면 0 막대를 늘어놓지 말고 미수집 안내만 보여준다. */
    it('모든 플랫폼이 수집 전이면 비교 차트 대신 안내를 보여준다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 0, likes: 0, comments: 0, shares: 0, hasData: false,
          dailyTrend: [] },
      ] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('분석 데이터가 없습니다')
    })

    /**
     * `hasData` 가 없는 옛 응답은 **판단 불가**다. 숨기면 실제 데이터를 감출 수 있고,
     * 그건 가짜 0 보다 나쁘다. 그대로 그린다.
     */
    it('hasData 가 없는 옛 응답은 숨기지 않는다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 4200, likes: 0, comments: 0, shares: 0,
          dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }] },
      ] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.text()).toContain('4.2천')
      expect(wrapper.text()).not.toContain('비교에서 제외')
    })

    /**
     * **자체 성과 점수의 가짜 점수.**
     *
     * `calculateVideoScore` 의 커버리지 항목은 업로드 개수만으로 점수가 나온다
     * (플랫폼 수 × 25, 가중치 0.15). 그래서 집계가 하나도 없는 신규 영상도 0 이 아닌
     * 총점을 받는다 — 2개 플랫폼이면 커버리지 50 → 총점 8점. 조회·참여·성장은 전부 0 인데
     * "8점"만 보이면 성과가 나빴다는 뜻으로 읽힌다.
     */
    it('수집된 성과가 없으면 자체 점수를 계산하지 않는다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 0, likes: 0, comments: 0, shares: 0, hasData: false,
          dailyTrend: [] },
      ] as never)

      const { wrapper } = await renderDetail()

      expect(wrapper.find('[data-testid="local-score-unavailable"]').exists()).toBe(true)
      expect(wrapper.text()).toContain('아직 수집된 성과가 없어 점수를 계산할 수 없습니다')
    })

    it('수집된 성과가 있으면 자체 점수 카드를 보여준다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 4200, likes: 100, comments: 5, shares: 2, hasData: true,
          dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }] },
      ] as never)

      const { wrapper } = await renderDetail()

      // 이 하네스는 `shallow: true` 라 자식 컴포넌트가 스텁된다. 카드 내부 문구는
      // `PerformanceScore.test.ts` 가 직접 마운트해서 확인한다.
      expect(wrapper.find('[data-testid="local-score-unavailable"]').exists()).toBe(false)
    })

    /** 새로고침이 플랫폼별 데이터를 섞지 않는지. */
    it('새로고침 후에도 선택한 플랫폼 값만 보여준다', async () => {
      vi.mocked(analyticsApi.videoAnalytics).mockResolvedValue([
        { platform: 'YOUTUBE', views: 4200, likes: 0, comments: 0, shares: 0, hasData: true,
          dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }] },
        { platform: 'TIKTOK', views: 9900000, likes: 0, comments: 0, shares: 0, hasData: true,
          dailyTrend: [{ date: '2026-08-10', totalViews: 9900000, platformViews: {} }] },
      ] as never)

      const { wrapper } = await renderDetail()
      await wrapper.get('[data-testid="analytics-refresh"]').trigger('click')
      await flushPromises()

      // 선택된 플랫폼은 YOUTUBE 하나뿐이다(video fixture 의 uploads).
      expect(wrapper.text()).toContain('4.2천')
    })
  })
})
