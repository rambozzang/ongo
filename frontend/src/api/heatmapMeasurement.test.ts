import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { analyticsApi } from './analytics'

/**
 * 게시 시간 히트맵 **매퍼 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버는 요일을 이름으로 준다 — `{"WED": {"14": 1000}}`. 매퍼는 그것을
 * `parseInt('WED', 10)` 했고 결과는 `NaN` 이었다. 히트맵 컴포넌트의 조회 키는
 * `` `${dayOfWeek}-${hour}` `` 라 `"NaN-14"` 가 되어 **어떤 칸과도 맞지 않았다.**
 * 화면 전체가 빈 칸으로 그려졌고, 그것이 "데이터 없음" 과 구분되지 않았다.
 */
describe('analyticsApi.heatmap', () => {
  const get = vi.spyOn(apiClient, 'get')

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function respond(data: Record<string, Record<string, number>>) {
    get.mockResolvedValue({ data: { success: true, data: { data } } } as never)
  }

  /** **이 케이스가 히트맵을 통째로 비우던 자리다.** */
  it('요일 이름을 인덱스로 옮긴다', async () => {
    respond({ WED: { '14': 1000 } })

    const result = await analyticsApi.heatmap()

    expect(result).toEqual([{ dayOfWeek: 3, hour: 14, value: 1000 }])
    expect(result.every((d) => Number.isFinite(d.dayOfWeek))).toBe(true)
  })

  it('일곱 요일을 모두 옮긴다', async () => {
    respond({
      SUN: { '0': 1 },
      MON: { '1': 2 },
      TUE: { '2': 3 },
      WED: { '3': 4 },
      THU: { '4': 5 },
      FRI: { '5': 6 },
      SAT: { '6': 7 },
    })

    const result = await analyticsApi.heatmap()

    expect(result.map((d) => d.dayOfWeek).sort()).toEqual([0, 1, 2, 3, 4, 5, 6])
  })

  /** **측정된 0 은 관측이다.** 그 시간에 올렸고 조회가 없었다는 뜻이라 칸을 남긴다. */
  it('측정된 0 조회수 칸을 버리지 않는다', async () => {
    respond({ WED: { '14': 0 } })

    expect(await analyticsApi.heatmap()).toEqual([{ dayOfWeek: 3, hour: 14, value: 0 }])
  })

  /** 서버가 안 준 칸을 0 으로 지어내지 않는다 — 재지 않은 것과 0 회는 다르다. */
  it('데이터 없는 칸을 만들어내지 않는다', async () => {
    respond({})

    expect(await analyticsApi.heatmap()).toEqual([])
  })

  it('빈 요일 묶음은 칸을 만들지 않는다', async () => {
    respond({ WED: {} })

    expect(await analyticsApi.heatmap()).toEqual([])
  })

  /** 알 수 없는 요일 키를 `NaN` 칸으로 흘려보내지 않는다. */
  it('알 수 없는 요일 키는 버린다', async () => {
    respond({ FUNKYDAY: { '14': 999 }, WED: { '14': 10 } })

    expect(await analyticsApi.heatmap()).toEqual([{ dayOfWeek: 3, hour: 14, value: 10 }])
  })
})
