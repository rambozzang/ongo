import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  MULTIPART_THRESHOLD,
  __resetMultipartSessionsForTest,
  usePresignedUpload,
} from './usePresignedUpload'

/*
 * 멀티파트 업로드가 **끊겨도 처음부터 다시 올리지 않는지** 고정한다.
 *
 * 예전에는 모든 파일이 presigned PUT 한 번이었다. 2 GB 를 올리다 끊기면 처음부터 다시
 * 올려야 했고, 전체 30분 제한 때문에 느린 회선에서는 2 GB 를 끝까지 보낼 수 없었다.
 */

const MiB = 1024 * 1024
/** 서버가 정한 조각 크기. 모든 시험 파일이 MULTIPART_THRESHOLD(64 MiB)를 넘도록 크게 잡는다. */
const PART = 40 * MiB

/** 응답 정책을 받아 스스로 응답하는 가짜 XHR. 조각 여러 개가 동시에 오가는 것을 흉내 낸다. */
class AutoXHR {
  static instances: AutoXHR[] = []
  /** URL 을 보고 상태코드를 정한다. 'hang' 이면 응답하지 않는다(일시정지·취소 시험용). */
  static policy: (url: string) => number | 'error' | 'hang' = () => 200
  readonly upload: { onprogress: ((e: ProgressEvent) => void) | null } = { onprogress: null }
  status = 0
  url = ''
  timeout = 0
  headers: Record<string, string> = {}
  body: Blob | null = null
  aborted = false
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  ontimeout: (() => void) | null = null

  constructor() {
    AutoXHR.instances.push(this)
  }
  open(_m: string, url: string) {
    this.url = url
  }
  setRequestHeader(k: string, v: string) {
    this.headers[k] = v
  }
  send(body: Blob) {
    this.body = body
    const outcome = AutoXHR.policy(this.url)
    if (outcome === 'hang') return
    setTimeout(() => {
      if (this.aborted) return
      const size = body?.size ?? 0
      this.upload.onprogress?.({ lengthComputable: true, loaded: size, total: size } as ProgressEvent)
      if (outcome === 'error') {
        this.onerror?.()
        return
      }
      this.status = outcome
      this.onload?.()
    }, 0)
  }
  abort() {
    this.aborted = true
    this.onabort?.()
  }
}

/** 64 MiB 를 실제로 만들지 않는다. 코드가 쓰는 것은 name·size·lastModified·type·slice 뿐이다. */
function bigFile(size: number, name = 'long.mp4', lastModified = 1) {
  return {
    name,
    size,
    lastModified,
    type: 'video/mp4',
    slice: (a: number, b: number) => ({ size: Math.min(b, size) - a }) as Blob,
  } as unknown as File
}

function item(size = 3 * PART, id = 'q-1') {
  return {
    id,
    file: bigFile(size),
    fileName: 'long.mp4',
    fileSize: size,
    status: 'uploading',
    progress: 0,
  }
}

const ok = (data: unknown) =>
  ({ ok: true, status: 200, json: async () => ({ success: true, data }) }) as unknown as Response
const fail = (status: number, error: string, message = error) =>
  ({ ok: false, status, json: async () => ({ success: false, error, message }) }) as unknown as Response

