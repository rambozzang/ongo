import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  ugcShortsPipelineApi,
  type PipelineStage,
  type PipelineRunResponse,
  type PipelineRunDetailResponse,
  type CreatePipelineRunRequest,
  type SelectHooksRequest,
  type ScheduleRunRequest,
  type RenderAvailabilityResponse,
  type RenderJobStatusResponse,
} from '@/api/ugcShortsPipeline'
import { useWorkspaceStore } from '@/stores/workspace'

export const useUgcShortsPipelineStore = defineStore('ugcShortsPipeline', () => {
  const workspaceStore = useWorkspaceStore()

  const runs = ref<PipelineRunResponse[]>([])
  const runsLoading = ref(false)
  const runsPage = ref(0)
  const runsTotalPages = ref(0)
  const runsHasNext = ref(false)
  const runsHasPrevious = ref(false)

  const detail = ref<PipelineRunDetailResponse | null>(null)
  const detailLoading = ref(false)

  const renderAvailability = ref<RenderAvailabilityResponse | null>(null)
  const renderAvailabilityLoading = ref(false)

  // 키: `${runId}:${seq}`
  const renderJobs = ref<Record<string, RenderJobStatusResponse>>({})

  async function requireWorkspaceId(): Promise<number> {
    const id = await workspaceStore.ensureActiveWorkspace()
    if (id == null) {
      throw new Error('활성 워크스페이스가 없습니다. 먼저 워크스페이스를 선택하세요.')
    }
    return id
  }

  async function fetchRuns(page = 0, size = 20) {
    runsLoading.value = true
    try {
      const id = await workspaceStore.ensureActiveWorkspace()
      if (id == null) {
        runs.value = []
        return
      }
      const res = await ugcShortsPipelineApi.list(id, page, size)
      runs.value = res.content
      runsPage.value = res.page
      runsTotalPages.value = res.totalPages
      runsHasNext.value = res.hasNext
      runsHasPrevious.value = res.hasPrevious
    } finally {
      runsLoading.value = false
    }
  }

  async function fetchDetail(runId: number) {
    detailLoading.value = true
    try {
      detail.value = await ugcShortsPipelineApi.get(await requireWorkspaceId(), runId)
    } finally {
      detailLoading.value = false
    }
  }

  /** 생성 직후 상세 화면으로 이동할 수 있게 생성된 실행을 반환한다 */
  async function createRun(request: CreatePipelineRunRequest) {
    return await ugcShortsPipelineApi.create(await requireWorkspaceId(), request)
  }

  /** 단계 재실행 후 상세를 다시 읽어 진행 표시를 갱신한다 */
  async function rerunStage(runId: number, stage: PipelineStage) {
    await ugcShortsPipelineApi.rerunStage(await requireWorkspaceId(), runId, stage)
    await fetchDetail(runId)
  }

  async function selectHooks(runId: number, request: SelectHooksRequest) {
    await ugcShortsPipelineApi.selectHooks(await requireWorkspaceId(), runId, request)
    await fetchDetail(runId)
  }

  async function confirmSchedule(runId: number, request: ScheduleRunRequest) {
    await ugcShortsPipelineApi.confirmSchedule(await requireWorkspaceId(), runId, request)
    await fetchDetail(runId)
  }

  /** 완성 영상 연결 후 상세를 다시 읽어 클립 상태를 갱신한다 */
  async function attachRenderedVideo(runId: number, clipId: number, videoId: number) {
    await ugcShortsPipelineApi.attachRenderedVideo(await requireWorkspaceId(), runId, clipId, videoId)
    await fetchDetail(runId)
  }

  async function deleteRun(runId: number) {
    await ugcShortsPipelineApi.remove(await requireWorkspaceId(), runId)
    runs.value = runs.value.filter((r) => r.id !== runId)
  }

  // ---- 서버 렌더 ----
  async function fetchRenderAvailability() {
    renderAvailabilityLoading.value = true
    try {
      renderAvailability.value = await ugcShortsPipelineApi.getRenderAvailability()
    } finally {
      renderAvailabilityLoading.value = false
    }
  }

  // 백엔드 경로가 clipId 기반이다(/clips/{clipId}/render). 기존 render-spec·
  // rendered-video 엔드포인트와 같은 규약이라 여기에 맞춘다.
  function renderJobKey(runId: number, clipId: number): string {
    return `${runId}:${clipId}`
  }

  async function startRender(runId: number, clipId: number): Promise<string> {
    const { renderJobId } = await ugcShortsPipelineApi.startRender(
      await requireWorkspaceId(),
      runId,
      clipId,
    )
    renderJobs.value[renderJobKey(runId, clipId)] = {
      status: 'QUEUED',
      progress: null,
      videoId: null,
      failureReason: null,
    }
    return renderJobId
  }

  async function fetchRenderStatus(runId: number, clipId: number) {
    const status = await ugcShortsPipelineApi.getRenderStatus(
      await requireWorkspaceId(),
      runId,
      clipId,
    )
    renderJobs.value[renderJobKey(runId, clipId)] = status
    return status
  }

  return {
    runs,
    runsLoading,
    runsPage,
    runsTotalPages,
    runsHasNext,
    runsHasPrevious,
    detail,
    detailLoading,
    renderAvailability,
    renderAvailabilityLoading,
    renderJobs,
    fetchRuns,
    fetchDetail,
    createRun,
    rerunStage,
    selectHooks,
    confirmSchedule,
    attachRenderedVideo,
    deleteRun,
    fetchRenderAvailability,
    startRender,
    fetchRenderStatus,
  }
})
