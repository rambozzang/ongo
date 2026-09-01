import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { analyticsApi } from './analytics'
import { calculateVideoScore } from '@/utils/scoreCalculator'
import type { VideoAnalytics } from '@/types/analytics'
import type { Video } from '@/types/video'

/**
 * 분석 응답의 **미수집·이름 불일치 지표가 프론트에서 0 으로 되살아나지 않는지** 고정한다.
 *
 * | 어댑터 | 필드 | 실제로 들어 있던 것 |
 * |---|---|---|
 * | `PinterestClient.kt:160` | `shares` | `PIN_CLICK` — 핀 **클릭 수** |
 * | `DailymotionClient.kt:121` | `shares` | `bookmarks_total` — **북마크 수** |
 * | `TumblrClient.kt:141` | `views` | `total_notes` — **노트 총합** |
 *
 * 서버가 이제 `null` 을 주지만, 매퍼가 `?? 0` 한 줄만 넣어도 서버 수정이 통째로
 * 무의미해진다. 게다가 이쪽은 하드코딩 0 과 달리 **원래 큰 숫자**였다.
 */
describe('분석 응답 미수집 지표 전달 계약', () => {
  const get = vi.spyOn(apiClient, 'get')

  beforeEach(() => vi.clearAllMocks())

  function givenVideoAnalytics(platforms: unknown[]) {
    get.mockResolvedValue({ data: { success: true, data: { videoId: 11, title: '영상', platforms } } } as never)
  }

  // ── videoAnalytics ──────────────────────────────────────────────────────

  it('Pinterest 공유·댓글 null을 0으로 만들지 않는다', async () => {
    givenVideoAnalytics([{
      platform: 'PINTEREST', views: 500, likes: 30, comments: null, shares: null,
      unavailableMetrics: ['comments', 'shares'], dailyData: [],
    }])

    const detail = (await analyticsApi.videoAnalytics(11))[0]

    expect(detail.shares).toBeNull()
    expect(detail.comments).toBeNull()
    expect(detail.views).toBe(500)
    expect(detail.likes).toBe(30)
  })

  it('Tumblr 조회수 null을 0으로 만들지 않는다', async () => {
    givenVideoAnalytics([{
      platform: 'TUMBLR', views: null, likes: 60, comments: 20, shares: 20,
      unavailableMetrics: ['views'], dailyData: [],
    }])

    const detail = (await analyticsApi.videoAnalytics(11))[0]

    expect(detail.views).toBeNull()
    // 조회수가 없으면 그릴 추이도 없다.
    expect(detail.dailyTrend).toEqual([])
    expect(detail.hasData).toBe(false)
  })

  /** **지원 플랫폼의 실제 0 은 관측이다.** null 로 바꾸면 실제 관찰을 잃는다. */
  it('YouTube 의 측정된 0은 숫자로 전달한다', async () => {
    givenVideoAnalytics([{
      platform: 'YOUTUBE', views: 500, likes: 0, comments: 0, shares: 0,
      unavailableMetrics: [], dailyData: [{ date: '2026-08-20', views: 500, likes: 0, comments: 0 }],
    }])

    const detail = (await analyticsApi.videoAnalytics(11))[0]

    expect(detail.shares).toBe(0)
    expect(detail.likes).toBe(0)
    expect(detail.hasData).toBe(true)
  })

  // ── scoreCalculator ─────────────────────────────────────────────────────

  const video = { id: 11, title: '영상', createdAt: '2026-08-20T00:00:00', uploads: [], tags: [] } as unknown as Video

  function analytics(rows: Partial<VideoAnalytics>[]): VideoAnalytics[] {
    return rows.map((r) => ({
      platform: 'YOUTUBE', views: 0, likes: 0, comments: 0, shares: 0, dailyTrend: [], ...r,
    })) as VideoAnalytics[]
  }

  /**
   * 점수 계산의 합계는 **측정된 값만** 더해야 한다. `?? 0` 을 하면 그 플랫폼이
   * "0 을 기록했다" 는 관측이 되어 점수가 실제보다 낮아진다.
   */
  it('점수 계산이 미수집 지표를 0으로 더하지 않는다', () => {
    // Tumblr 조회수 null + YouTube 조회수 1,000 → 분모는 1,000 이어야 한다.
    const measuredOnly = calculateVideoScore(video, analytics([
      { platform: 'YOUTUBE', views: 1_000, likes: 100, comments: 20, shares: 10 },
    ]))
    const withUnmeasured = calculateVideoScore(video, analytics([
      { platform: 'YOUTUBE', views: 1_000, likes: 100, comments: 20, shares: 10 },
      { platform: 'TUMBLR', views: null, likes: null, comments: null, shares: null },
    ]))

    expect(withUnmeasured.overall).toBe(measuredOnly.overall)
  })

  /** 측정된 0 은 그대로 분자에 들어간다 — 관측이기 때문이다. 미수집은 축에서 뺀다. */
  it('측정된 0은 점수 계산에 반영하고 미수집 축은 0점으로 만들지 않는다', () => {
    const withZero = calculateVideoScore(video, analytics([
      { platform: 'YOUTUBE', views: 1_000, likes: 0, comments: 0, shares: 0 },
    ]))
    const withNull = calculateVideoScore(video, analytics([
      { platform: 'YOUTUBE', views: 1_000, likes: null, comments: null, shares: null },
    ]))

    expect(withZero.engagement).toBe(0)
    expect(withNull.engagement).toBeNull()
    expect(withZero.overall).not.toBe(withNull.overall)
    expect(withZero.overall).toBeGreaterThan(0)
  })

  // ── topVideos 매퍼 ──────────────────────────────────────────────────────

  function givenTopVideos(videos: unknown[]) {
    get.mockResolvedValue({ data: { success: true, data: { videos } } } as never)
  }

  /**
   * **매퍼의 `?? 0` 이 서버 수정을 통째로 무효화하던 자리다.**
   *
   * `totalLikes: v.totalLikes ?? 0` 은 "좋아요를 수집하는 업로드가 없다" 를
   * "좋아요 0개" 로 바꿔 놓는다.
   */
  it('인기 영상의 좋아요 null을 0으로 만들지 않는다', async () => {
    givenTopVideos([{
      id: 11, title: '영상', thumbnailUrl: null,
      totalViews: 500, totalLikes: null, unavailableMetrics: ['likes'],
      publishedAt: null, platforms: ['NAVER_CLIP'],
    }])

    const item = (await analyticsApi.topVideos('30d'))[0]

    expect(item.totalLikes).toBeNull()
    expect(item.unavailableMetrics).toContain('likes')
  })

  it('인기 영상의 조회수 null도 0으로 만들지 않는다', async () => {
    givenTopVideos([{
      id: 11, title: '영상', thumbnailUrl: null,
      totalViews: null, totalLikes: 60, unavailableMetrics: ['views'],
      publishedAt: null, platforms: ['TUMBLR'],
    }])

    const item = (await analyticsApi.topVideos('30d'))[0]

    expect(item.totalViews).toBeNull()
    expect(item.totalLikes).toBe(60)
  })

  /** 필드가 없는 옛 응답도 판단 불가라 `null` 이다 — 0 이 아니다. */
  it('좋아요 필드가 없는 옛 응답은 null로 둔다', async () => {
    givenTopVideos([{
      id: 11, title: '영상', thumbnailUrl: null, totalViews: 500,
      publishedAt: null, platforms: ['YOUTUBE'],
    }])

    expect((await analyticsApi.topVideos('30d'))[0].totalLikes).toBeNull()
  })

  /** **측정된 0 은 관측이다.** null 로 바꾸면 실제 관찰을 잃는다. */
  it('인기 영상의 측정된 0은 숫자로 전달한다', async () => {
    givenTopVideos([{
      id: 11, title: '영상', thumbnailUrl: null,
      totalViews: 500, totalLikes: 0, unavailableMetrics: [],
      publishedAt: null, platforms: ['YOUTUBE'],
    }])

    expect((await analyticsApi.topVideos('30d'))[0].totalLikes).toBe(0)
  })
})
