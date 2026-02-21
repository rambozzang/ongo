import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Workspace, CreateWorkspaceRequest, UpdateWorkspaceRequest } from '@/types/workspace'
import { workspaceApi } from '@/api/workspace'

const STORAGE_KEY = 'ongo_active_workspace_id'

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const activeWorkspaceId = ref<number | null>(null)
  const loading = ref(false)

  const activeWorkspace = computed(() =>
    workspaces.value.find(w => w.id === activeWorkspaceId.value) ?? workspaces.value[0] ?? null
  )

  async function fetchWorkspaces() {
    loading.value = true
    try {
      workspaces.value = await workspaceApi.list()
      const savedId = localStorage.getItem(STORAGE_KEY)
      if (savedId && workspaces.value.some(w => w.id === Number(savedId))) {
        activeWorkspaceId.value = Number(savedId)
      } else if (workspaces.value.length > 0) {
        activeWorkspaceId.value = workspaces.value[0].id
      }
    } catch {
      workspaces.value = []
    } finally {
      loading.value = false
    }
  }

  function switchWorkspace(id: number) {
    activeWorkspaceId.value = id
    localStorage.setItem(STORAGE_KEY, String(id))
  }

  async function createWorkspace(request: CreateWorkspaceRequest) {
    const workspace = await workspaceApi.create(request)
    workspaces.value.push(workspace)
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
    if (activeWorkspaceId.value === id && workspaces.value.length > 0) {
      switchWorkspace(workspaces.value[0].id)
    }
  }

  return {
    workspaces,
    activeWorkspaceId,
    activeWorkspace,
    loading,
    fetchWorkspaces,
    switchWorkspace,
    createWorkspace,
    updateWorkspace,
    removeWorkspace,
  }
})
