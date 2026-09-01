import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { analyticsApi } from './analytics'

/**
 * 영상 분석 매퍼가 **서버의 `null` 을 숫자로 되살리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버는 지원하는 지표라도 그 기간에 집계 행이 없으면 `dailyData.sumOf { .. }` 가 내는
 * `0` 을 그대로 보냈다. 화면은 `hasData` 로 숨길 수 있었지만 **JSON 계약 자체가
 * "0 회 측정됨"** 이라고 말했다.
 *
 * 이제 서버가 `null` 을 준다. 매퍼가 여기서 `?? 0` 을 하면 그 수정이 통째로 무의미해진다.
 *
 * ## 세 상태
 *
 * - `null` + 지표가 `unavailableMetrics` 에 **있음** → 플랫폼이 주지 않음(측정 불가)
 * - `null` + 지표가 `unavailableMetrics` 에 **없음** → 수집 대기(`hasData=false`)
 * - 숫자 → 실측. `0` 은 관측이다.
 */
describe('analyticsApi.videoAnalytics 측정 계약', () => {
  const get = vi.spyOn(apiClient, 'get')

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function respond(platforms: unknown[]) {
    get.mockResolvedValue({
      data: { success: true, data: { videoId: 1, title: '영상', platforms } },
    } as never)
  }

  /** **이 케이스가 "조회수 0회" 로 보이던 자리다.** */
  it('수집 대기(null)를 0 으로 바꾸지 않고 hasData 를 false 로 준다', async () => {
    respond([
      {
        platform: 'YOUTUBE',
        views: null,
        likes: null,
        comments: null,
        shares: null,
        unavailableMetrics: [],
        dailyData: [],
      },
    ])

    const detail = (await analyticsApi.videoAnalytics(1))[0]

    expect(detail.views).toBeNull()
    expect(detail.likes).toBeNull()
    expect(detail.comments).toBeNull()
    expect(detail.shares).toBeNull()
    expect(detail.hasData).toBe(false)
    // 플랫폼은 지표를 준다 — 미지원으로 뭉치면 안 된다.
    expect(detail.unavailableMetrics).toEqual([])
  })

  /** **측정된 0 은 관측이다.** */
  it('실측 0 은 0 으로 유지하고 hasData 를 true 로 준다', async () => {
    respond([
      {
        platform: 'YOUTUBE',
        views: 0,
        likes: 0,
        comments: 0,
        shares: 0,
        unavailableMetrics: [],
        dailyData: [{ date: '2026-08-20', views: 0, likes: 0, comments: 0, shares: 0 }],
      },
    ])

    const detail = (await analyticsApi.videoAnalytics(1))[0]

    expect(detail.views).toBe(0)
    expect(detail.likes).toBe(0)
    expect(detail.hasData).toBe(true)
    expect(detail.dailyTrend).toHaveLength(1)
  })

  /** 미지원은 `null` + 사유를 그대로 전달하고, 가짜 계열을 만들지 않는다. */
  it('미지원 지표의 null 과 사유를 그대로 전달한다', async () => {
    respond([
      {
        platform: 'TUMBLR',
        views: null,
        likes: 5,
        comments: null,
        shares: null,
        unavailableMetrics: ['VIEWS', 'COMMENTS', 'SHARES'],
        dailyData: [],
      },
    ])

    const detail = (await analyticsApi.videoAnalytics(1))[0]

    expect(detail.views).toBeNull()
    expect(detail.unavailableMetrics).toContain('VIEWS')
    expect(detail.hasData).toBe(false)
    expect(detail.dailyTrend).toEqual([])
  })

  /**
   * 같은 응답에 미지원과 수집 대기가 섞여 있어도 둘을 뭉치지 않는다.
   * 값은 둘 다 `null` 이지만 `unavailableMetrics` 가 이유를 가른다.
   */
  it('혼합 플랫폼에서 미지원과 수집 대기를 뭉치지 않는다', async () => {
    respond([
      { platform: 'YOUTUBE', views: null, likes: null, comments: null, shares: null, unavailableMetrics: [], dailyData: [] },
      { platform: 'TUMBLR', views: null, likes: 5, comments: null, shares: null, unavailableMetrics: ['VIEWS'], dailyData: [] },
      {
        platform: 'TIKTOK',
        views: 700,
        likes: 20,
        comments: 3,
        shares: 1,
        unavailableMetrics: [],
        dailyData: [{ date: '2026-08-20', views: 700, likes: 20, comments: 3, shares: 1 }],
      },
    ])

    const details = await analyticsApi.videoAnalytics(1)
    const byPlatform = Object.fromEntries(details.map((d) => [d.platform, d]))

    // 측정된 플랫폼은 값이 그대로 산다.
    expect(byPlatform.TIKTOK.views).toBe(700)
    expect(byPlatform.TIKTOK.hasData).toBe(true)
    // 수집 대기: null 이지만 사유 목록은 비어 있다.
    expect(byPlatform.YOUTUBE.views).toBeNull()
    expect(byPlatform.YOUTUBE.unavailableMetrics).toEqual([])
    // 미지원: null 이고 사유가 있다.
    expect(byPlatform.TUMBLR.views).toBeNull()
    expect(byPlatform.TUMBLR.unavailableMetrics).toContain('VIEWS')
  })

  /** 어떤 경우에도 `null` 이 `0` 으로 바뀌어 나가면 안 된다. */
  it('null 지표가 숫자 0 으로 바뀌지 않는다', async () => {
    respond([
      { platform: 'YOUTUBE', views: null, likes: null, comments: null, shares: null, unavailableMetrics: [], dailyData: [] },
    ])

    const detail = (await analyticsApi.videoAnalytics(1))[0]

    expect([detail.views, detail.likes, detail.comments, detail.shares]).not.toContain(0)
  })
})
