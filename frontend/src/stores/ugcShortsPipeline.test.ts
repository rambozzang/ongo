import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUgcShortsPipelineStore } from './ugcShortsPipeline'
import { ugcShortsPipelineApi } from '@/api/ugcShortsPipeline'

const workspace = vi.hoisted(() => ({ ensureActiveWorkspace: vi.fn() }))

vi.mock('@/stores/workspace', () => ({
  useWorkspaceStore: vi.fn(() => workspace),
}))

vi.mock('@/api/ugcShortsPipeline', () => ({
  ugcShortsPipelineApi: {
    list: vi.fn(), get: vi.fn(), create: vi.fn(), rerunStage: vi.fn(), selectHooks: vi.fn(),
    confirmSchedule: vi.fn(), attachRenderedVideo: vi.fn(), remove: vi.fn(),
    getRenderAvailability: vi.fn(), startRender: vi.fn(), getRenderStatus: vi.fn(),
  },
}))

describe('UGC Shorts pipeline store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    workspace.ensureActiveWorkspace.mockResolvedValue(7)
    vi.mocked(ugcShortsPipelineApi.get).mockResolvedValue({ id: 10, clips: [] } as never)
  })

  it('loads paginated runs and keeps navigation metadata from the server', async () => {
    vi.mocked(ugcShortsPipelineApi.list).mockResolvedValue({
      content: [{ id: 10, status: 'RUNNING' }],
      page: 2,
      totalPages: 4,
      hasNext: true,
      hasPrevious: true,
    } as never)
    const store = useUgcShortsPipelineStore()

    await store.fetchRuns(2, 10)
    expect(ugcShortsPipelineApi.list).toHaveBeenCalledWith(7, 2, 10)
    expect(store.runs).toHaveLength(1)
    expect(store.runsPage).toBe(2)
    expect(store.runsTotalPages).toBe(4)
    expect(store.runsHasNext).toBe(true)
    expect(store.runsHasPrevious).toBe(true)
    expect(store.runsLoading).toBe(false)

    vi.mocked(ugcShortsPipelineApi.list).mockRejectedValueOnce(new Error('실행 목록 장애'))
    await expect(store.fetchRuns(2, 10)).rejects.toThrow('실행 목록 장애')
    expect(store.runs).toHaveLength(1)
    expect(store.runsLoadError).toBe('실행 목록 장애')
  })

  it('runs stage actions, refreshes detail, and removes a run locally', async () => {
    const store = useUgcShortsPipelineStore()
    store.runs = [{ id: 10 }, { id: 11 }] as never
    vi.mocked(ugcShortsPipelineApi.create).mockResolvedValue({ id: 12 } as never)

    await expect(store.createRun({ sourceVideoId: 4 } as never)).resolves.toMatchObject({ id: 12 })
    await store.fetchDetail(10)
    await store.rerunStage(10, 'HOOK')
    await store.selectHooks(10, { selections: [] } as never)
    await store.confirmSchedule(10, { startAt: '2099-03-01T12:00:00' } as never)
    await store.attachRenderedVideo(10, 3, 44)
    await store.deleteRun(10)

    expect(ugcShortsPipelineApi.rerunStage).toHaveBeenCalledWith(7, 10, 'HOOK')
    expect(ugcShortsPipelineApi.selectHooks).toHaveBeenCalledWith(7, 10, { selections: [] })
    expect(ugcShortsPipelineApi.confirmSchedule).toHaveBeenCalledWith(7, 10, { startAt: '2099-03-01T12:00:00' })
    expect(ugcShortsPipelineApi.attachRenderedVideo).toHaveBeenCalledWith(7, 10, 3, 44)
    expect(ugcShortsPipelineApi.remove).toHaveBeenCalledWith(7, 10)
    expect(store.runs.map((run) => run.id)).toEqual([11])
  })

  it('tracks render availability, queued jobs, and polled render status', async () => {
    vi.mocked(ugcShortsPipelineApi.getRenderAvailability).mockResolvedValue({ available: true } as never)
    vi.mocked(ugcShortsPipelineApi.startRender).mockResolvedValue({ renderJobId: 'job-1' } as never)
    vi.mocked(ugcShortsPipelineApi.getRenderStatus).mockResolvedValue({
      status: 'COMPLETED', progress: 100, videoId: 44, failureReason: null,
    } as never)
    const store = useUgcShortsPipelineStore()

    await store.fetchRenderAvailability()
    await expect(store.startRender(10, 3)).resolves.toBe('job-1')
    expect(store.renderJobs['10:3']).toMatchObject({ status: 'QUEUED' })
    await expect(store.fetchRenderStatus(10, 3)).resolves.toMatchObject({ status: 'COMPLETED' })
    expect(store.renderJobs['10:3']).toMatchObject({ status: 'COMPLETED', videoId: 44 })
    expect(store.renderAvailabilityLoading).toBe(false)
  })

  it('clears data instead of leaving a stale run list when no workspace is active', async () => {
    workspace.ensureActiveWorkspace.mockResolvedValue(null)
    const store = useUgcShortsPipelineStore()
    store.runs = [{ id: 99 }] as never

    await store.fetchRuns()
    expect(store.runs).toEqual([])
    expect(ugcShortsPipelineApi.list).not.toHaveBeenCalled()
    await expect(store.fetchDetail(99)).rejects.toThrow('활성 워크스페이스가 없습니다')
  })
})
