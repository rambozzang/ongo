import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from './workspace'
import { workspaceApi } from '@/api/workspace'

vi.mock('@/api/workspace', () => ({ workspaceApi: {
  list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(),
} }))

describe('workspace store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    const values = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
      clear: () => values.clear(),
    })
    vi.clearAllMocks()
  })

  it('selects the first server workspace, persists switching, and falls back after removal', async () => {
    vi.mocked(workspaceApi.list).mockResolvedValue([
      { id: 3, name: '작업공간', slug: 'work', ownerId: 1 },
      { id: 4, name: '두번째', slug: 'second', ownerId: 1 },
    ] as never)
    const store = useWorkspaceStore()
    await store.fetchWorkspaces()
    expect(store.activeWorkspace?.id).toBe(3)
    store.switchWorkspace(4)
    expect(store.activeWorkspaceId).toBe(4)
    vi.mocked(workspaceApi.remove).mockResolvedValue(undefined)
    await store.removeWorkspace(4)
    expect(store.activeWorkspaceId).toBe(3)
    expect(store.workspaces.map((workspace) => workspace.id)).toEqual([3])
  })

  it('preserves the last confirmed workspace and exposes a retryable load error', async () => {
    vi.mocked(workspaceApi.list).mockResolvedValueOnce([
      { id: 3, name: '작업공간', slug: 'work', ownerId: 1 },
    ] as never)
    const store = useWorkspaceStore()
    await store.fetchWorkspaces()

    vi.mocked(workspaceApi.list).mockRejectedValueOnce(new Error('network'))
    await store.fetchWorkspaces(true)

    expect(store.loadError).toBe(true)
    expect(store.activeWorkspace?.id).toBe(3)
    expect(store.workspaces).toHaveLength(1)
  })
})