describe('usePresignedUpload — 멀티파트', () => {
  const fetchMock = vi.fn()
  let partRequests: number[][]
  let startCalls: number

  /** 조각 [partCount] 개짜리 세션을 여는 서버. 조각 크기는 PART. */
  function server(partCount: number, fileSize: number, overrides: Record<string, (body: any) => Response> = {}) {
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      const body = init?.body ? JSON.parse(String(init.body)) : undefined
      for (const [suffix, handler] of Object.entries(overrides)) {
        if (url.endsWith(suffix)) return handler(body)
      }
      if (url.endsWith('/videos/upload/start')) {
        startCalls++
        return ok({
          videoId: 7,
          multipart: true,
          uploadId: 'up-1',
          objectKey: 'videos/7/long.mp4',
          partSize: PART,
          partCount,
        })
      }
      if (url.endsWith('/upload/multipart/parts')) {
        partRequests.push(body.partNumbers)
        const urls: Record<string, string> = {}
        body.partNumbers.forEach((n: number) => {
          urls[String(n)] = `https://r2/part-${n}?v=${partRequests.length}`
        })
        return ok({ urls })
      }
      if (url.endsWith('/upload/multipart/complete')) return ok(null)
      if (url.endsWith('/upload/multipart/abort')) return ok(null)
      throw new Error(`예상하지 못한 요청: ${url} (fileSize=${fileSize})`)
    })
  }

  const putsTo = () => AutoXHR.instances.map((x) => Number(/part-(\d+)/.exec(x.url)?.[1]))

  beforeEach(() => {
    __resetMultipartSessionsForTest()
    AutoXHR.instances = []
    AutoXHR.policy = () => 200
    partRequests = []
    startCalls = 0
    vi.stubGlobal('XMLHttpRequest', AutoXHR)
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('큰 파일은 조각으로 나눠 올리고 서버에 완료를 알린다', async () => {
    const size = 4 * PART + 3 * MiB
    server(5, size)
    const onProgress = vi.fn()

    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't', onProgress })
      .upload(item(size))

    expect(videoId).toBe(7)
    expect(putsTo().sort()).toEqual([1, 2, 3, 4, 5])
    // 마지막 조각은 나머지 크기다 — 한 바이트라도 틀리면 서명 불일치로 거부된다.
    const last = AutoXHR.instances.find((x) => x.url.includes('part-5'))!
    expect(last.body?.size).toBe(3 * MiB)
    expect(fetchMock.mock.calls.some(([u]) => String(u).endsWith('/upload/multipart/complete'))).toBe(true)
    expect(onProgress).toHaveBeenLastCalledWith('q-1', 100)
    // 전체 30분 제한 대신 조각 단위 제한이 걸린다.
    expect(AutoXHR.instances.every((x) => x.timeout > 0)).toBe(true)
  })

  it('작은 파일은 기존 단일 PUT 경로를 그대로 쓴다', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.endsWith('/videos/upload/init')) return ok({ videoId: 3, uploadUrl: 'https://r2/single' })
      if (url.endsWith('/videos/3/upload/complete')) return ok(null)
      throw new Error(url)
    })

    const small = { ...item(MULTIPART_THRESHOLD - 1), file: bigFile(MULTIPART_THRESHOLD - 1) }
    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(small)

    expect(videoId).toBe(3)
    expect(AutoXHR.instances.map((x) => x.url)).toEqual(['https://r2/single'])
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/upload/start'))).toBe(false)
  })

  /** 로컬 MinIO 는 멀티파트가 없다. 서버가 단일 PUT URL 을 주면 그대로 폴백한다. */
  it('서버가 멀티파트를 지원하지 않으면 단일 PUT 으로 폴백한다', async () => {
    const size = 2 * PART
    fetchMock.mockImplementation(async (url: string) => {
      if (url.endsWith('/videos/upload/start')) return ok({ videoId: 9, multipart: false, uploadUrl: 'https://minio/put' })
      if (url.endsWith('/videos/9/upload/complete')) return ok(null)
      throw new Error(url)
    })

    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))

    expect(videoId).toBe(9)
    expect(AutoXHR.instances.map((x) => x.url)).toEqual(['https://minio/put'])
  })

  /**
   * **핵심.** 조각 하나가 실패하면 그 조각만 다시 보낸다. 그리고 재시도는 **새 URL** 로 간다 —
   * 실패 원인이 URL 만료(403)여도 다음 시도는 새 서명으로 가야 한다.
   */
  it('실패한 조각만 새 URL 로 다시 보낸다', async () => {
    const size = 3 * PART
    server(3, size)
    let failedOnce = false
    AutoXHR.policy = (url) => {
      if (url.includes('part-2') && !failedOnce) {
        failedOnce = true
        return 403
      }
      return 200
    }

    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))

    expect(videoId).toBe(7)
    const part2 = AutoXHR.instances.filter((x) => x.url.includes('part-2'))
    expect(part2).toHaveLength(2)
    expect(part2[0].url).not.toBe(part2[1].url)
    // 다른 조각은 한 번씩만 갔다.
    expect(AutoXHR.instances.filter((x) => x.url.includes('part-1'))).toHaveLength(1)
    expect(AutoXHR.instances.filter((x) => x.url.includes('part-3'))).toHaveLength(1)
  }, 10_000)

  /**
   * **가장 비싼 회귀.** 네트워크가 오래 끊겨 업로드가 실패해도, 재시도하면 이미 올린 조각은
   * 건너뛴다. 큐는 재시도 때 컴포저블을 새로 만들므로 세션이 모듈 수준에 있어야 한다.
   */
  it('실패 후 재시도하면 이미 올린 조각은 건너뛰고 세션을 다시 열지 않는다', async () => {
    const size = 3 * PART
    server(3, size)
    AutoXHR.policy = (url) => (url.includes('part-3') ? 'error' : 200)

    await expect(
      usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size)),
    ).rejects.toThrow('네트워크 오류')
    expect(startCalls).toBe(1)

    AutoXHR.instances = []
    AutoXHR.policy = () => 200
    // 큐가 하듯 새 인스턴스로 재시도한다.
    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))

    expect(videoId).toBe(7)
    expect(startCalls).toBe(1)
    expect(putsTo()).toEqual([3])
  }, 20_000)

  it('일시정지하면 null 을 돌려주고, 재개하면 남은 조각만 보낸다', async () => {
    const size = 3 * PART
    server(3, size)
    let paused = false
    AutoXHR.policy = (url) => (url.includes('part-3') ? 'hang' : 200)
    const shouldContinue = () => !paused

    const first = usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't', shouldContinue })
    const pending = first.upload(item(size))
    // part-3 이 매달린 뒤 일시정지를 감지하도록 진행 이벤트를 흘려 준다.
    await vi.waitFor(() => expect(AutoXHR.instances.some((x) => x.url.includes('part-3'))).toBe(true))
    // 1·2번이 **끝난 뒤** 일시정지한다. 전송 중에 멈춘 조각은 완료로 셀 수 없어 재개 때
    // 다시 가는 것이 맞다 — 이 시험은 "끝난 조각을 건너뛰는가" 를 본다.
    await vi.waitFor(() =>
      expect(AutoXHR.instances.filter((x) => /part-[12]\?/.test(x.url) && x.status === 200)).toHaveLength(2),
    )
    await new Promise((r) => setTimeout(r, 0))
    paused = true
    const hanging = AutoXHR.instances.find((x) => x.url.includes('part-3'))!
    hanging.upload.onprogress?.({ lengthComputable: true, loaded: 1, total: PART } as ProgressEvent)

    expect(await pending).toBeNull()

    AutoXHR.instances = []
    paused = false
    AutoXHR.policy = () => 200
    const videoId = await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't', shouldContinue })
      .upload(item(size))

    expect(videoId).toBe(7)
    expect(putsTo()).toEqual([3])
    expect(startCalls).toBe(1)
  })

  /** 취소하면 서버에 알려 R2 조각과 행을 정리하게 한다. 다음 업로드는 새 세션이다. */
  it('취소하면 서버에 세션 중단을 알리고 세션을 잊는다', async () => {
    const size = 3 * PART
    server(3, size)
    AutoXHR.policy = () => 'hang'

    const uploader = usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' })
    const pending = uploader.upload(item(size))
    await vi.waitFor(() => expect(AutoXHR.instances.length).toBeGreaterThan(0))
    uploader.abort()

    await expect(pending).rejects.toThrow('업로드가 취소되었습니다')
    const abortCall = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/videos/7/upload/multipart/abort'))
    expect(abortCall).toBeDefined()
    expect(JSON.parse(String(abortCall![1].body))).toEqual({ uploadId: 'up-1', objectKey: 'videos/7/long.mp4' })
    // 매달려 있던 조각 전송도 전부 끊겼다.
    expect(AutoXHR.instances.every((x) => x.aborted)).toBe(true)

    AutoXHR.instances = []
    AutoXHR.policy = () => 200
    await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))
    expect(startCalls).toBe(2)
  })

  it('조각 URL 은 한꺼번에가 아니라 몇 개씩 받는다', async () => {
    const size = 20 * PART
    server(20, size)

    await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))

    expect(partRequests.length).toBeGreaterThan(1)
    expect(Math.max(...partRequests.map((r) => r.length))).toBeLessThanOrEqual(8)
    expect(new Set(partRequests.flat()).size).toBe(20)
  })

  /**
   * 모든 조각이 2xx 였는데 서버가 빠졌다고 하면, 어느 조각을 믿을지 알 수 없다. 완료 표시를
   * 지워 다음 재시도가 조각을 전부 다시 보내게 한다 — 그러지 않으면 "다 올렸다" 고 믿는
   * 클라이언트가 같은 완료 요청만 되풀이한다.
   */
  it('서버가 조각 누락을 계속 알리면 다음 재시도에서 조각을 전부 다시 보낸다', async () => {
    const size = 2 * PART
    let completeCalls = 0
    server(2, size, {
      '/upload/multipart/complete': () => {
        completeCalls++
        return fail(400, 'MULTIPART_INCOMPLETE', '조각 누락')
      },
    })

    await expect(
      usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size)),
    ).rejects.toThrow('조각 누락')
    expect(completeCalls).toBe(2)

    AutoXHR.instances = []
    server(2, size)
    await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size))
    expect(putsTo().sort()).toEqual([1, 2])
  }, 10_000)

  /** 같은 큐 항목에 다른 파일을 넣었다면 이전 세션의 조각을 섞으면 안 된다. */
  it('같은 큐 항목이라도 파일이 바뀌면 새 세션을 연다', async () => {
    const size = 2 * PART
    server(2, size)
    AutoXHR.policy = (url) => (url.includes('part-2') ? 'error' : 200)
    await expect(
      usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(item(size)),
    ).rejects.toThrow()

    AutoXHR.policy = () => 200
    const other = { ...item(size), file: bigFile(size, 'long.mp4', 999) }
    await usePresignedUpload({ getBaseUrl: () => '/api/v1', getToken: () => 't' }).upload(other)

    expect(startCalls).toBe(2)
  }, 20_000)
})
