import apiClient, { unwrapResponse } from './client'
import type { ResData, PageRequest, PageResponse } from '@/types/api'
import type {
  Video,
  VideoCreateRequest,
  VideoPublishRequest,
  VideoListFilter,
  ContentImage,
  OptimizationCheckRequest,
  OptimizationCheckResponse,
  VideoTranslation,
  VideoFeedResponse,
  PlatformUploadCapability,
  VideoDownloadAvailability,
  VideoImportJob,
  VideoImportResult,
  VideoDeletionResult,
} from '@/types/video'

const IMPORT_POLL_INTERVAL_MS = 3_000
/** 10GB 원본도 이 안에 끝난다. 넘으면 기다리기를 멈출 뿐 서버 작업은 계속된다. */
const IMPORT_MAX_WAIT_MS = 3 * 60 * 60 * 1_000
const IMPORT_MAX_TRANSIENT_FAILURES = 5

export const videoApi = {
  getImportAvailability() {
    return apiClient
      .get<ResData<VideoDownloadAvailability>>('/videos/import-url/availability')
      .then(unwrapResponse)
  },

  /**
   * URL 에서 영상을 가져온다. **서버 작업을 시작하고 끝날 때까지 상태를 묻는다.**
   *
   * 예전에는 POST 한 번이 끝날 때까지 기다렸다. nginx 는 1분에 연결을 끊어, 1분 넘게 걸리는 가져오기는
   * 서버가 끝까지 해내는데도 화면은 실패를 보였다. 10GB 원본은 수십 분이 걸린다.
   *
   * 호출하는 쪽의 약속은 그대로다 — 성공하면 결과를, 실패하면 사용자에게 보일 문구를 담은 Error 를 던진다.
   */
  async importUrl(
    request: { url: string; title?: string },
    options: { pollIntervalMs?: number; maxWaitMs?: number; sleep?: (ms: number) => Promise<void> } = {},
  ): Promise<VideoImportResult> {
    const pollIntervalMs = options.pollIntervalMs ?? IMPORT_POLL_INTERVAL_MS
    const maxWaitMs = options.maxWaitMs ?? IMPORT_MAX_WAIT_MS
    const sleep = options.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))

    let job = await apiClient
      .post<ResData<VideoImportJob>>('/videos/import-url', request)
      .then(unwrapResponse)

    let waited = 0
    let transientFailures = 0
    while (job.status === 'QUEUED' || job.status === 'RUNNING') {
      if (waited >= maxWaitMs) {
        throw new Error('가져오기가 너무 오래 걸립니다. 잠시 뒤 내 영상 목록에서 확인해 주세요.')
      }
      await sleep(pollIntervalMs)
      waited += pollIntervalMs
      try {
        job = await apiClient
          .get<ResData<VideoImportJob>>(`/videos/import-url/jobs/${encodeURIComponent(job.jobId)}`)
          .then(unwrapResponse)
        transientFailures = 0
      } catch (error) {
        // 작업 상태는 서버 메모리에 있다. 재기동하면 사라지지만 영상은 이미 만들어졌을 수 있다.
        if ((error as { response?: { status?: number } })?.response?.status === 404) {
          throw new Error('가져오기 상태를 확인할 수 없습니다. 서버가 다시 시작됐을 수 있으니 내 영상 목록을 확인해 주세요.')
        }
        // 잠깐의 네트워크 끊김으로 수십 분짜리 작업을 실패로 보이게 하지 않는다.
        transientFailures += 1
        if (transientFailures >= IMPORT_MAX_TRANSIENT_FAILURES) throw error
      }
    }

    if (job.status === 'SUCCEEDED' && job.result) return job.result
    throw new Error(job.errorMessage || '소스 영상을 가져오지 못했습니다.')
  },

  getUploadCapabilities() {
    return apiClient
      .get<ResData<PlatformUploadCapability[]>>('/videos/stream-publish/capabilities')
      .then(unwrapResponse)
  },

  list(filter: VideoListFilter & PageRequest) {
    return apiClient
      .get<ResData<PageResponse<Video>>>('/videos', { params: filter })
      .then(unwrapResponse)
  },

  get(id: number) {
    return apiClient.get<ResData<Video>>(`/videos/${id}`).then(unwrapResponse)
  },

  create(request: VideoCreateRequest) {
    return apiClient.post<ResData<Video>>('/videos', request).then(unwrapResponse)
  },

  generate(request: {
    type: 'image-text-slides'
    output: 'vertical' | 'horizontal'
    customParams: { prompt: string; title?: string; tags?: string[] }
  }) {
    return apiClient
      .post<ResData<Array<{ id: string; path: string }>>>('/videos/generate', request, { timeout: 240000 })
      .then(unwrapResponse)
  },

  update(id: number, request: Partial<VideoCreateRequest>) {
    return apiClient.put<ResData<Video>>(`/videos/${id}`, request).then(unwrapResponse)
  },

  publish(id: number, request: VideoPublishRequest) {
    return apiClient.post<ResData<Video>>(`/videos/${id}/publish`, request).then(unwrapResponse)
  },

  retry(id: number, platform: string) {
    return apiClient.post<ResData<void>>(`/videos/${id}/retry/${platform}`).then(unwrapResponse)
  },

  retryUpload(id: number, uploadId: number) {
    return apiClient.post<ResData<void>>(`/videos/${id}/uploads/${uploadId}/retry`).then(unwrapResponse)
  },

  recheck(id: number, platform: string) {
    return apiClient.post<ResData<void>>(`/videos/${id}/recheck/${platform}`).then(unwrapResponse)
  },

  recheckUpload(id: number, uploadId: number) {
    return apiClient.post<ResData<void>>(`/videos/${id}/uploads/${uploadId}/recheck`).then(unwrapResponse)
  },

  recycle(id: number, request: {
    title: string
    description?: string
    tags: string[]
    category?: string
    platforms: Array<{
      platform: string
      channelId?: number
      title?: string
      description?: string
      tags?: string[]
      scheduledAt?: string
    }>
  }) {
    return apiClient.post<ResData<{ videoId: number; uploads: Array<{ platform: string; status: string; errorMessage?: string }> }>>(
      `/videos/${id}/recycle`,
      request,
    ).then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient.delete<ResData<VideoDeletionResult>>(`/videos/${id}`).then(unwrapResponse)
  },

  confirmUpload(videoId: number) {
    return apiClient.post<ResData<void>>(`/videos/${videoId}/upload/complete`).then(unwrapResponse)
  },

  optimizationCheck(request: OptimizationCheckRequest) {
    return apiClient
      .post<ResData<OptimizationCheckResponse>>('/videos/optimization-check', request)
      .then(unwrapResponse)
  },

  // Content Images
  uploadImages(videoId: number, files: File[]) {
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    return apiClient
      .post<ResData<ContentImage[]>>(`/videos/${videoId}/images`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000,
      })
      .then(unwrapResponse)
  },

  getImages(videoId: number) {
    return apiClient
      .get<ResData<ContentImage[]>>(`/videos/${videoId}/images`)
      .then(unwrapResponse)
  },

  reorderImages(videoId: number, imageIds: number[]) {
    return apiClient
      .put<ResData<void>>(`/videos/${videoId}/images/reorder`, { imageIds })
      .then(unwrapResponse)
  },

  // Translations
  getTranslations(videoId: number) {
    return apiClient
      .get<ResData<VideoTranslation[]>>(`/videos/${videoId}/translations`)
      .then(unwrapResponse)
  },

  requestTranslation(videoId: number, languages: string[]) {
    return apiClient
      .post<ResData<VideoTranslation[]>>(`/videos/${videoId}/translations`, { languages })
      .then(unwrapResponse)
  },

  updateTranslation(videoId: number, translationId: number, data: { title?: string; description?: string }) {
    return apiClient
      .put<ResData<VideoTranslation>>(`/videos/${videoId}/translations/${translationId}`, data)
      .then(unwrapResponse)
  },

  deleteTranslation(videoId: number, translationId: number) {
    return apiClient
      .delete<ResData<void>>(`/videos/${videoId}/translations/${translationId}`)
      .then(unwrapResponse)
  },

  /**
   * 라이브러리의 영상 에셋으로 **편집 가능한 영상 초안**을 만든다.
   *
   * 서버가 오브젝트를 새 영상 전용 경로로 복사하므로 원본 에셋은 그대로 남는다 —
   * 나중에 에셋을 정리해도 이 초안은 깨지지 않는다.
   */
  createFromAsset(assetId: number) {
    return apiClient
      .post<ResData<{ videoId: number }>>(`/videos/from-asset/${assetId}`)
      .then(unwrapResponse)
  },

  /**
   * 플랫폼 피드.
   *
   * **숫자 페이지 이동은 서버가 지원하지 않는다.** 각 플랫폼 커서가 독립적이라 "N 번째
   * 페이지" 를 만들 수 없다. 이어보려면 이전 응답의 `nextPageTokens` 를
   * `channelToken=<채널ID>:<토큰>` 형태로 돌려준다.
   */
  feed(params: { platform?: string; size?: number; sort?: string; channelToken?: string[] }) {
    return apiClient
      .get<ResData<VideoFeedResponse>>('/videos/feed', { params })
      .then(unwrapResponse)
  },
}
