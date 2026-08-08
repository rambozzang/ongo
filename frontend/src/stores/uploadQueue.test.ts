import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUploadQueueStore } from './uploadQueue'
import { usePresignedUpload } from '@/composables/usePresignedUpload'
import type { Platform } from '@/types/channel'

vi.mock('@/composables/usePresignedUpload', () => ({ usePresignedUpload: vi.fn() }))

function fakeFile(name = 'video.mp4', size = 100) {
  return { name, type: 'video/mp4', size } as File
}

function queueItem(overrides: Record<string, unknown> = {}) {
  return {
    file: fakeFile(),
    fileName: 'video.mp4',
    fileSize: 100,
    title: '영상',
    platforms: ['YOUTUBE'] as Platform[],
    ...overrides,
  }
}

describe('upload queue store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('marks a completed file transfer as needs-config until a publish config exists', async () => {
    const upload = vi.fn().mockResolvedValue(101)
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort: vi.fn() } as never)
    const store = useUploadQueueStore()
    store.addToQueue(queueItem())

    store.startProcessing()
    await vi.waitFor(() => expect(upload).toHaveBeenCalledTimes(1))
    await vi.waitFor(() => expect(store.queue[0].status).toBe('needs-config'))

    expect(store.queue[0].videoId).toBe(101)
    expect(store.queue[0].platformProgress.YOUTUBE.status).toBe('pending')
    expect(store.completedCount).toBe(0)
    expect(store.uploadingCount).toBe(0)
    expect(store.isProcessing).toBe(false)
  })

  it('marks a configured upload completed and updates aggregate progress', async () => {
    const upload = vi.fn().mockResolvedValue(102)
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort: vi.fn() } as never)
    const store = useUploadQueueStore()
    store.addToQueue(queueItem({ platformConfigs: [{ platform: 'YOUTUBE', title: '게시' }] }))

    store.startProcessing()
    await vi.waitFor(() => expect(store.queue[0].status).toBe('completed'))

    expect(store.queue[0].progress).toBe(100)
    expect(store.queue[0].platformProgress.YOUTUBE).toEqual({ status: 'completed', progress: 100 })
    expect(store.completedCount).toBe(1)
    expect(store.totalProgress).toBe(100)
    expect(store.isProcessing).toBe(false)
  })

  it('surfaces upload failures and allows a failed item to be retried', async () => {
    const upload = vi.fn().mockRejectedValueOnce(new Error('storage unavailable')).mockResolvedValueOnce(103)
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort: vi.fn() } as never)
    const store = useUploadQueueStore()
    store.addToQueue(queueItem({ platformConfigs: [{ platform: 'YOUTUBE', title: '게시' }] }))

    store.startProcessing()
    await vi.waitFor(() => expect(store.queue[0].status).toBe('failed'))
    expect(store.queue[0].error).toBe('storage unavailable')
    expect(store.failedCount).toBe(1)

    store.retryItem(store.queue[0].id)
    expect(store.queue[0].status).toBe('queued')
    store.startProcessing()
    await vi.waitFor(() => expect(store.queue[0].status).toBe('completed'))
    expect(upload).toHaveBeenCalledTimes(2)
  })

  it('does not leave a cancelled upload stuck in uploading state', async () => {
    const upload = vi.fn().mockResolvedValue(null)
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort: vi.fn() } as never)
    const store = useUploadQueueStore()
    store.addToQueue(queueItem())

    store.startProcessing()
    await vi.waitFor(() => expect(store.queue[0].status).toBe('paused'))

    expect(store.uploadingCount).toBe(0)
    expect(store.isProcessing).toBe(false)
  })

  it('aborts active transfers when an item is removed or the queue is cleared', async () => {
    let resolveUpload!: (videoId: number) => void
    const upload = vi.fn().mockImplementation(() => new Promise<number>((resolve) => { resolveUpload = resolve }))
    const abort = vi.fn()
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort } as never)
    const store = useUploadQueueStore()
    store.addToQueue(queueItem())
    store.addToQueue(queueItem({ fileName: 'second.mp4', file: fakeFile('second.mp4') }))

    store.startProcessing()
    await vi.waitFor(() => expect(upload).toHaveBeenCalledTimes(2))
    const firstId = store.queue[0].id
    store.removeFromQueue(firstId)
    expect(abort).toHaveBeenCalledTimes(1)
    expect(store.queue).toHaveLength(1)

    store.clearAll()
    expect(abort).toHaveBeenCalledTimes(2)
    expect(store.queue).toEqual([])
    expect(store.isProcessing).toBe(false)
    resolveUpload(104)
  })

  it('supports pause, resume, reorder, and clearing only terminal completed items', () => {
    const store = useUploadQueueStore()
    store.addToQueue(queueItem({ fileName: 'first.mp4', file: fakeFile('first.mp4') }))
    store.addToQueue(queueItem({ fileName: 'second.mp4', file: fakeFile('second.mp4') }))
    const [first, second] = store.queue
    first.status = 'completed'
    first.progress = 100
    second.status = 'uploading'
    store.pauseItem(second.id)
    expect(second.status).toBe('paused')
    store.resumeItem(second.id)
    expect(second.status).toBe('queued')
    store.reorderQueue(0, 1)
    expect(store.queue[0].id).toBe(second.id)
    store.clearCompleted()
    expect(store.queue.map((item) => item.id)).toEqual([second.id])
  })
})
