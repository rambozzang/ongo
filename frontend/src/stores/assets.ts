import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Asset, AssetFilter, AssetType } from '@/types/asset'
import { assetsApi } from '@/api/assets'

export const useAssetsStore = defineStore('assets', () => {
  // ---- State ----
  const assets = ref<Asset[]>([])
  const viewMode = ref<'grid' | 'list'>('grid')
  const filter = ref<AssetFilter>({})
  const selectedAssets = ref<Set<number>>(new Set())
  const loading = ref(false)
  const loadError = ref<string | null>(null)

  // ---- Load from API ----
  async function fetchAssets() {
    loading.value = true
    loadError.value = null
    try {
      const params: Record<string, string | number> = { page: 0, size: 100 }
      if (filter.value.type) params.fileType = filter.value.type
      const response = await assetsApi.list(params as Parameters<typeof assetsApi.list>[0])
      assets.value = response.assets.map((a) => ({
        id: a.id,
        type: (a.fileType as AssetType) ?? 'TEMPLATE',
        name: a.originalFilename ?? a.filename,
        fileUrl: a.fileUrl,
        fileSize: a.fileSizeBytes ?? 0,
        mimeType: a.mimeType ?? 'application/octet-stream',
        tags: a.tags,
        thumbnail: a.fileType === 'IMAGE' ? a.fileUrl : null,
        duration: a.durationSeconds,
        width: a.width,
        height: a.height,
        createdAt: a.createdAt ?? new Date().toISOString(),
      }))
    } catch (error) {
      // A failed request is not an empty asset library. Preserve the last
      // confirmed list so users can retry without losing their context.
      loadError.value = error instanceof Error ? error.message : '에셋을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  // Initialize by fetching from API
  fetchAssets()

  // ---- Getters ----
  const filteredAssets = computed<Asset[]>(() => {
    let result = assets.value

    if (filter.value.type) {
      result = result.filter((a) => a.type === filter.value.type)
    }

    if (filter.value.tags && filter.value.tags.length > 0) {
      const filterTags = filter.value.tags
      result = result.filter((a) => filterTags.some((t) => a.tags.includes(t)))
    }

    if (filter.value.search) {
      const search = filter.value.search.toLowerCase()
      result = result.filter(
        (a) =>
          a.name.toLowerCase().includes(search) ||
          a.tags.some((t) => t.toLowerCase().includes(search)),
      )
    }

    if (filter.value.dateRange) {
      const start = new Date(filter.value.dateRange.startDate).getTime()
      const end = new Date(filter.value.dateRange.endDate).getTime()
      result = result.filter((a) => {
        const created = new Date(a.createdAt).getTime()
        return created >= start && created <= end
      })
    }

    return result.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
  })

  const storageUsed = computed<number>(() => {
    return assets.value.reduce((sum, a) => sum + a.fileSize, 0)
  })

  const storageLimit = computed<number>(() => {
    return 10 * 1024 * 1024 * 1024
  })

  // ---- Actions ----
  async function uploadAsset(file: File, tags: string[]): Promise<Asset> {
    try {
      const response = await assetsApi.upload(file, 'default', tags)
      const newAsset: Asset = {
        id: response.id,
        type: (response.fileType as AssetType) ?? getAssetTypeFromMime(file.type),
        name: response.originalFilename ?? file.name,
        fileUrl: response.fileUrl,
        fileSize: response.fileSizeBytes ?? file.size,
        mimeType: response.mimeType ?? file.type,
        tags: response.tags,
        thumbnail: response.fileType === 'IMAGE' ? response.fileUrl : null,
        duration: response.durationSeconds,
        width: response.width,
        height: response.height,
        createdAt: response.createdAt ?? new Date().toISOString(),
      }
      assets.value.push(newAsset)
      return newAsset
    } catch (error) {
      throw error
    }
  }

  async function deleteAsset(id: number) {
    try {
      await assetsApi.delete(id)
    } catch (error) {
      throw error
    }
    const index = assets.value.findIndex((a) => a.id === id)
    if (index !== -1) {
      const asset = assets.value[index]
      if (asset.fileUrl?.startsWith('blob:')) {
        URL.revokeObjectURL(asset.fileUrl)
      }
      if (asset.thumbnail?.startsWith('blob:')) {
        URL.revokeObjectURL(asset.thumbnail)
      }
      assets.value.splice(index, 1)
      selectedAssets.value.delete(id)
    }
  }

  async function addTag(assetId: number, tag: string) {
    const asset = assets.value.find((a) => a.id === assetId)
    if (asset && !asset.tags.includes(tag)) {
      const previousTags = [...asset.tags]
      asset.tags.push(tag)
      try {
        await assetsApi.update(assetId, { tags: asset.tags })
      } catch (error) {
        asset.tags = previousTags
        throw error
      }
    }
  }

  async function removeTag(assetId: number, tag: string) {
    const asset = assets.value.find((a) => a.id === assetId)
    if (asset) {
      const previousTags = [...asset.tags]
      asset.tags = asset.tags.filter((t) => t !== tag)
      try {
        await assetsApi.update(assetId, { tags: asset.tags })
      } catch (error) {
        asset.tags = previousTags
        throw error
      }
    }
  }


  async function bulkDelete(ids: number[]) {
    await Promise.all(ids.map((id) => deleteAsset(id)))
    selectedAssets.value = new Set()
  }

  function toggleSelection(id: number) {
    const next = new Set(selectedAssets.value)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    selectedAssets.value = next
  }

  function selectAll(ids: number[]) {
    selectedAssets.value = new Set(ids)
  }

  function clearSelection() {
    selectedAssets.value = new Set()
  }

  // ---- Helpers ----
  function getAssetTypeFromMime(mime: string): AssetType {
    if (mime.startsWith('video/')) return 'VIDEO'
    if (mime.startsWith('image/')) return 'IMAGE'
    if (mime.startsWith('audio/')) return 'AUDIO'
    return 'TEMPLATE'
  }

  return {
    // State
    assets,
    viewMode,
    filter,
    selectedAssets,
    loadError,
    loading,
    // Getters
    filteredAssets,
    storageUsed,
    storageLimit,
    // Actions
    fetchAssets,
    uploadAsset,
    deleteAsset,
    addTag,
    removeTag,
    bulkDelete,
    toggleSelection,
    selectAll,
    clearSelection,
  }
})
