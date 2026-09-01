import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'

vi.mock('@/api/client', () => ({
  __esModule: true,
  default: { post: vi.fn() },
  unwrapResponse: (r: any) => r.data.data,
}))

import { competitorApi } from '@/api/competitor'

describe('competitorApi.sync', () => {
  beforeEach(() => vi.clearAllMocks())

  it('POST /competitors/sync 를 호출하고 CompetitorSyncResponse 를 그대로 보존한다', async () => {
    const resData = {
      requested: 2,
      synced: 2,
      unsupported: 1,
      failed: 0,
      results: [],
      competitors: [],
      totalCount: 2,
    }
    // backend ResData: { success, message, data, error } — unwrapResponse 는 data 를 돌려준다.
    ;(apiClient.post as any).mockResolvedValue({ data: { success: true, message: 'msg', data: resData, error: null } })

    const result = await competitorApi.sync()

    expect(apiClient.post).toHaveBeenCalledWith('/competitors/sync')
    // 실제 동기화 수치(CompetitorSyncResponse)가 버려지지 않는다.
    expect(result.synced).toBe(2)
    expect(result.unsupported).toBe(1)
    expect(result.requested).toBe(2)
  })
})
