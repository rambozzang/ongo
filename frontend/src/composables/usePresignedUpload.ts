/**
 * Presigned URL 기반 직접 업로드 컴포저블.
 * 클라이언트 → S3/R2/MinIO 직접 전송 (서버 대역폭 미사용).
 *
 * ## 두 가지 경로
 *
 * - **작은 파일(< MULTIPART_THRESHOLD)**: presigned PUT 한 번. 기존 경로 그대로다.
 * - **큰 파일**: 멀티파트. 16 MiB 안팎의 조각으로 나눠 몇 개씩 동시에 보낸다.
 *   끊기면 **실패한 조각만** 다시 보내고, 일시정지·재시도 때는 이미 올린 조각을 건너뛴다.
 *
 * 예전에는 모든 파일이 PUT 한 번이었다. 2 GB 를 올리다 끊기면 처음부터 다시 올려야 했고,
 * 전체 30분 제한 때문에 업로드 속도가 약 9 Mbps 미만이면 2 GB 는 끝까지 갈 수 없었다.
 *
 * 스토리지가 멀티파트를 지원하지 않으면(로컬 MinIO) 서버가 `multipart=false` 와 단일 PUT
 * URL 을 돌려주고, 여기서 그대로 폴백한다.
 */

/** 이 크기 이상이면 멀티파트로 보낸다. 작은 파일은 요청 수만 늘어 손해다. */
export const MULTIPART_THRESHOLD = 64 * 1024 * 1024
/** 동시에 보내는 조각 수. 브라우저 호스트당 연결 한도(보통 6)보다 작게 둔다. */
const PART_CONCURRENCY = 4
/** 조각 하나의 재시도 횟수와 간격(ms). 간격은 시도마다 두 배. */
const PART_MAX_ATTEMPTS = 4
const PART_RETRY_BASE_MS = 1000
/** 조각 하나의 제한 시간. 전체가 아니라 조각 단위라 큰 파일도 느린 회선에서 끝까지 간다. */
const PART_TIMEOUT_MS = 10 * 60 * 1000
/** 서버의 MAX_PART_URLS_PER_REQUEST(20) 이하. */
const PART_URL_BATCH = 8

interface MultipartSession {
  videoId: number
  uploadId: string
  objectKey: string
  partSize: number
  partCount: number
  /** 이 세션을 만든 파일. 같은 큐 항목이라도 파일이 바뀌면 세션을 버린다. */
  fileSignature: string
  completed: Set<number>
}

/**
 * **모듈 수준**에 둔다. 큐 스토어는 업로드를 시작할 때마다 이 컴포저블을 새로 만들기
 * 때문에, 인스턴스 안에 두면 일시정지 후 재개·실패 후 재시도 때 세션이 사라져 처음부터
 * 다시 올리게 된다. 키는 큐 항목 id 다(재시도해도 바뀌지 않는다).
 */
const multipartSessions = new Map<string, MultipartSession>()

function fileSignature(file: File): string {
  return `${file.name}:${file.size}:${file.lastModified}`
}

/** 테스트 전용 — 모듈 상태를 비운다. */
export function __resetMultipartSessionsForTest(): void {
  multipartSessions.clear()
}

export interface PresignedUploadItem {
  id: string
  file: File
  fileName: string
  fileSize: number
  status: string
  progress: number
  error?: string
  startedAt?: string
  completedAt?: string
  metadata?: {
    title: string
    description?: string
    tags?: string[]
    category?: string
  }
  platformConfigs?: Array<{
    platform: string
    channelId?: number
    title: string
    description: string
    tags: string[]
    visibility: string
    scheduledAt?: string
  }>
}

export interface PresignedUploadOptions {
  getBaseUrl?: () => string
  getToken?: () => string
  onProgress?: (id: string, progress: number) => void
  onStatusChange?: (id: string, status: string) => void
  onComplete?: (id: string) => void
  onError?: (id: string, error: string) => void
  onSpeedUpdate?: (id: string, bytesPerSecond: number, remainingSeconds: number) => void
  /** Check if upload should continue (e.g., not paused) */
  shouldContinue?: (id: string) => boolean
  /** Abort a stuck upload instead of leaving the UI in an indefinite loading state. */
  timeoutMs?: number
}

