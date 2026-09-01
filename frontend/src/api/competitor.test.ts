import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { competitorApi } from './competitor'

describe('competitor API contracts', () => {
  const post = vi.spyOn(apiClient, 'post')

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the server sync summary without replacing it with a local success message', async () => {
    const syncResult = {
      requested: 2,
      synced: 1,
      unsupported: 1,
      failed: 0,
      results: [
        { competitorId: 7, channelName: 'YouTube 경쟁 채널', platform: 'YOUTUBE', status: 'SYNCED', message: null },
        { competitorId: 8, channelName: 'Instagram 경쟁 채널', platform: 'INSTAGRAM', status: 'UNSUPPORTED', message: '자동 동기화 미지원' },
      ],
      competitors: [],
      totalCount: 2,
    }
    post.mockResolvedValue({
      data: { success: true, message: '1건을 동기화했습니다 (미지원 1건)', data: syncResult },
    } as never)

    const result = await competitorApi.sync()

    expect(post).toHaveBeenCalledWith('/competitors/sync')
    expect(result).toEqual(syncResult)
  })

  it('uses the real AI insight endpoint', async () => {
    post.mockResolvedValue({
      data: { success: true, message: null, data: { summary: '요약', strengths: [], weaknesses: [], opportunities: [], recommendations: [] } },
    } as never)

    await competitorApi.insight()

    expect(post).toHaveBeenCalledWith('/ai/competitor-insight')
  })
})
