<script setup lang="ts">
import { ref, shallowRef, computed, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAssetsStore } from '@/stores/assets'
import type { Asset, AssetType } from '@/types/asset'
import AssetCard from '@/components/assets/AssetCard.vue'
import AssetUploadModal from '@/components/assets/AssetUploadModal.vue'
import AssetPreviewModal from '@/components/assets/AssetPreviewModal.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useLocale } from '@/composables/useLocale'
import { useRouter } from 'vue-router'
import { useNotification } from '@/composables/useNotification'
import { videoApi } from '@/api/video'
import SectionCard from '@/components/redesign/SectionCard.vue'
import {
  PlusIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  Squares2X2Icon,
  ListBulletIcon,
  TrashIcon,
  ArchiveBoxXMarkIcon,
  CloudArrowUpIcon,
} from '@heroicons/vue/24/outline'

const assetsStore = useAssetsStore()
const router = useRouter()
const notify = useNotification()
const { t } = useLocale()
const {
  filteredAssets,
  viewMode,
  selectedAssets,
  filter: _filter,
  storageUsed,
  storageLimit,
  storageUsageLoading,
  storageUsageError,
  loadError,
  page,
  pageSize,
  totalCount,
  totalPages,
  hasNextPage,
  hasPrevPage,
} = storeToRefs(assetsStore)

// Search
const searchKeyword = ref('')
let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    assetsStore.filter = { ...assetsStore.filter, search: searchKeyword.value || undefined }
  }, 300)
}

function clearSearch() {
  searchKeyword.value = ''
  assetsStore.filter = { ...assetsStore.filter, search: undefined }
}

// Type filter
const activeTypeFilter = ref<AssetType | undefined>(undefined)

const typeOptions: { label: string; value: AssetType | undefined }[] = [
  { label: '전체', value: undefined },
  { label: '영상', value: 'VIDEO' },
  { label: '이미지', value: 'IMAGE' },
  { label: '오디오', value: 'AUDIO' },
  { label: '템플릿', value: 'TEMPLATE' },
]

function onTypeFilter(value: AssetType | undefined) {
  activeTypeFilter.value = value
  assetsStore.filter = { ...assetsStore.filter, type: value }
}

// Tag filter
const allTags = computed<string[]>(() => {
  const tagSet = new Set<string>()
  assetsStore.assets.forEach((a) => a.tags.forEach((t) => tagSet.add(t)))
  return Array.from(tagSet).sort()
})

const activeTagFilter = ref<string | undefined>(undefined)

function onTagFilter(tag: string | undefined) {
  activeTagFilter.value = tag
  assetsStore.filter = { ...assetsStore.filter, tags: tag ? [tag] : undefined }
}

// Modals
const showUploadModal = ref(false)
const showPreviewModal = ref(false)
const previewAsset = ref<Asset | null>(null)
function openPreview(asset: Asset) {
  previewAsset.value = asset
  showPreviewModal.value = true
}

// 삭제 확인 모달 — 단건 삭제 / 일괄 삭제가 하나의 모달을 공유
const showConfirmModal = ref(false)
const confirmTitle = ref('')
const confirmMessage = ref('')
const pendingAction = shallowRef<(() => void) | null>(null)

function askConfirm(title: string, message: string, action: () => void) {
  confirmTitle.value = title
  confirmMessage.value = message
  pendingAction.value = action
  showConfirmModal.value = true
}

function runPendingAction() {
  const action = pendingAction.value
  pendingAction.value = null
  action?.()
}

/**
 * 삭제 결과를 사용자에게 알린다.
 *
 * 서버는 지울 수 없는 이유를 문장으로 내려 준다 — 브랜드 키트가 쓰고 있으면
 * `ASSET_IN_USE` 와 함께 **어느 키트인지**까지 담아 온다(`AssetUseCase.AssetInUseException`).
 * `client.ts` 가 그 문장을 `error.message` 로 올려 주므로 그대로 보여 주면 된다.
 *
 * 예전에는 `assetsStore.deleteAsset(id)` 를 `await` 없이 불렀다. 거절되면 처리되지 않은
 * 프라미스가 되고, 화면에는 **아무것도 남지 않았다** — 모달은 닫히고 에셋은 그대로라
 * 사용자는 눌렀는지조차 알 수 없었다.
 */
async function runDelete(remove: () => Promise<unknown>, fallbackMessage: string) {
  try {
    await remove()
  } catch (error) {
    notify.error(error instanceof Error ? error.message : fallbackMessage)
  }
}

// Single delete
function handleDelete(id: number) {
  askConfirm(t('assets.deleteTitle'), t('assets.deleteMessage'), () => {
    void runDelete(() => assetsStore.deleteAsset(id), '에셋을 삭제하지 못했습니다.')
  })
}

