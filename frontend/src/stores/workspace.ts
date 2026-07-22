import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '@/types/workspace'
import { workspaceApi } from '@/api/workspace'

const STORAGE_KEY = 'ongo_active_workspace_id'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const activeWorkspaceId = ref<number | null>(null)
  const loading = ref(false)
  let fetchPromise: Promise<Workspace[]> | null = null

  const activeWorkspace = computed(() =>
    workspaces.value.find(w => w.id === activeWorkspaceId.value) ?? workspaces.value[0] ?? null
  )

  async function fetchWorkspaces(force = false): Promise<Workspace[]> {
    if (!force && fetchPromise) return fetchPromise
    if (!force && workspaces.value.length > 0 && activeWorkspaceId.value != null) {
      return workspaces.value
    }

    loading.value = true
    fetchPromise = workspaceApi.list()
      .then((items) => {
        workspaces.value = items
        const savedId = localStorage.getItem(STORAGE_KEY)
        if (savedId && items.some(w => w.id === Number(savedId))) {
          activeWorkspaceId.value = Number(savedId)
        } else if (items.length > 0) {
          activeWorkspaceId.value = items[0].id
          localStorage.setItem(STORAGE_KEY, String(items[0].id))
        } else {
          activeWorkspaceId.value = null
          localStorage.removeItem(STORAGE_KEY)
        }
        return items
      })
      .catch(() => {
        workspaces.value = []
        activeWorkspaceId.value = null
        return []
      })
      .finally(() => {
        loading.value = false
        fetchPromise = null
      })

    return fetchPromise
  }

  async function ensureActiveWorkspace(): Promise<number | null> {
    if (activeWorkspaceId.value != null && workspaces.value.some(w => w.id === activeWorkspaceId.value)) {
      return activeWorkspaceId.value
    }

    const items = await fetchWorkspaces()
    return activeWorkspaceId.value ?? items[0]?.id ?? null
  }

  function switchWorkspace(id: number) {
    activeWorkspaceId.value = id
    localStorage.setItem(STORAGE_KEY, String(id))
  }

  async function createWorkspace(request: CreateWorkspaceRequest) {
    const workspace = await workspaceApi.create(request)
    workspaces.value.push(workspace)
    if (activeWorkspaceId.value == null) switchWorkspace(workspace.id)
    return workspace
  }

  async function updateWorkspace(id: number, request: UpdateWorkspaceRequest) {
    const workspace = await workspaceApi.update(id, request)
    const idx = workspaces.value.findIndex(w => w.id === id)
    if (idx !== -1) workspaces.value[idx] = workspace
    return workspace
  }

  async function removeWorkspace(id: number) {
    await workspaceApi.remove(id)
    workspaces.value = workspaces.value.filter(w => w.id !== id)
    if (activeWorkspaceId.value === id) {
      if (workspaces.value.length > 0) switchWorkspace(workspaces.value[0].id)
      else {
        activeWorkspaceId.value = null
        localStorage.removeItem(STORAGE_KEY)
      }
    }
  }

  return {
    workspaces,
    activeWorkspaceId,
    activeWorkspace,
    loading,
    fetchWorkspaces,
    ensureActiveWorkspace,
    switchWorkspace,
    createWorkspace,
    updateWorkspace,
    removeWorkspace,
  }
})
