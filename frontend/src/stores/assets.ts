import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { Asset, AssetFilter, AssetType } from '@/types/asset'
import { assetsApi } from '@/api/assets'
import { subscriptionApi } from '@/api/subscription'

export const useAssetsStore = defineStore('assets', () => {
  // ---- State ----
  const assets = ref<Asset[]>([])
  const viewMode = ref<'grid' | 'list'>('grid')
  const filter = ref<AssetFilter>({})
  const selectedAssets = ref<Set<number>>(new Set())
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  /*
   * 서버 페이지네이션. 예전에는 `page 0, size 100` 으로 한 번만 읽고 끝냈다 — 101 번째
   * 에셋부터는 화면에 아예 나오지 않아 재사용(콘텐츠 만들기)도 삭제도 할 수 없었다.
   * 다른 목록 화면과 같은 패턴을 쓴다(activityLogs·channelAudit 등).
   */
  const page = ref(0)
  const pageSize = ref(24)
  const totalCount = ref(0)
  // The server is the source of truth: the list endpoint is paginated and cannot
  // safely be used to infer total storage usage.
  const storageUsed = ref<number | null>(null)
  const storageLimit = ref<number | null>(null)
  const storageUsageLoading = ref(false)
  const storageUsageError = ref<string | null>(null)

  // ---- Load from API ----
  async function fetchAssets() {
    loading.value = true
    loadError.value = null
    try {
      /*
       * **거르기는 서버가 한다.** 예전에는 받아 온 목록을 화면에서 다시 걸렀는데, 그러면
       * 검색이 "이 페이지에 없다"와 "라이브러리에 없다"를 구분하지 못하고 총계도 조건을
       * 반영하지 못한다.
       */
      const params = {
        page: page.value,
        size: pageSize.value,
        ...(filter.value.type ? { fileType: filter.value.type } : {}),
        ...(filter.value.search ? { search: filter.value.search } : {}),
        ...(filter.value.tags?.length ? { tag: filter.value.tags[0] } : {}),
      }
      const response = await assetsApi.list(params)
      totalCount.value = response.totalCount ?? 0
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
  void fetchStorageUsage()

  // ---- Getters ----
  /**
   * 화면에 그릴 목록.
   *
   * **여기서 다시 거르지 않는다.** 조건은 서버가 이미 적용했고, 한 번 더 거르면 총계와
   * 화면 건수가 어긋난다. 정렬만 서버와 같은 기준(최신순)으로 고정한다.
   */
  const filteredAssets = computed<Asset[]>(() =>
    [...assets.value].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
  )

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  function nextPage() {
    if (hasNextPage.value) {
      page.value++
      void fetchAssets()
    }
  }

  function prevPage() {
    if (hasPrevPage.value) {
      page.value--
      void fetchAssets()
    }
  }

  /**
   * 목록을 바꾸는 작업 뒤에 **서버와 다시 맞춘다.**
   *
   * 페이지네이션이 붙은 뒤로 화면의 배열만 고치는 것은 더 이상 안전하지 않다. 한 페이지는
   * 서버가 `created_at DESC` 로 자른 창이라, 여기서 한 건을 넣거나 빼면 그 창의 경계가
   * 어긋난다 — 지운 자리만큼 다음 페이지의 첫 건이 앞으로 당겨져 **같은 에셋이 두 페이지에
   * 나오거나** 아예 건너뛴다. `totalCount` 도 함께 틀어져 "다음" 버튼이 잘못 열리고 닫힌다.
   *
   * 그래서 서버가 준 페이지로 통째로 갈아 끼운다.
   */
  async function refreshCurrentPage() {
    await fetchAssets()
    /*
     * 마지막 페이지의 마지막 건을 지우면 그 페이지는 비어 버린다. 그대로 두면 "에셋이
     * 없습니다" 가 뜨고 사용자는 라이브러리가 비었다고 읽는다 — 실제로는 앞 페이지에 있다.
     *
     * 조회가 실패했을 때는 옮기지 않는다. 그때 `assets` 는 마지막으로 확인된 목록을 그대로
     * 들고 있고(빈 배열이 아니다), 실패를 근거로 페이지를 옮기면 오류가 이동으로 둔갑한다.
     */
    if (loadError.value === null && page.value > 0 && assets.value.length === 0) {
      page.value = Math.max(0, Math.min(page.value - 1, totalPages.value - 1))
      await fetchAssets()
    }
  }

  /**
   * 조건이 바뀌면 **첫 페이지로 되돌린다.**
   *
   * 3 페이지를 보다가 조건을 좁히면 결과가 한 페이지뿐일 수 있다. 페이지를 그대로 두면
   * 빈 화면이 나오고, 사용자는 "검색 결과가 없다"로 읽는다 — 실제로는 있는데 3 페이지에
   * 없을 뿐이다.
   *
   * 화면이 `filter` 에 새 객체를 대입하므로 참조 변경만으로 걸린다.
   */
  watch(filter, () => {
    page.value = 0
    void fetchAssets()
  })

  async function fetchStorageUsage() {
    storageUsageLoading.value = true
    storageUsageError.value = null
    try {
      const usage = await subscriptionApi.getUsage()
      storageUsed.value = Math.max(0, usage.storageUsedMb) * 1024 * 1024
      storageLimit.value = usage.storageLimitBytes > 0 ? usage.storageLimitBytes : null
    } catch (error) {
      // Never turn an unavailable measurement into 0% or a guessed plan size.
      storageUsed.value = null
      storageLimit.value = null
      storageUsageError.value = error instanceof Error ? error.message : '저장공간 사용량을 불러오지 못했습니다.'
    } finally {
      storageUsageLoading.value = false
    }
  }

  // ---- Actions ----
  async function uploadAsset(file: File, tags: string[]): Promise<Asset> {
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
    /*
     * **배열에 밀어 넣지 않는다.** 그러면 24개짜리 페이지가 25개가 되고, 그 한 칸만큼
     * 다음 페이지가 밀려 같은 에셋이 두 번 보인다. `totalCount` 도 그대로라 마지막
     * 페이지에 닿지 못한다.
     *
     * 새 에셋은 최신순 정렬의 맨 앞이므로 **첫 페이지**에 있다. 3페이지를 보던 중이었다면
     * 거기서는 방금 올린 파일이 보이지 않으므로 첫 페이지로 옮겨 놓고 다시 읽는다.
     *
     * 여러 장을 올리면 장마다 한 번씩 다시 읽는다. 진행률도 장마다 갱신되므로 사용자가
     * 보는 목록과 총계가 매 순간 서버와 같다.
     */
    page.value = 0
    await fetchAssets()
    await fetchStorageUsage()
    return newAsset
  }

  /**
   * 서버에서 지우고 **화면에서만** 걷어낸다. 서버 재조회와 사용량 갱신은 하지 않는다 —
   * 여러 건을 지울 때 건마다 왕복하지 않도록 호출자가 마지막에 한 번만 한다.
   */
  async function deleteOne(id: number) {
    await assetsApi.delete(id)
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
    }
    // 지운 항목이 선택에 남아 있으면 "3개 선택됨" 이 실제와 달라진다.
    selectedAssets.value.delete(id)
  }

  async function deleteAsset(id: number) {
    await deleteOne(id)
    await refreshCurrentPage()
    await fetchStorageUsage()
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

  /**
   * 여러 건을 지운다. **재조회와 사용량 갱신은 마지막에 한 번뿐이다.**
   *
   * 예전에는 [deleteAsset] 을 건마다 병렬로 불러 사용량 조회가 N 번 나갔고, 응답 순서가
   * 보장되지 않아 **먼저 보낸(더 오래된) 측정치가 마지막에 도착해** 남는 일이 있었다.
   * 삭제 자체는 서로 독립이라 병렬로 두고, 서버와 맞추는 일만 한 번으로 모은다.
   */
  async function bulkDelete(ids: number[]) {
    /*
     * **일부가 거절돼도 서버와 다시 맞춘다.**
     *
     * 브랜드 키트가 쓰고 있는 에셋은 서버가 거절한다(`ASSET_IN_USE`). 그때
     * `Promise.all` 이 곧바로 실패하는데, 이미 지워진 건들이 있으므로 여기서 멈추면
     * 총계와 페이지가 어긋난 채 남는다. 그래서 성공·실패와 무관하게 다시 읽는다.
     *
     * 선택 상태는 **통째로 비우지 않는다.** [deleteOne] 이 지운 건만 빼므로, 남은 선택은
     * 정확히 "지우지 못한 것들"이 된다 — 사용자가 무엇을 처리해야 하는지 그대로 보인다.
     * 전부 성공하면 자연히 비어 있다.
     *
     * 거절 사유는 그대로 올려 보낸다. 화면이 서버 문장(어느 브랜드 키트인지)을 보여 준다.
     */
    try {
      await Promise.all(ids.map((id) => deleteOne(id)))
    } finally {
      await refreshCurrentPage()
      await fetchStorageUsage()
    }
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
    page,
    pageSize,
    totalCount,
    storageUsageLoading,
    storageUsageError,
    // Getters
    filteredAssets,
    totalPages,
    hasNextPage,
    hasPrevPage,
    nextPage,
    prevPage,
    storageUsed,
    storageLimit,
    // Actions
    fetchAssets,
    refreshCurrentPage,
    fetchStorageUsage,
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
