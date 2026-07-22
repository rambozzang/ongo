import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  ugcCampaignApi,
  type CampaignResponse,
  type CampaignDetailResponse,
  type CreateCampaignRequest,
  type UpdateCampaignRequest,
  type UpsertPlaybookRequest,
} from '@/api/ugcCampaign'
import { useWorkspaceStore } from '@/stores/workspace'

export const useUgcCampaignStore = defineStore('ugcCampaign', () => {
  const workspaceStore = useWorkspaceStore()

  const campaigns = ref<CampaignResponse[]>([])
  const totalElements = ref(0)
  const page = ref(0)
  const size = ref(20)
  const statusFilter = ref<string | null>(null)
  const query = ref('')
  const loading = ref(false)
  const current = ref<CampaignDetailResponse | null>(null)

  const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / size.value)))
  const hasNextPage = computed(() => page.value < totalPages.value - 1)
  const hasPrevPage = computed(() => page.value > 0)

  function requireWorkspaceId(): number {
    const id = workspaceStore.activeWorkspaceId
    if (id == null) {
      throw new Error('활성 워크스페이스가 없습니다. 먼저 워크스페이스를 선택하세요.')
    }
    return id
  }

  async function fetchCampaigns() {
    loading.value = true
    try {
      const res = await ugcCampaignApi.list(requireWorkspaceId(), {
        page: page.value,
        size: size.value,
        status: statusFilter.value ?? undefined,
        query: query.value.trim() || undefined,
      })
      campaigns.value = res.items
      totalElements.value = res.totalElements
    } finally {
      loading.value = false
    }
  }

  function nextPage() {
    if (!hasNextPage.value) return Promise.resolve()
    page.value += 1
    return fetchCampaigns()
  }

  function prevPage() {
    if (!hasPrevPage.value) return Promise.resolve()
    page.value -= 1
    return fetchCampaigns()
  }

  function setStatusFilter(status: string | null) {
    statusFilter.value = status
    page.value = 0
    return fetchCampaigns()
  }

  function setQuery(q: string) {
    query.value = q
    page.value = 0
    return fetchCampaigns()
  }

  async function fetchCampaign(id: number) {
    current.value = await ugcCampaignApi.get(requireWorkspaceId(), id)
    return current.value
  }

  function createCampaign(request: CreateCampaignRequest) {
    return ugcCampaignApi.create(requireWorkspaceId(), request)
  }

  async function updateCampaign(id: number, request: UpdateCampaignRequest) {
    const result = await ugcCampaignApi.update(requireWorkspaceId(), id, request)
    current.value = result
    return result
  }

  async function publish(id: number) {
    const result = await ugcCampaignApi.publish(requireWorkspaceId(), id)
    current.value = result
    return result
  }

  async function pause(id: number) {
    const result = await ugcCampaignApi.pause(requireWorkspaceId(), id)
    current.value = result
    return result
  }

  async function complete(id: number) {
    const result = await ugcCampaignApi.complete(requireWorkspaceId(), id)
    current.value = result
    return result
  }

  function upsertPlaybook(id: number, request: UpsertPlaybookRequest) {
    return ugcCampaignApi.upsertPlaybook(requireWorkspaceId(), id, request)
  }

  return {
    campaigns,
    totalElements,
    page,
    size,
    statusFilter,
    query,
    loading,
    current,
    totalPages,
    hasNextPage,
    hasPrevPage,
    fetchCampaigns,
    nextPage,
    prevPage,
    setStatusFilter,
    setQuery,
    fetchCampaign,
    createCampaign,
    updateCampaign,
    publish,
    pause,
    complete,
    upsertPlaybook,
  }
})