// Bulk operations
const hasSelection = computed(() => selectedAssets.value.size > 0)
const selectedCount = computed(() => selectedAssets.value.size)

function isSelected(id: number): boolean {
  return selectedAssets.value.has(id)
}

const isAllSelected = computed(() => {
  if (filteredAssets.value.length === 0) return false
  return filteredAssets.value.every((a) => selectedAssets.value.has(a.id))
})

function toggleSelectAll() {
  if (isAllSelected.value) {
    assetsStore.clearSelection()
  } else {
    assetsStore.selectAll(filteredAssets.value.map((a) => a.id))
  }
}

function handleBulkDelete() {
  const count = selectedAssets.value.size
  const ids = [...selectedAssets.value]
  askConfirm(t('assets.bulkDeleteTitle'), t('assets.bulkDeleteMessage', { count }), () => {
    void runDelete(() => assetsStore.bulkDelete(ids), '에셋을 삭제하지 못했습니다.')
  })
}

/**
 * 라이브러리의 영상 에셋으로 콘텐츠 초안을 만들고 작성 화면으로 넘긴다.
 *
 * 서버가 오브젝트를 새 영상 전용 경로로 복사해 DRAFT 행을 만들어 준다. 여기서 로컬 File 을
 * 지어내 업로드 흐름을 흉내내지 않는다 — 이미 스토리지에 있는 파일을 한 번 더 올리는
 * 일이고, 사용자 회선과 쿼터를 두 번 쓴다.
 *
 * 실패하면 **이동하지 않는다.** 초안이 없는데 작성 화면으로 보내면 빈 화면에서 원인을
 * 알 수 없다.
 */
const promoting = ref<number | null>(null)
async function handleUseAsContent(assetId: number) {
  if (promoting.value !== null) return
  promoting.value = assetId
  try {
    const { videoId } = await videoApi.createFromAsset(assetId)
    await router.push({ path: '/compose', query: { videoId: String(videoId) } })
  } catch (error) {
    notify.error(error instanceof Error ? error.message : '콘텐츠를 만들지 못했습니다.')
  } finally {
    promoting.value = null
  }
}

// Storage
const storagePercentage = computed(() => {
  if (storageUsed.value == null || storageLimit.value == null || storageLimit.value <= 0) return null
  return Math.min(100, Math.round((storageUsed.value / storageLimit.value) * 100))
})

function formatStorage(bytes: number): string {
  if (bytes >= 1_073_741_824) return (bytes / 1_073_741_824).toFixed(1) + ' GB'
  if (bytes >= 1_048_576) return (bytes / 1_048_576).toFixed(1) + ' MB'
  return (bytes / 1024).toFixed(1) + ' KB'
}

function onUploaded() {
  // Could show a toast notification here
}

// Has active filters
const hasActiveFilters = computed(() => {
  return (
    activeTypeFilter.value !== undefined ||
    activeTagFilter.value !== undefined ||
    searchKeyword.value !== ''
  )
})

function clearFilters() {
  searchKeyword.value = ''
  activeTypeFilter.value = undefined
  activeTagFilter.value = undefined
  assetsStore.filter = {}
}

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout)
})
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-h1 font-bold text-gray-900 dark:text-gray-100">
          에셋 라이브러리
        </h1>
        <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
          영상, 이미지, 오디오, 템플릿을 한곳에서 관리하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Storage Usage -->
        <div v-if="storageUsed != null && storageLimit != null" class="hidden items-center gap-2 sm:flex">
          <div class="w-32">
            <div class="mb-0.5 flex items-center justify-between text-body-xs text-gray-500 dark:text-gray-400">
              <span>저장 공간</span>
              <span>{{ storagePercentage }}%</span>
            </div>
            <div class="h-1.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full transition-all"
                :class="
                  storagePercentage != null && storagePercentage > 90
                    ? 'bg-error'
                    : storagePercentage != null && storagePercentage > 70
                      ? 'bg-warning'
                      : 'bg-primary-600'
                "
                :style="{ width: (storagePercentage ?? 0) + '%' }"
              />
            </div>
            <p class="mt-0.5 text-[10px] text-gray-400 dark:text-gray-500">
              {{ formatStorage(storageUsed) }} / {{ formatStorage(storageLimit) }}
            </p>
          </div>
        </div>
        <span v-else-if="storageUsageLoading" class="hidden text-body-xs text-content-tertiary sm:inline">
          저장공간 확인 중…
        </span>
        <span v-else-if="storageUsageError" class="hidden text-body-xs text-warning-strong sm:inline" role="status">
          저장공간을 확인할 수 없습니다
        </span>

        <!-- Upload Button -->
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showUploadModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          에셋 업로드
        </button>
      </div>
    </div>

    <PageGuide
