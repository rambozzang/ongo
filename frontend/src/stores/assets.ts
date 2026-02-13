import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Asset, AssetFolder, AssetFilter, AssetType } from '@/types/asset'
import { assetsApi } from '@/api/assets'

export const useAssetsStore = defineStore('assets', () => {
  // ---- State ----
  const assets = ref<Asset[]>([])
  const folders = ref<AssetFolder[]>([])
  const selectedFolderId = ref<number | null>(null)
  const viewMode = ref<'grid' | 'list'>('grid')
  const filter = ref<AssetFilter>({})
  const selectedAssets = ref<Set<number>>(new Set())
  const loading = ref(false)

  // ---- Load from API ----
  async function fetchAssets() {
    loading.value = true
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
        folderId: null,
        thumbnail: a.fileType === 'IMAGE' ? a.fileUrl : null,
        duration: a.durationSeconds,
        width: a.width,
        height: a.height,
        createdAt: a.createdAt ?? new Date().toISOString(),
      }))
    } catch {
      // API failed - start with empty list
      assets.value = []
    } finally {
      loading.value = false
    }
  }

  // Initialize by fetching from API
  fetchAssets()

  // ---- Getters ----
  const filteredAssets = computed<Asset[]>(() => {
    let result = assets.value

    if (selectedFolderId.value !== null) {
      result = result.filter((a) => a.folderId === selectedFolderId.value)
    }

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

  const currentFolder = computed<AssetFolder | null>(() => {
    if (selectedFolderId.value === null) return null
    return folders.value.find((f) => f.id === selectedFolderId.value) ?? null
  })

  const storageUsed = computed<number>(() => {
    return assets.value.reduce((sum, a) => sum + a.fileSize, 0)
  })

  const storageLimit = computed<number>(() => {
    return 10 * 1024 * 1024 * 1024
  })

  // ---- Actions ----
  async function uploadAsset(file: File, folderId: number | null, tags: string[]): Promise<Asset> {
    try {
      const response = await assetsApi.upload(file, folderId?.toString() ?? 'default', tags)
      const newAsset: Asset = {
        id: response.id,
        type: (response.fileType as AssetType) ?? getAssetTypeFromMime(file.type),
        name: response.originalFilename ?? file.name,
        fileUrl: response.fileUrl,
        fileSize: response.fileSizeBytes ?? file.size,
        mimeType: response.mimeType ?? file.type,
        tags: response.tags,
        folderId,
        thumbnail: response.fileType === 'IMAGE' ? response.fileUrl : null,
        duration: response.durationSeconds,
        width: response.width,
        height: response.height,
        createdAt: response.createdAt ?? new Date().toISOString(),
      }
      assets.value.push(newAsset)
      updateFolderCounts()
      return newAsset
    } catch {
      // Fallback to local creation
      const assetType = getAssetTypeFromMime(file.type)
      const newAsset: Asset = {
        id: Math.max(...assets.value.map((a) => a.id), 0) + 1,
        type: assetType,
        name: file.name,
        fileUrl: URL.createObjectURL(file),
        fileSize: file.size,
        mimeType: file.type,
        tags,
        folderId,
        thumbnail: assetType === 'IMAGE' ? URL.createObjectURL(file) : null,
        duration: null,
        width: null,
        height: null,
        createdAt: new Date().toISOString(),
      }
      assets.value.push(newAsset)
      updateFolderCounts()
      return newAsset
    }
  }

  async function deleteAsset(id: number) {
    try {
      await assetsApi.delete(id)
    } catch {
      // Continue with local delete
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
      updateFolderCounts()
    }
  }

  function moveToFolder(assetId: number, folderId: number | null) {
    const asset = assets.value.find((a) => a.id === assetId)
    if (asset) {
      asset.folderId = folderId
      updateFolderCounts()
    }
  }

  async function addTag(assetId: number, tag: string) {
    const asset = assets.value.find((a) => a.id === assetId)
    if (asset && !asset.tags.includes(tag)) {
      asset.tags.push(tag)
      try {
        await assetsApi.update(assetId, { tags: asset.tags })
      } catch {
        // Keep local state
      }
    }
  }

  async function removeTag(assetId: number, tag: string) {
    const asset = assets.value.find((a) => a.id === assetId)
    if (asset) {
      asset.tags = asset.tags.filter((t) => t !== tag)
      try {
        await assetsApi.update(assetId, { tags: asset.tags })
      } catch {
        // Keep local state
      }
    }
  }

  function createFolder(name: string, parentId: number | null = null): AssetFolder {
    const newFolder: AssetFolder = {
      id: Math.max(...folders.value.map((f) => f.id), 0) + 1,
      name,
      parentId,
      assetCount: 0,
      createdAt: new Date().toISOString(),
    }
    folders.value.push(newFolder)
    return newFolder
  }

  function renameFolder(id: number, name: string) {
    const folder = folders.value.find((f) => f.id === id)
    if (folder) {
      folder.name = name
    }
  }

  function deleteFolder(id: number) {
    assets.value.forEach((a) => {
      if (a.folderId === id) {
        a.folderId = null
      }
    })
    const index = folders.value.findIndex((f) => f.id === id)
    if (index !== -1) {
      folders.value.splice(index, 1)
    }
    if (selectedFolderId.value === id) {
      selectedFolderId.value = null
    }
    updateFolderCounts()
  }

  function bulkDelete(ids: number[]) {
    ids.forEach((id) => deleteAsset(id))
    selectedAssets.value = new Set()
  }

  function bulkMove(ids: number[], folderId: number | null) {
    ids.forEach((id) => moveToFolder(id, folderId))
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

  function updateFolderCounts() {
    folders.value.forEach((folder) => {
      folder.assetCount = assets.value.filter((a) => a.folderId === folder.id).length
    })
  }

  return {
    // State
    assets,
    folders,
    selectedFolderId,
    viewMode,
    filter,
    selectedAssets,
    loading,
    // Getters
    filteredAssets,
    currentFolder,
    storageUsed,
    storageLimit,
    // Actions
    fetchAssets,
    uploadAsset,
    deleteAsset,
    moveToFolder,
    addTag,
    removeTag,
    createFolder,
    renameFolder,
    deleteFolder,
    bulkDelete,
    bulkMove,
    toggleSelection,
    selectAll,
    clearSelection,
  }
})
