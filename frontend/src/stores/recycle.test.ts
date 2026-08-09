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
    vi.mocked(videoApi.recycle).mockResolvedValue(undefined as never)
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
