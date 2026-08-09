import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { usePresignedUpload } from './usePresignedUpload'

class FakeXMLHttpRequest {
  static instances: FakeXMLHttpRequest[] = []
  readonly upload: { onprogress: ((event: ProgressEvent) => void) | null } = { onprogress: null }
  status = 0
  method = ''
  url = ''
  body: unknown = null
  headers: Record<string, string> = {}
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null

  constructor() {
    FakeXMLHttpRequest.instances.push(this)
  }

  open(method: string, url: string) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value
  }

  send(body: unknown) {
    this.body = body
  }

  complete(status = 200) {
    this.status = status
    this.onload?.()
  }

  fail() {
    this.onerror?.()
  }

  abort() {
    this.onabort?.()
  }
}

function file() {
  return new File(['video'], 'source.mp4', { type: 'video/mp4' })
}

function item() {
  return {
    id: 'upload-1',
    file: file(),
    fileName: 'source.mp4',
    fileSize: 5,
    status: 'uploading',
    progress: 0,
    metadata: { title: '제목', description: '설명', tags: ['#ongo'], category: '교육' },
    platformConfigs: [
      {
        platform: 'YOUTUBE',
        title: '제목',
        description: '설명',
        tags: ['#ongo'],
        visibility: 'PUBLIC',
      },
    ],
  }
}

const options = {
  getBaseUrl: () => '/api/v1',
  getToken: () => 'access-token',
}

const flushAsync = () => new Promise<void>((resolve) => setTimeout(resolve, 0))

describe('usePresignedUpload', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    FakeXMLHttpRequest.instances = []
    vi.stubGlobal('XMLHttpRequest', FakeXMLHttpRequest)
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('runs init, direct PUT, completion, metadata, and publish in order', async () => {
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ data: { id: 55, uploadUrl: 'https://storage.test/put' } }),
      })
      .mockResolvedValueOnce({ ok: true, json: async () => ({}) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({}) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({}) })

    const onProgress = vi.fn()
    const onComplete = vi.fn()
    const onSpeedUpdate = vi.fn()
    const pending = usePresignedUpload({
      ...options,
      onProgress,
      onComplete,
      onSpeedUpdate,
    }).upload(item())

    await flushAsync()
    const xhr = FakeXMLHttpRequest.instances[0]
    expect(xhr).toBeDefined()
    expect(xhr.method).toBe('PUT')
    expect(xhr.url).toBe('https://storage.test/put')
    expect(xhr.headers['Content-Type']).toBe('video/mp4')
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 5, total: 5 } as ProgressEvent)
    xhr.complete()

    await expect(pending).resolves.toBe(55)
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/v1/videos/upload/init',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ Authorization: 'Bearer access-token' }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/videos/55/upload/complete',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/v1/videos/55',
      expect.objectContaining({ method: 'PUT' }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      '/api/v1/videos/55/publish',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(onProgress).toHaveBeenLastCalledWith('upload-1', 100)
    expect(onComplete).toHaveBeenCalledWith('upload-1')
    expect(onSpeedUpdate).not.toHaveBeenCalled()
  })

  it('returns null for a paused upload and does not acknowledge or publish it', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: { id: 55, uploadUrl: 'https://storage.test/put' } }),
    })
    const shouldContinue = vi.fn().mockReturnValue(false)
    const pending = usePresignedUpload({ ...options, shouldContinue }).upload(item())
    await flushAsync()
    const xhr = FakeXMLHttpRequest.instances[0]

    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: 5 } as ProgressEvent)
    expect(xhr.status).toBe(0)
    xhr.abort()

    await expect(pending).resolves.toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(shouldContinue).toHaveBeenCalledWith('upload-1')
  })

  it('surfaces storage and backend failures with actionable messages', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: false,
      status: 413,
      json: async () => ({ error: '파일이 너무 큽니다' }),
    })
    await expect(usePresignedUpload(options).upload(item())).rejects.toThrow('파일이 너무 큽니다')

    fetchMock.mockReset()
    fetchMock
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ data: { id: 55, uploadUrl: 'https://storage.test/put' } }),
      })
      .mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => ({ message: '완료 확인 실패' }),
      })
    const pending = usePresignedUpload(options).upload(item())
    await flushAsync()
    FakeXMLHttpRequest.instances[FakeXMLHttpRequest.instances.length - 1]?.complete()
    await expect(pending).rejects.toThrow('완료 확인 실패')

    fetchMock.mockReset()
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: { id: 55, uploadUrl: 'https://storage.test/put' } }),
    })
    const storageFailure = usePresignedUpload(options).upload(item())
    await flushAsync()
    FakeXMLHttpRequest.instances[FakeXMLHttpRequest.instances.length - 1]?.fail()
    await expect(storageFailure).rejects.toThrow('네트워크 오류로 업로드 실패')
  })

  it('aborts backend confirmation and never reaches metadata or publish after storage PUT', async () => {
    fetchMock.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: { id: 55, uploadUrl: 'https://storage.test/put' } }),
    })

    let rejectConfirmation: ((error: Error) => void) | undefined
    fetchMock.mockImplementationOnce(
      (_url: string, request: RequestInit) =>
        new Promise((_resolve, reject) => {
          rejectConfirmation = reject
          request.signal?.addEventListener('abort', () => reject(new Error('aborted by caller')), {
            once: true,
          })
        }),
    )

    const uploader = usePresignedUpload(options)
    const pending = uploader.upload(item())
    await flushAsync()
    FakeXMLHttpRequest.instances[FakeXMLHttpRequest.instances.length - 1]?.complete()
    await flushAsync()

    uploader.abort()
    rejectConfirmation?.(new Error('confirmation cancelled'))

    await expect(pending).rejects.toThrow('업로드가 취소되었습니다')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock).not.toHaveBeenCalledWith('/api/v1/videos/55', expect.anything())
    expect(fetchMock).not.toHaveBeenCalledWith('/api/v1/videos/55/publish', expect.anything())
  })

  it('turns an aborted initialization request into a cancellation error', async () => {
    let rejectInit: ((error: Error) => void) | undefined
    fetchMock.mockImplementationOnce(
      (_url: string, request: RequestInit) =>
        new Promise((_resolve, reject) => {
          rejectInit = reject
          request.signal?.addEventListener('abort', () => reject(new Error('aborted by caller')), {
            once: true,
          })
        }),
    )

    const uploader = usePresignedUpload(options)
    const pending = uploader.upload(item())
    uploader.abort()
    rejectInit?.(new Error('init cancelled'))

    await expect(pending).rejects.toThrow('업로드가 취소되었습니다')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
