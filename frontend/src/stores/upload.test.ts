import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUploadStore, type UploadMetadata } from './upload'
import { videoApi } from '@/api/video'
import { usePresignedUpload } from '@/composables/usePresignedUpload'

vi.mock('@/api/video', () => ({
  videoApi: { uploadImages: vi.fn() },
}))

vi.mock('@/composables/usePresignedUpload', () => ({
  usePresignedUpload: vi.fn(),
}))

const videoFile = (name = 'video.mp4', size = 128) =>
  new File(['x'.repeat(size)], name, { type: 'video/mp4' })

const imageFile = (name = 'cover.png', size = 64) =>
  new File(['x'.repeat(size)], name, { type: 'image/png' })

const metadata: UploadMetadata = {
  title: '게시 제목',
  description: '게시 설명',
  tags: ['태그'],
  category: '10',
  visibility: 'PUBLIC',
  thumbnailUrl: '',
}

const platformConfigs = [{
  platform: 'YOUTUBE',
  title: '게시 제목',
  description: '게시 설명',
  tags: ['태그'],
  visibility: 'PUBLIC',
}] as never

describe('upload store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    const storage = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
      removeItem: (key: string) => storage.delete(key),
      clear: () => storage.clear(),
    })
  })

  it('stores video selection without pretending that selection published the video', async () => {
    const store = useUploadStore()
    await store.startUpload(videoFile())

    expect(store.file?.name).toBe('video.mp4')
    expect(store.mediaType).toBe('VIDEO')
    expect(store.progress.bytesTotal).toBe(128)
    expect(store.hasActiveSession).toBe(true)
    expect(store.isImage).toBe(false)
    expect(store.isUploading).toBe(false)
  })

  it('cancels the previous transfer before replacing a file and ignores its late result', async () => {
    let rejectFirst!: (error: Error) => void
    const firstAbort = vi.fn(() => rejectFirst(new Error('업로드가 취소되었습니다')))
    vi.mocked(usePresignedUpload).mockReturnValue({
      upload: vi.fn(() => new Promise<number>((_resolve, reject) => { rejectFirst = reject })),
      abort: firstAbort,
    } as never)
    const store = useUploadStore()
    await store.startUpload(videoFile('first.mp4'))
    const firstPublish = store.cloudPublish(videoFile('first.mp4'), metadata, platformConfigs)
    await vi.waitFor(() => expect(store.canAbort).toBe(true))

    await store.startUpload(videoFile('second.mp4'))
    await expect(firstPublish).rejects.toThrow('업로드가 취소되었습니다')

    expect(firstAbort).toHaveBeenCalledOnce()
    expect(store.file?.name).toBe('second.mp4')
    expect(store.videoId).toBeNull()
    expect(store.isUploading).toBe(false)
  })

  it('uploads images through the server and records final progress', async () => {
    const images = [{ id: 7, url: 'https://cdn.test/cover.png' }]
    vi.mocked(videoApi.uploadImages).mockResolvedValue(images as never)
    const store = useUploadStore()
    await store.startImageUpload([imageFile(), imageFile('second.png', 32)])

    await expect(store.uploadImagesToServer(41)).resolves.toEqual(images)
    expect(videoApi.uploadImages).toHaveBeenCalledWith(41, store.imageFiles)
    expect(store.contentImages).toEqual(images)
    expect(store.progress).toMatchObject({ bytesUploaded: 96, bytesTotal: 96, percentage: 100 })
    expect(store.uploading).toBe(false)
  })

  it('surfaces image upload errors and always leaves uploading false', async () => {
    vi.mocked(videoApi.uploadImages).mockRejectedValue(new Error('이미지 저장소 장애'))
    const store = useUploadStore()
    await store.startImageUpload([imageFile()])

    await expect(store.uploadImagesToServer(42)).rejects.toThrow('이미지 저장소 장애')
    expect(store.uploadError).toBe('이미지 저장소 장애')
    expect(store.uploading).toBe(false)
  })

  it('publishes through the cloud path, updates progress callbacks, and clears abort state', async () => {
    const upload = vi.fn().mockImplementation(async (item: { id: string }) => {
      const options = vi.mocked(usePresignedUpload).mock.calls[0]?.[0] as {
        onProgress?: (id: string, percentage: number) => void
        onSpeedUpdate?: (id: string, bytes: number, remaining: number) => void
      }
      options.onProgress?.(item.id, 50)
      options.onSpeedUpdate?.(item.id, 2048, 2.4)
      return 88
    })
    const abort = vi.fn()
    vi.mocked(usePresignedUpload).mockReturnValue({ upload, abort } as never)
    const store = useUploadStore()

    await expect(store.cloudPublish(videoFile(), metadata, platformConfigs)).resolves.toEqual({ videoId: 88 })
    expect(upload).toHaveBeenCalledOnce()
    expect(store.videoId).toBe(88)
    expect(store.progress).toMatchObject({ percentage: 100, bytesUploaded: 128, speed: 0, remainingSeconds: 0 })
    expect(store.canAbort).toBe(false)
    expect(store.uploading).toBe(false)
  })

  it('reports cloud cancellation/failure and keeps the user-facing error', async () => {
    vi.mocked(usePresignedUpload).mockReturnValue({
      upload: vi.fn().mockResolvedValue(null),
      abort: vi.fn(),
    } as never)
    const store = useUploadStore()
    await expect(store.cloudPublish(videoFile(), metadata, platformConfigs)).rejects.toThrow('업로드가 취소되었습니다.')
    expect(store.uploadError).toBe('업로드가 취소되었습니다.')
    expect(store.uploading).toBe(false)

    vi.mocked(usePresignedUpload).mockReturnValue({
      upload: vi.fn().mockRejectedValue(new Error('presigned 장애')),
      abort: vi.fn(),
    } as never)
    await expect(store.cloudPublish(videoFile(), metadata, platformConfigs)).rejects.toThrow('presigned 장애')
    expect(store.uploadError).toBe('presigned 장애')
  })

  it('handles stream publish success and parses the server video id', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeXHR)
    const store = useUploadStore()
    const promise = store.streamPublish(videoFile(), metadata, platformConfigs)
    const xhr = FakeXHR.lastInstance!
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 64, total: 128 } as ProgressEvent)
    await vi.waitFor(() => expect(xhr.send).toHaveBeenCalledOnce())
    xhr.status = 202
    xhr.responseText = JSON.stringify({ data: { videoId: 99 } })
    xhr.onload?.(new Event('load'))

    await expect(promise).resolves.toEqual({ videoId: 99 })
    expect(xhr.requestHeaders.Authorization).toBe('Bearer null')
    expect(store.videoId).toBe(99)
    expect(store.progress.percentage).toBe(100)
    expect(store.canAbort).toBe(false)
  })

  it('maps stream HTTP, network, and abort failures to actionable errors', async () => {
    const cases: Array<{ status: number; body: string; trigger: 'load' | 'error' | 'abort'; message: string }> = [
      { status: 400, body: JSON.stringify({ message: '플랫폼 설정 오류' }), trigger: 'load', message: '플랫폼 설정 오류' },
      { status: 0, body: '', trigger: 'error', message: '네트워크 오류' },
      { status: 0, body: '', trigger: 'abort', message: '업로드가 취소되었습니다' },
    ]

    for (const current of cases) {
      vi.stubGlobal('XMLHttpRequest', FakeXHR)
      const store = useUploadStore()
      const promise = store.streamPublish(videoFile(), metadata, platformConfigs)
      const xhr = FakeXHR.lastInstance!
      await vi.waitFor(() => expect(xhr.send).toHaveBeenCalledOnce())
      xhr.status = current.status
      xhr.responseText = current.body
      if (current.trigger === 'load') xhr.onload?.(new Event('load'))
      if (current.trigger === 'error') xhr.onerror?.(new Event('error'))
      if (current.trigger === 'abort') xhr.onabort?.(new Event('abort'))

      await expect(promise).rejects.toThrow(current.message)
      expect(store.uploading).toBe(false)
      expect(store.canAbort).toBe(false)
    }
  })

  it('resets the whole session and invokes the registered abort handler', async () => {
    const abort = vi.fn()
    vi.mocked(usePresignedUpload).mockReturnValue({ upload: vi.fn(() => new Promise(() => undefined)), abort } as never)
    const store = useUploadStore()
    await store.startUpload(videoFile())
    const publishPromise = store.cloudPublish(videoFile(), metadata, platformConfigs)
    await vi.waitFor(() => expect(store.canAbort).toBe(true))
    store.abortPublish()
    expect(abort).toHaveBeenCalledOnce()
    store.resetUpload()
    expect(store.file).toBeNull()
    expect(store.videoId).toBeNull()
    expect(store.progress.bytesTotal).toBe(0)
    expect(store.hasActiveSession).toBe(false)
    await Promise.race([publishPromise, Promise.resolve()])
  })
})

class FakeXHR {
  static lastInstance: FakeXHR | null = null
  upload: { onprogress?: (event: ProgressEvent) => void } = {}
  status = 0
  responseText = ''
  requestHeaders: Record<string, string> = {}
  open = vi.fn()
  send = vi.fn()
  setRequestHeader = vi.fn((name: string, value: string) => {
    this.requestHeaders[name] = value
  })
  abort = vi.fn(() => this.onabort?.(new Event('abort')))
  onload?: (event: Event) => void
  onerror?: (event: Event) => void
  onabort?: (event: Event) => void

  constructor() {
    FakeXHR.lastInstance = this
  }
}
