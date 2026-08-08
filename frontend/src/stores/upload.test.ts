import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { videoApi } from '@/api/video'
import { useUploadStore } from './upload'

vi.mock('@/api/video', () => ({
  videoApi: {
    uploadImages: vi.fn(),
  },
}))

vi.mock('@/composables/usePresignedUpload', () => ({
  usePresignedUpload: vi.fn(),
}))

function fakeFile(name: string, type: string, size: number) {
  return { name, type, size } as File
}

describe('upload session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('keeps video selection separate from the actual publish request', async () => {
    const store = useUploadStore()
    const file = fakeFile('source.mp4', 'video/mp4', 1200)

    await store.startUpload(file)

    expect(store.file).toMatchObject(file)
    expect(store.mediaType).toBe('VIDEO')
    expect(store.hasActiveSession).toBe(true)
    expect(store.progress.bytesTotal).toBe(1200)
    expect(store.isUploading).toBe(false)
  })

  it('routes image selections to the image session and uploads them through the API', async () => {
    const store = useUploadStore()
    const images = [fakeFile('cover.png', 'image/png', 80), fakeFile('frame.jpg', 'image/jpeg', 120)]
    const result = [{ id: 1, videoId: 55, url: 'https://cdn.test/1.png', sortOrder: 0 }] as never
    vi.mocked(videoApi.uploadImages).mockResolvedValue(result)

    await store.startUpload(images[0])
    await store.startImageUpload(images)
    const uploaded = await store.uploadImagesToServer(55)

    expect(store.isImage).toBe(true)
    expect(store.imageFiles).toEqual(images)
    expect(videoApi.uploadImages).toHaveBeenCalledWith(55, images)
    expect(uploaded).toBe(result)
    expect(store.progress.percentage).toBe(100)
    expect(store.uploadError).toBeNull()
  })

  it('preserves a visible upload error and resets the session explicitly', async () => {
    const store = useUploadStore()
    vi.mocked(videoApi.uploadImages).mockRejectedValue(new Error('storage unavailable'))
    await store.startImageUpload([fakeFile('cover.png', 'image/png', 80)])

    await expect(store.uploadImagesToServer(55)).rejects.toThrow('storage unavailable')
    expect(store.uploadError).toBe('storage unavailable')

    store.resetUpload()
    expect(store.file).toBeNull()
    expect(store.videoId).toBeNull()
    expect(store.hasActiveSession).toBe(false)
    expect(store.uploadError).toBeNull()
  })
})