title="에셋 라이브러리" :items="[
      '업로드 버튼으로 영상·이미지·오디오·템플릿 파일을 라이브러리에 저장하세요',
      '그리드/리스트 뷰를 전환하여 에셋을 원하는 방식으로 확인하세요',
      '유형 필터(영상/이미지/오디오/템플릿)와 태그 필터, 검색을 조합하여 원하는 에셋을 빠르게 찾으세요',
      '여러 에셋을 선택하여 일괄 삭제하고, 상단의 스토리지 사용량 바에서 잔여 공간을 확인하세요',
      '에셋을 클릭하면 미리보기 모달에서 상세 정보를 확인하고 영상 업로드에 바로 활용할 수 있습니다',
    ]" />

    <!-- Toolbar -->
    <SectionCard :title="t('assets.title')" class="mb-6">
      <div class="flex flex-col gap-3 desktop:flex-row desktop:items-center desktop:justify-between">
        <!-- Search -->
        <div class="relative flex-1 desktop:max-w-sm">
          <MagnifyingGlassIcon
            class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
          />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="파일명 또는 태그 검색..."
            class="input-field pl-10 pr-9"
            @input="onSearchInput"
          />
          <button
            v-if="searchKeyword"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            @click="clearSearch"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <!-- Type Filter -->
          <div class="flex rounded-lg border border-line-control bg-surface-input">
            <button
              v-for="opt in typeOptions"
              :key="opt.value ?? 'all'"
              class="px-3 py-1.5 text-caption first:rounded-l-lg last:rounded-r-lg transition-colors"
              :class="
                activeTypeFilter === opt.value
                  ? 'bg-accent-dim text-accent'
                  : 'text-content-tertiary hover:bg-surface-raised hover:text-content'
              "
              @click="onTypeFilter(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>

          <!-- Tag Filter (dropdown) -->
          <select
            :value="activeTagFilter ?? ''"
            class="input-field w-auto text-body-xs"
            @change="onTagFilter(($event.target as HTMLSelectElement).value || undefined)"
          >
            <option value="">태그 전체</option>
            <option v-for="tag in allTags" :key="tag" :value="tag">{{ tag }}</option>
          </select>

          <!-- Clear Filters -->
          <button
            v-if="hasActiveFilters"
            class="inline-flex items-center gap-1 text-body-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
            @click="clearFilters"
          >
            <XMarkIcon class="h-3.5 w-3.5" />
            필터 초기화
          </button>

          <!-- View Mode Toggle -->
          <div class="ml-auto flex rounded-lg border border-line-control bg-surface-input">
            <button
              class="rounded-l-lg px-3 py-1.5 text-body transition-colors"
              :class="
                viewMode === 'grid'
                  ? 'bg-accent-dim text-accent'
                  : 'text-content-tertiary hover:bg-surface-raised hover:text-content'
              "
              title="그리드 보기"
              @click="assetsStore.viewMode = 'grid'"
            >
              <Squares2X2Icon class="h-5 w-5" />
            </button>
            <button
              class="rounded-r-lg px-3 py-1.5 text-body transition-colors"
              :class="
                viewMode === 'list'
                  ? 'bg-accent-dim text-accent'
                  : 'text-content-tertiary hover:bg-surface-raised hover:text-content'
              "
              title="리스트 보기"
              @click="assetsStore.viewMode = 'list'"
            >
              <ListBulletIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </SectionCard>

    <!-- Main Content -->
    <div v-if="loadError" class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ loadError }}</span>
      <button type="button" class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" :disabled="assetsStore.loading" @click="assetsStore.fetchAssets()">
        다시 시도
      </button>
    </div>

    <div class="flex flex-col gap-6 desktop:flex-row">
      <!-- Assets Area -->
      <div class="min-w-0 flex-1">
        <!-- Select All -->
        <div
          v-if="filteredAssets.length > 0"
          class="mb-3 flex items-center gap-2"
        >
          <input
            type="checkbox"
            :checked="isAllSelected"
            class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
            @change="toggleSelectAll"
          />
          <span class="text-body text-gray-500 dark:text-gray-400">
            전체 선택 ({{ filteredAssets.length }}개)
          </span>
        </div>

        <!-- Empty State -->
        <div
          v-if="filteredAssets.length === 0 && !loadError"
          class="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-300 bg-white px-6 py-16 dark:border-gray-700 dark:bg-gray-800"
        >
          <ArchiveBoxXMarkIcon class="mb-4 h-12 w-12 text-gray-300 dark:text-gray-600" />
          <h3 class="mb-1 text-body-lg font-medium text-gray-900 dark:text-white">
            {{ hasActiveFilters ? '필터 조건에 맞는 에셋이 없습니다' : '아직 에셋이 없어요' }}
          </h3>
          <p class="mb-4 text-body text-gray-500 dark:text-gray-400">
            {{ hasActiveFilters ? '필터를 변경하거나 초기화해 보세요.' : '영상, 이미지, 오디오 파일을 업로드하여 관리해보세요.' }}
          </p>
          <button
            v-if="!hasActiveFilters"
            class="btn-primary inline-flex items-center gap-2"
            @click="showUploadModal = true"
          >
            <CloudArrowUpIcon class="h-5 w-5" />
            첫 에셋 업로드하기
          </button>
          <button
            v-else
            class="rounded-lg border border-gray-300 px-4 py-2 text-body font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="clearFilters"
          >
            필터 초기화
          </button>
        </div>

        <!-- Grid View -->
        <div
          v-else-if="viewMode === 'grid'"
          class="page-grid page-grid--cards"
        >
          <AssetCard
            v-for="asset in filteredAssets"
            :key="asset.id"
            :asset="asset"
            :selected="isSelected(asset.id)"
            :view-mode="'grid'"
            @select="assetsStore.toggleSelection"
            @preview="openPreview"
            @delete="handleDelete"
            @use="handleUseAsContent"
          />
        </div>

        <!-- List View -->
        <div
          v-else
          class="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800"
        >
          <div class="overflow-x-auto">
            <table class="w-full min-w-[700px]">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 text-left text-caption uppercase tracking-wider text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400">
                  <th class="w-10 px-4 py-3">
                    <input
                      type="checkbox"
                      :checked="isAllSelected"
                      class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
                      @change="toggleSelectAll"
                    />
                  </th>
                  <th class="px-4 py-3">파일</th>
                  <th class="px-4 py-3">유형</th>
                  <th class="px-4 py-3">크기</th>
                  <th class="px-4 py-3">상세</th>
                  <th class="w-24 px-4 py-3"></th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                <AssetCard
                  v-for="asset in filteredAssets"
                  :key="asset.id"
                  :asset="asset"
                  :selected="isSelected(asset.id)"
                  :view-mode="'list'"
                  @select="assetsStore.toggleSelection"
                  @preview="openPreview"
                  @delete="handleDelete"
                  @use="handleUseAsContent"
                />
              </tbody>
            </table>
          </div>
        </div>

        <!--
          Result count + Pagination

          예전에는 `filteredAssets.length` 를 "총 N개"로 보여 줬다. 페이지네이션이 붙은
          지금 그 값은 **이 페이지의 건수**라, 그대로 두면 24개를 보여 주면서 "총 24개"라고
          말한다. 총계는 서버가 조건과 함께 센 값을 쓴다.
        -->
        <div v-if="totalCount > 0" class="mt-4 flex flex-wrap items-center justify-between gap-3">
          <p class="text-body-xs text-gray-500 dark:text-gray-400">
            {{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalCount) }} / 총 {{ totalCount }}개
          </p>
          <div v-if="totalPages > 1" class="flex gap-2">
            <button
              type="button"
              class="btn-secondary text-body"
              :disabled="!hasPrevPage || assetsStore.loading"
              @click="assetsStore.prevPage()"
            >
              이전
            </button>
            <button
              type="button"
              class="btn-secondary text-body"
              :disabled="!hasNextPage || assetsStore.loading"
              @click="assetsStore.nextPage()"
            >
              다음
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar -->
    <Transition name="slide-up">
      <div
        v-if="hasSelection"
        class="fixed bottom-6 left-1/2 z-40 flex -translate-x-1/2 items-center gap-3 rounded-2xl border border-gray-200 bg-white px-5 py-3 shadow-xl dark:border-gray-700 dark:bg-gray-800"
      >
        <span class="text-body font-medium text-gray-700 dark:text-gray-300">
          {{ selectedCount }}개 선택됨
        </span>
        <div class="h-5 w-px bg-gray-200 dark:bg-gray-700" />
        <button
          class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-body font-medium text-error-strong hover:bg-error-subtle"
          @click="handleBulkDelete"
        >
          <TrashIcon class="h-4 w-4" />
          삭제
        </button>
        <button
          class="ml-1 rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          title="선택 해제"
          @click="assetsStore.clearSelection()"
        >
          <XMarkIcon class="h-4 w-4" />
        </button>
      </div>
    </Transition>

    <!-- Upload Modal -->
    <AssetUploadModal
      v-model="showUploadModal"
      @uploaded="onUploaded"
    />

    <!-- Preview Modal -->
    <AssetPreviewModal
      v-model="showPreviewModal"
      :asset="previewAsset"
      @delete="handleDelete"
    />

    <!-- 에셋 삭제 확인 (단건 / 일괄 공용) -->
    <ConfirmModal
      v-model="showConfirmModal"
      :title="confirmTitle"
      :message="confirmMessage"
      :confirm-text="$t('action.delete')"
      danger
      @confirm="runPendingAction"
      @cancel="pendingAction = null"
    />
  </div>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translate(-50%, 20px);
}
</style>
