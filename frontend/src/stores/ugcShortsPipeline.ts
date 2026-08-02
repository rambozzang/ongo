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

  async function deleteRun(runId: number) {
    await ugcShortsPipelineApi.remove(await requireWorkspaceId(), runId)
    runs.value = runs.value.filter((r) => r.id !== runId)
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
    fetchRuns,
    fetchDetail,
    createRun,
    rerunStage,
    selectHooks,
    confirmSchedule,
    deleteRun,
  }
})
