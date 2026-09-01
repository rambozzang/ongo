import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { analyticsApi } from './analytics'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

/**
 * 평균 시청 시간 계약이 **미측정을 0초로 되돌리지 않는지** 고정한다.
 *
 * 시청 시간을 조회하는 어댑터는 YouTube 하나뿐이라, 다른 플랫폼만 쓰는 크리에이터에게는
 * 분자가 없다. 예전에는 서버가 `avgDurationSeconds = 0` 을 내려줬고 `PerformanceView` 는
 * 응답 객체가 항상 오므로 `—` 대신 **"0초"** 를 그렸다 — 재지 않았을 뿐인데 시청이
 * 없었다는 관측이 된다.
 *
 * 서버는 이제 유효한 행이 없으면 `null` 과 빈 `data` 를 준다. 매퍼가 `?? 0` 한 줄만
 * 넣어도 서버 수정이 통째로 무의미해진다.
 */
describe('평균 시청 시간 측정 불가 계약', () => {
  const get = vi.spyOn(apiClient, 'get')

  function givenResponse(body: Record<string, unknown>) {
    get.mockResolvedValue({ data: { success: true, data: body } } as never)
  }

  beforeEach(() => vi.clearAllMocks())

  it('서버가 null을 주면 0초로 만들지 않는다', async () => {
    givenResponse({
      period: '30d',
      avgDurationSeconds: null,
      data: [],
      measuredPlatforms: [],
      unavailableReason: '시청 시간이 수집되지 않아 평균 시청 시간을 계산할 수 없습니다',
    })

    const response = await analyticsApi.avgViewDuration(30)

    expect(response.avgDurationSeconds).toBeNull()
    expect(response.data).toEqual([])
    expect(response.unavailableReason).toContain('시청 시간이 수집되지 않아')
  })

  it('측정된 값은 그대로 전달한다', async () => {
    givenResponse({
      period: '30d',
      avgDurationSeconds: 120,
      data: [{ date: '2026-08-10', avgDurationSeconds: 120, totalWatchTimeSeconds: 12000, totalViews: 100 }],
      measuredPlatforms: ['YOUTUBE'],
      unavailableReason: null,
    })

    const response = await analyticsApi.avgViewDuration(30)

    expect(response.avgDurationSeconds).toBe(120)
    expect(response.measuredPlatforms).toEqual(['YOUTUBE'])
  })

  /** **측정된 0초는 관측 결과다.** null 로 바꾸면 실제 관찰을 잃는다. */
  it('측정된 0초는 숫자로 전달한다', async () => {
    givenResponse({
      period: '30d',
      avgDurationSeconds: 0,
      data: [{ date: '2026-08-10', avgDurationSeconds: 0, totalWatchTimeSeconds: 0, totalViews: 5000 }],
      measuredPlatforms: ['YOUTUBE'],
      unavailableReason: null,
    })

    const response = await analyticsApi.avgViewDuration(30)

    expect(response.avgDurationSeconds).toBe(0)
    expect(response.unavailableReason).toBeNull()
  })

  it('평균 시청 시간은 서버 엔드포인트에서 읽는다', async () => {
    givenResponse({ period: '30d', avgDurationSeconds: null, data: [] })

    await analyticsApi.avgViewDuration(30)

    expect(get).toHaveBeenCalledWith('/analytics/avg-view-duration', { params: { days: 30 } })
  })

  /**
   * 화면은 `avgDurationSeconds != null` 로 갈라 "측정 불가" 문구를 쓴다.
   * 그 문구가 두 로케일에 없으면 키 경로가 그대로 노출된다.
   */
  it('ko/en 두 로케일에 측정 불가 문구가 있다', () => {
    expect(koMessages.analyticsView.notMeasured).toBeTruthy()
    expect((enMessages as { analyticsView: { notMeasured?: string } }).analyticsView.notMeasured).toBeTruthy()
  })
})