export function usePresignedUpload(options: PresignedUploadOptions = {}) {
  let currentXhr: XMLHttpRequest | null = null
  /** 멀티파트는 조각 여러 개를 동시에 보낸다. 취소·시간초과 때 전부 끊어야 한다. */
  const activeXhrs = new Set<XMLHttpRequest>()
  let currentAbortController: AbortController | null = null
  let currentSession: { itemId: string; session: MultipartSession } | null = null
  let currentBaseUrl = ''
  let currentToken = ''
  let abortRequested = false

  const DEFAULT_TIMEOUT_MS = 30 * 60 * 1000

  function getBaseUrl(): string {
    if (options.getBaseUrl) return options.getBaseUrl()
    return (
      (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_API_BASE_URL ||
      '/api/v1'
    )
  }

  function getToken(): string {
    if (options.getToken) return options.getToken()
    return localStorage.getItem('accessToken') || ''
  }

  // 서버 JSON 오류에서 **안정 코드**(STORAGE_QUOTA_EXCEEDED 등)를 보존한다.
  // fetch 는 axios 와 달리 실패를 일반 Error 로만 던지므로, 안정 코드를 잃으면
  // 화면이 업그레이드 경로를 판별하지 못한다. 다만 사람이 읽을 문장(한국어 메시지)을
  // 안정 코드로 오인하지 않도록, 코드 형태(`^[A-Z][A-Z0-9_]*$`)일 때만 response 에 보관한다.
  // abort/network/413(HTML 본문) 같은 일반 오류는 이 헬퍼를 거치지 않아 기존 동작을 유지한다.
  const STABLE_CODE = /^[A-Z][A-Z0-9_]*$/

  function toServerError(response: Response, body: unknown): Error {
    const data = (body ?? null) as { error?: unknown; message?: unknown } | null
    const rawError = typeof data?.error === 'string' ? data.error : undefined
    const rawMessage = typeof data?.message === 'string' ? data.message : undefined
    const isStableCode = rawError != null && STABLE_CODE.test(rawError)
    const message = rawMessage ?? (isStableCode ? rawError! : (rawError ?? `업로드 실패: ${response.status}`))
    const error = new Error(message)
    if (isStableCode) {
      ;(error as unknown as { response: { status: number; data: { error: string; message: string | null } } }).response =
        {
          status: response.status,
          data: { error: rawError!, message: rawMessage ?? null },
        }
    }
    return error
  }

  function authHeaders(token: string): Record<string, string> {
    return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
  }

  async function postJson(url: string, token: string, body: unknown, signal: AbortSignal): Promise<Response> {
    return fetch(url, {
      method: 'POST',
      headers: authHeaders(token),
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  }

  /**
   * 파일(또는 조각)을 presigned URL 로 PUT 한다.
   *
   * @param onLoaded 지금까지 보낸 바이트. 진행률·속도 계산은 호출부가 한다.
   * @param timeoutMs 0 이면 제한 없음. 조각 전송에서는 조각 단위 제한을 건다.
   * @returns 일시정지로 멈췄으면 false, 끝까지 보냈으면 true.
   */
  function putBlob(
    itemId: string,
    url: string,
    body: Blob,
    contentType: string | null,
    onLoaded: (loaded: number, total: number) => void,
    timeoutMs = 0,
  ): Promise<boolean> {
    return new Promise<boolean>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      activeXhrs.add(xhr)
      currentXhr = xhr
      const done = () => {
        activeXhrs.delete(xhr)
        if (currentXhr === xhr) currentXhr = null
      }

      xhr.upload.onprogress = (e: ProgressEvent) => {
        if (!e.lengthComputable) return
        // Pause 체크
        if (options.shouldContinue && !options.shouldContinue(itemId)) {
          xhr.abort()
          return
        }
        onLoaded(e.loaded, e.total)
      }

      xhr.onload = () => {
        done()
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve(true)
        } else {
          reject(new Error(`S3 업로드 실패: ${xhr.status}`))
        }
      }

      xhr.onerror = () => {
        done()
        reject(new Error('네트워크 오류로 업로드 실패'))
      }

      xhr.ontimeout = () => {
        done()
        reject(new Error('조각 전송 시간이 초과되었습니다'))
      }

      xhr.onabort = () => {
        done()
        // shouldContinue가 false인 경우 (일시정지) — 에러 아님
        if (options.shouldContinue && !options.shouldContinue(itemId)) {
          resolve(false)
          return
        }
        reject(new Error('업로드가 취소되었습니다'))
      }

      xhr.open('PUT', url)
      if (timeoutMs > 0) xhr.timeout = timeoutMs
      // 조각 PUT 에는 Content-Type 을 넣지 않는다. 조각 서명에 포함되지 않은 헤더라
      // 넣어도 무해하지만, 오브젝트의 Content-Type 은 세션을 열 때 이미 정해졌다.
      if (contentType) xhr.setRequestHeader('Content-Type', contentType)
      xhr.send(body)
    })
  }

  /** 단일 PUT 진행률·속도 보고. 기존 동작 그대로다. */
  function singleProgressReporter(itemId: string): (loaded: number, total: number) => void {
    let lastCalcTime = Date.now()
    let lastCalcBytes = 0
    return (loaded, total) => {
      const progress = Math.min(99, Math.round((loaded / total) * 100))
      options.onProgress?.(itemId, progress)

      // Speed 계산
      const now = Date.now()
      const timeSinceLastCalc = (now - lastCalcTime) / 1000
      if (timeSinceLastCalc >= 1) {
        const bytesPerSecond = (loaded - lastCalcBytes) / timeSinceLastCalc
        const remainingBytes = total - loaded
        const remainingSeconds = bytesPerSecond > 0 ? remainingBytes / bytesPerSecond : 0
        options.onSpeedUpdate?.(itemId, bytesPerSecond, remainingSeconds)
        lastCalcTime = now
        lastCalcBytes = loaded
      }
    }
  }

  /** 단일 PUT 업로드 후 서버에 확정을 요청한다. 일시정지면 null. */
  async function putAndConfirmSingle(
    item: PresignedUploadItem,
    baseUrl: string,
    token: string,
    videoId: number,
    presignedUrl: string,
    signal: AbortSignal,
  ): Promise<number | null> {
    // Step 2: PUT 파일을 presigned URL로 직접 업로드
    await putBlob(item.id, presignedUrl, item.file, item.file.type || 'video/mp4', singleProgressReporter(item.id))

    // Pause로 인한 중단인 경우 완료 처리 스킵
    if (options.shouldContinue && !options.shouldContinue(item.id)) {
      return null
    }

    // Step 3: 백엔드에 업로드 완료 알림
    const confirmResponse = await fetch(`${baseUrl}/videos/${videoId}/upload/complete`, {
      method: 'POST',
      headers: authHeaders(token),
      signal,
    })

    if (!confirmResponse.ok) {
      const errorBody = await confirmResponse.json().catch(() => null)
      throw toServerError(confirmResponse, errorBody)
    }
    return videoId
  }

  /** 작은 파일: 기존 경로(`/upload/init` → PUT → `/upload/complete`). */
  async function uploadSingle(
    item: PresignedUploadItem,
    baseUrl: string,
    token: string,
    signal: AbortSignal,
  ): Promise<number | null> {
    // Step 1: Init upload — videoId + presigned URL 발급
    const initResponse = await fetch(`${baseUrl}/videos/upload/init`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({
        filename: item.fileName,
        fileSize: item.fileSize,
        contentType: item.file.type || 'video/mp4',
      }),
      signal,
    })

    if (!initResponse.ok) {
      const errorBody = await initResponse.json().catch(() => null)
      throw toServerError(initResponse, errorBody)
    }

    const initData = await initResponse.json()
    const videoId = initData.data?.id ?? initData.data?.videoId
    const presignedUrl = initData.data?.uploadUrl

    if (!videoId) {
      throw new Error('videoId를 받지 못했습니다')
    }
    if (!presignedUrl) {
      throw new Error('presigned URL을 받지 못했습니다')
    }

    return putAndConfirmSingle(item, baseUrl, token, videoId, presignedUrl, signal)
  }

  // ── 큰 파일: 멀티파트 ───────────────────────────────────────────────

  async function startMultipart(
    item: PresignedUploadItem,
    baseUrl: string,
    token: string,
    signal: AbortSignal,
  ): Promise<MultipartSession | { videoId: number; uploadUrl: string }> {
    const response = await postJson(
      `${baseUrl}/videos/upload/start`,
      token,
      { filename: item.fileName, fileSize: item.fileSize, contentType: item.file.type || 'video/mp4' },
      signal,
    )
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw toServerError(response, body)
    }
    const data = (await response.json()).data ?? {}
    if (!data.videoId) throw new Error('videoId를 받지 못했습니다')

    // 스토리지가 멀티파트를 지원하지 않는다(로컬 MinIO). 단일 PUT 으로 폴백한다.
    if (!data.multipart) {
      if (!data.uploadUrl) throw new Error('presigned URL을 받지 못했습니다')
      return { videoId: data.videoId, uploadUrl: data.uploadUrl }
    }
    if (!data.uploadId || !data.objectKey || !data.partSize || !data.partCount) {
      throw new Error('멀티파트 업로드 정보를 받지 못했습니다')
    }
    return {
      videoId: data.videoId,
      uploadId: data.uploadId,
      objectKey: data.objectKey,
      partSize: data.partSize,
      partCount: data.partCount,
      fileSignature: fileSignature(item.file),
      completed: new Set<number>(),
    }
  }

  async function fetchPartUrls(
    baseUrl: string,
    token: string,
    session: MultipartSession,
    partNumbers: number[],
    signal: AbortSignal,
  ): Promise<Record<string, string>> {
    const response = await postJson(
      `${baseUrl}/videos/${session.videoId}/upload/multipart/parts`,
      token,
      { uploadId: session.uploadId, objectKey: session.objectKey, partNumbers },
      signal,
    )
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw toServerError(response, body)
    }
    return ((await response.json()).data?.urls ?? {}) as Record<string, string>
  }

  function sleep(ms: number, signal: AbortSignal): Promise<void> {
    return new Promise((resolve, reject) => {
      const t = setTimeout(resolve, ms)
      signal.addEventListener('abort', () => {
        clearTimeout(t)
        reject(new Error('업로드가 취소되었습니다'))
      }, { once: true })
    })
  }

  /**
   * 남은 조각을 보낸다. 조각마다 재시도하고, **재시도 때마다 URL 을 새로 받는다** —
   * 실패 원인이 URL 만료(403)여도 다음 시도는 새 서명으로 간다.
   *
   * @returns 일시정지로 멈췄으면 false.
   */
  async function sendParts(
    item: PresignedUploadItem,
    baseUrl: string,
    token: string,
    session: MultipartSession,
    signal: AbortSignal,
  ): Promise<boolean> {
    const pending: number[] = []
    for (let n = 1; n <= session.partCount; n++) if (!session.completed.has(n)) pending.push(n)

    const sizeOf = (n: number) => (n < session.partCount ? session.partSize : item.fileSize - session.partSize * (session.partCount - 1))
    const inFlight = new Map<number, number>()
    let completedBytes = 0
    session.completed.forEach((n) => { completedBytes += sizeOf(n) })

    let lastCalcTime = Date.now()
    let lastCalcBytes = completedBytes
    const report = () => {
      let loaded = completedBytes
      inFlight.forEach((v) => { loaded += v })
      options.onProgress?.(item.id, Math.min(99, Math.round((loaded / item.fileSize) * 100)))
      const now = Date.now()
      const dt = (now - lastCalcTime) / 1000
      if (dt >= 1) {
        const bps = (loaded - lastCalcBytes) / dt
        options.onSpeedUpdate?.(item.id, bps, bps > 0 ? (item.fileSize - loaded) / bps : 0)
        lastCalcTime = now
        lastCalcBytes = loaded
      }
    }
    report()

    // URL 은 조각 몇 개씩 미리 받아 둔다. 한꺼번에 받으면 느린 회선에서 뒷조각 URL 이 만료된다.
    const urlCache = new Map<number, string>()
    const takeUrl = async (n: number): Promise<string> => {
      const cached = urlCache.get(n)
      if (cached) {
        urlCache.delete(n)
        return cached
      }
      const batch = [n, ...pending.filter((p) => p !== n && !urlCache.has(p) && !session.completed.has(p) && !inFlight.has(p))]
        .slice(0, PART_URL_BATCH)
      const urls = await fetchPartUrls(baseUrl, token, session, batch, signal)
      batch.forEach((p) => { if (p !== n && urls[String(p)]) urlCache.set(p, urls[String(p)]) })
      const url = urls[String(n)]
      if (!url) throw new Error(`조각 ${n} 의 업로드 URL 을 받지 못했습니다`)
      return url
    }

    let paused = false
    let failure: Error | null = null
    let cursor = 0

    const worker = async () => {
      while (!paused && !failure && !signal.aborted) {
        const n = pending[cursor++]
        if (n === undefined) return
        const start = (n - 1) * session.partSize
        const blob = item.file.slice(start, start + sizeOf(n))

        for (let attempt = 1; ; attempt++) {
          try {
            // 재시도라면 캐시된(어쩌면 만료된) URL 을 버리고 새로 받는다.
            if (attempt > 1) urlCache.delete(n)
            const url = await takeUrl(n)
            inFlight.set(n, 0)
            const finished = await putBlob(item.id, url, blob, null, (loaded) => {
              inFlight.set(n, loaded)
              report()
            }, PART_TIMEOUT_MS)
            inFlight.delete(n)
            if (!finished) {
              paused = true
              return
            }
            session.completed.add(n)
            completedBytes += sizeOf(n)
            report()
            break
          } catch (error) {
            inFlight.delete(n)
            if (signal.aborted || abortRequested) throw error
            if (attempt >= PART_MAX_ATTEMPTS) {
              failure = error instanceof Error ? error : new Error(String(error))
              return
            }
            await sleep(PART_RETRY_BASE_MS * 2 ** (attempt - 1), signal)
          }
        }
      }
    }

    await Promise.all(Array.from({ length: Math.min(PART_CONCURRENCY, pending.length) }, worker))
    if (failure) throw failure
    if (paused || (options.shouldContinue && !options.shouldContinue(item.id))) {
      // 진행 중이던 다른 조각도 멈춘다. 끝난 조각은 세션에 남아 재개 때 건너뛴다.
      activeXhrs.forEach((x) => x.abort())
      return false
    }
    return true
  }

  async function completeMultipart(
    baseUrl: string,
    token: string,
    session: MultipartSession,
    signal: AbortSignal,
  ): Promise<void> {
    for (let attempt = 1; ; attempt++) {
      const response = await postJson(
        `${baseUrl}/videos/${session.videoId}/upload/multipart/complete`,
        token,
        { uploadId: session.uploadId, objectKey: session.objectKey },
        signal,
      )
      if (response.ok) return
      const body = await response.json().catch(() => null)
      const error = toServerError(response, body)
      const code = (body as { error?: string } | null)?.error
      if (code === 'MULTIPART_INCOMPLETE') {
        // 모든 조각이 2xx 였는데도 빠졌다고 한다. 스토리지 목록 반영이 잠깐 늦을 수 있어
        // 한 번 더 기다렸다 묻는다. 그래도 안 되면 어느 조각인지 믿을 수 없으므로 완료 표시를
        // 지워, 다음 재시도가 조각을 전부 다시 보내게 한다(같은 번호 덮어쓰기는 허용된다).
        if (attempt < 2) {
          await sleep(2000, signal)
          continue
        }
        session.completed.clear()
      }
      throw error
    }
  }

  /** 큰 파일: 멀티파트. 일시정지면 null. */
  async function uploadLarge(
    item: PresignedUploadItem,
    baseUrl: string,
    token: string,
    signal: AbortSignal,
  ): Promise<number | null> {
    let session = multipartSessions.get(item.id)
    // 같은 큐 항목이라도 파일이 바뀌었으면 이전 세션은 쓸 수 없다.
    if (session && session.fileSignature !== fileSignature(item.file)) {
      multipartSessions.delete(item.id)
      session = undefined
    }

    if (!session) {
      const started = await startMultipart(item, baseUrl, token, signal)
      if (!('uploadId' in started)) {
        return putAndConfirmSingle(item, baseUrl, token, started.videoId, started.uploadUrl, signal)
      }
      session = started
      multipartSessions.set(item.id, session)
    }
    currentSession = { itemId: item.id, session }

    const finished = await sendParts(item, baseUrl, token, session, signal)
    if (!finished) return null

    await completeMultipart(baseUrl, token, session, signal)
    // 서버에서 확정까지 끝났다. 재시도 대상이 아니므로 세션을 버린다.
    multipartSessions.delete(item.id)
    return session.videoId
  }

  async function upload(item: PresignedUploadItem): Promise<number | null> {
    const baseUrl = getBaseUrl()
    const token = getToken()
    const abortController = new AbortController()
    const useMultipart = item.fileSize >= MULTIPART_THRESHOLD
    // 전체 제한 시간은 단일 PUT 에만 건다. 멀티파트는 조각 단위 제한과 재시도가 대신한다 —
    // 전체에 걸면 느린 회선의 큰 파일이 다시 "30분 안에 못 올리면 실패" 로 돌아간다.
    const timeoutMs = useMultipart ? 0 : (options.timeoutMs ?? DEFAULT_TIMEOUT_MS)
    let timedOut = false
    const timeoutId =
      timeoutMs > 0
        ? setTimeout(() => {
            timedOut = true
            abortController.abort()
            activeXhrs.forEach((x) => x.abort())
          }, timeoutMs)
        : undefined
    currentAbortController = abortController
    currentBaseUrl = baseUrl
    currentToken = token
    abortRequested = false

    try {
      const videoId = useMultipart
        ? await uploadLarge(item, baseUrl, token, abortController.signal)
        : await uploadSingle(item, baseUrl, token, abortController.signal)
      if (videoId === null) return null

      if (item.metadata) {
        const updateResponse = await fetch(`${baseUrl}/videos/${videoId}`, {
          method: 'PUT',
          headers: authHeaders(token),
          body: JSON.stringify(item.metadata),
          signal: abortController.signal,
        })
        if (!updateResponse.ok) {
          const body = await updateResponse.json().catch(() => null)
          throw toServerError(updateResponse, body)
        }
      }

      if (item.platformConfigs?.length) {
        const publishResponse = await fetch(`${baseUrl}/videos/${videoId}/publish`, {
          method: 'POST',
          headers: authHeaders(token),
          body: JSON.stringify({ platforms: item.platformConfigs }),
          signal: abortController.signal,
        })
        if (!publishResponse.ok) {
          const body = await publishResponse.json().catch(() => null)
          throw toServerError(publishResponse, body)
        }
      }

      options.onProgress?.(item.id, 100)
      options.onComplete?.(item.id)
      return videoId
    } catch (error) {
      if (timedOut) throw new Error('업로드 시간이 초과되었습니다')
      if (abortRequested || abortController.signal.aborted) {
        throw new Error('업로드가 취소되었습니다')
      }
      throw error
    } finally {
      if (timeoutId !== undefined) clearTimeout(timeoutId)
      if (currentAbortController === abortController) currentAbortController = null
      currentXhr = null
      activeXhrs.clear()
    }
  }

  /**
   * 사용자가 취소했다. 멀티파트라면 서버에 세션 중단을 알려 R2 에 올라간 조각과 영상 행을
   * 정리하게 한다. 알리지 않으면 조각이 보이지 않는 채로 버킷 용량을 차지하다가
   * StaleUploadCleanupUseCase 가 회수할 때까지 남는다.
   */
  function abort(): void {
    abortRequested = true
    currentAbortController?.abort()
    activeXhrs.forEach((x) => x.abort())
    activeXhrs.clear()
    currentXhr?.abort()
    currentXhr = null

    const target = currentSession
    currentSession = null
    if (target) {
      multipartSessions.delete(target.itemId)
      const { session } = target
      // 취소 요청은 방금 abort 한 컨트롤러와 무관하게 보낸다. 실패해도 서버 정리 경로가 회수한다.
      void fetch(`${currentBaseUrl}/videos/${session.videoId}/upload/multipart/abort`, {
        method: 'POST',
        headers: authHeaders(currentToken),
        body: JSON.stringify({ uploadId: session.uploadId, objectKey: session.objectKey }),
      }).catch(() => undefined)
    }
  }

  return { upload, abort }
}
