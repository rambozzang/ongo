import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRecycleStore } from './recycle'
import { videoApi } from '@/api/video'

vi.mock('@/api/video', () => ({
  videoApi: { recycle: vi.fn() },
}))

vi.mock('@/stores/notification', () => ({
  useNotificationStore: () => ({ error: vi.fn() }),
}))

describe('recycle store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(videoApi.recycle).mockResolvedValue({ videoId: 11, uploads: [] } as never)
  })

  /**
   * **서버 응답을 버리지 않는다.**
   *
   * 예전에는 `Promise<void>` 였다. 서버는 플랫폼별 `status` 와 `errorMessage` 를 주는데
   * 여기서 사라졌고, 화면은 판단 근거 없이 "재게시되었습니다" 를 단언했다. 어떤 플랫폼이
   * 거절됐는지는 어디에도 남지 않았다.
   */
  it('서버가 준 플랫폼별 접수 결과를 그대로 돌려준다', async () => {
    vi.mocked(videoApi.recycle).mockResolvedValue({
      videoId: 11,
      uploads: [
        { platform: 'YOUTUBE', status: 'UPLOADING' },
        { platform: 'TIKTOK', status: 'FAILED', errorMessage: '채널 연결이 만료되었습니다' },
      ],
    } as never)

    const result = await useRecycleStore().recycleVideo(10, {
      title: '재게시', description: '', tags: [], category: '', platforms: ['YOUTUBE#101'],
    })

    expect(result.videoId).toBe(11)
    expect(result.uploads).toEqual([
      { platform: 'YOUTUBE', status: 'UPLOADING' },
      { platform: 'TIKTOK', status: 'FAILED', errorMessage: '채널 연결이 만료되었습니다' },
    ])
  })

  /** 실패는 호출자에게 전파돼야 한다 — 삼키면 화면이 성공으로 진행한다. */
  it('요청이 실패하면 예외를 그대로 올린다', async () => {
    vi.mocked(videoApi.recycle).mockRejectedValue(new Error('서버가 응답하지 않습니다'))

    await expect(
      useRecycleStore().recycleVideo(10, {
        title: '재게시', description: '', tags: [], category: '', platforms: ['YOUTUBE#101'],
      }),
    ).rejects.toThrow('서버가 응답하지 않습니다')
  })

  it('sends the selected account ids when recycling to multiple accounts', async () => {
    await useRecycleStore().recycleVideo(10, {
      title: '재게시',
      description: '',
      tags: [],
      category: '',
      platforms: ['YOUTUBE#101', 'YOUTUBE#102'],
    })

    expect(videoApi.recycle).toHaveBeenCalledWith(10, expect.objectContaining({
      platforms: [
        expect.objectContaining({ platform: 'YOUTUBE', channelId: 101 }),
        expect.objectContaining({ platform: 'YOUTUBE', channelId: 102 }),
      ],
    }))
  })
})
