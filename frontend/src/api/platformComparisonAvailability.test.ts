import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import apiClient from './client'
import { analyticsApi } from './analytics'
import PlatformPieChart from '@/components/dashboard/PlatformPieChart.vue'
import type { PlatformComparison } from '@/types/analytics'

/**
 * 플랫폼 비교 계약이 **미지원 지표를 숫자로 되돌리지 않는지** 고정한다.
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
 * 서버는 이제 그 자리에 `null` 과 `unavailableMetrics` 를 준다.
 *
 * 지금 화면(`PlatformPieChart`)은 `views` 만 쓰지만, **향후 소비자가 미지원 지표를 숫자로
 * 보지 않도록 계약을 여기서 고정한다.** 매퍼가 `?? 0` 한 줄만 넣어도 서버 수정이
 * 통째로 무의미해진다.
 */
describe('플랫폼 비교 미지원 지표 계약', () => {
  const get = vi.spyOn(apiClient, 'get')

  function givenPlatforms(platforms: unknown[]) {
    get.mockResolvedValue({ data: { success: true, data: { platforms } } } as never)
  }

  beforeEach(() => vi.clearAllMocks())

  it('서버가 null을 주면 지표를 0으로 만들지 않는다', async () => {
    givenPlatforms([{
      platform: 'FACEBOOK', views: 500, likes: 40, comments: 12,
      shares: null, unavailableMetrics: ['shares'],
    }])

    const facebook = (await analyticsApi.platformComparison('30d'))[0]

    expect(facebook.shares).toBeNull()
    expect(facebook.unavailableMetrics).toContain('shares')
    // 지원 지표는 그대로 살아 있어야 한다.
    expect(facebook.views).toBe(500)
    expect(facebook.likes).toBe(40)
  })

  /**
   * Pinterest 는 댓글도 공유도 주지 않는다.
   *
   * 공유 자리에 있던 값은 `PinterestClient.kt:160` 의 `metrics["PIN_CLICK"]` — 핀을
   * **클릭한 횟수**이지 공유가 아니었다. 서버가 이제 둘 다 `null` 로 준다.
   */
  it('Pinterest 댓글과 공유가 null로 전달된다', async () => {
    givenPlatforms([{
      platform: 'PINTEREST', views: 300, likes: 20,
      comments: null, shares: null, unavailableMetrics: ['comments', 'shares'],
    }])

    const pinterest = (await analyticsApi.platformComparison('30d'))[0]

    expect(pinterest.comments).toBeNull()
    expect(pinterest.shares).toBeNull()
    // 실제로 조회하는 값(노출·저장)은 그대로 살아 있어야 한다.
    expect(pinterest.views).toBe(300)
    expect(pinterest.likes).toBe(20)
  })

  /** **지원 지표의 실제 0 은 관측 결과다.** null 로 바꾸면 실제 관찰을 잃는다. */
  it('지원 지표의 측정된 0은 숫자로 전달된다', async () => {
    givenPlatforms([{
      platform: 'YOUTUBE', views: 1000, likes: 0, comments: 0, shares: 0,
      unavailableMetrics: [],
    }])

    const youtube = (await analyticsApi.platformComparison('30d'))[0]

    expect(youtube.likes).toBe(0)
    expect(youtube.comments).toBe(0)
    expect(youtube.shares).toBe(0)
    expect(youtube.unavailableMetrics).toEqual([])
  })

  it('플랫폼 비교는 서버 엔드포인트에서 읽는다', async () => {
    givenPlatforms([])

    await analyticsApi.platformComparison('30d')

    expect(get).toHaveBeenCalled()
  })

  // ── 현재 소비자 회귀 ─────────────────────────────────────────────────────

  /**
   * `PlatformPieChart` 는 `views` 만 쓴다. 다른 지표가 `null` 이 돼도 깨지지 않아야 한다.
   */
  it('원형 차트는 지표가 null이어도 조회수로 정상 렌더링된다', () => {
    // jsdom 에는 matchMedia 가 없다. 테마 스토어가 setup 에서 부르므로 채워 준다 —
    // 이번 변경과 무관한 환경 공백이다.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false, media: query, onchange: null,
      addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
      addListener: () => {}, removeListener: () => {},
    }))

    const data: PlatformComparison[] = [
      { platform: 'FACEBOOK', views: 500, likes: 40, comments: 12, shares: null, unavailableMetrics: ['shares'] },
      { platform: 'YOUTUBE', views: 1000, likes: 0, comments: 0, shares: 0, unavailableMetrics: [] },
    ]

    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(PlatformPieChart, { props: { data }, global: { plugins: [pinia] } })

    expect(wrapper.html()).toBeTruthy()
    expect(wrapper.text()).not.toContain('NaN')
  })
})
