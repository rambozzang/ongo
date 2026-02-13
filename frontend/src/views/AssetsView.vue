<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAssetsStore } from '@/stores/assets'
import type { Asset, AssetType } from '@/types/asset'
import FolderSidebar from '@/components/assets/FolderSidebar.vue'
import AssetCard from '@/components/assets/AssetCard.vue'
import AssetUploadModal from '@/components/assets/AssetUploadModal.vue'
import AssetPreviewModal from '@/components/assets/AssetPreviewModal.vue'
import {
  PlusIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  Squares2X2Icon,
  ListBulletIcon,
  TrashIcon,
  FolderArrowDownIcon,
  ArchiveBoxXMarkIcon,
  CloudArrowUpIcon,
} from '@heroicons/vue/24/outline'

const assetsStore = useAssetsStore()
const {
  filteredAssets,
  viewMode,
  selectedAssets,
  filter: _filter,
  storageUsed,
  storageLimit,
  folders,
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
const showMoveModal = ref(false)
const moveTargetAssetId = ref<number | null>(null)
const moveTargetFolderId = ref<number | null>(null)

function openPreview(asset: Asset) {
  previewAsset.value = asset
  showPreviewModal.value = true
}

function openMoveModal(assetId: number) {
  moveTargetAssetId.value = assetId
  moveTargetFolderId.value = null
  showMoveModal.value = true
}

function confirmMove() {
  if (moveTargetAssetId.value !== null) {
    assetsStore.moveToFolder(moveTargetAssetId.value, moveTargetFolderId.value)
  }
  showMoveModal.value = false
  moveTargetAssetId.value = null
}

// Single delete
function handleDelete(id: number) {
  if (confirm('이 에셋을 삭제하시겠습니까?')) {
    assetsStore.deleteAsset(id)
  }
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
  if (confirm(`선택한 ${count}개의 에셋을 삭제하시겠습니까?`)) {
    assetsStore.bulkDelete([...selectedAssets.value])
  }
}

const showBulkMoveModal = ref(false)
const bulkMoveFolderId = ref<number | null>(null)

function openBulkMoveModal() {
  bulkMoveFolderId.value = null
  showBulkMoveModal.value = true
}

function confirmBulkMove() {
  assetsStore.bulkMove([...selectedAssets.value], bulkMoveFolderId.value)
  showBulkMoveModal.value = false
}

// Storage
const storagePercentage = computed(() => {
  if (storageLimit.value === 0) return 0
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
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          에셋 라이브러리
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          영상, 이미지, 오디오, 템플릿을 한곳에서 관리하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Storage Usage -->
        <div class="hidden items-center gap-2 sm:flex">
          <div class="w-32">
            <div class="mb-0.5 flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>저장 공간</span>
              <span>{{ storagePercentage }}%</span>
            </div>
            <div class="h-1.5 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full transition-all"
                :class="
                  storagePercentage > 90
                    ? 'bg-red-500'
                    : storagePercentage > 70
                      ? 'bg-amber-500'
                      : 'bg-primary-600'
                "
                :style="{ width: storagePercentage + '%' }"
              />
            </div>
            <p class="mt-0.5 text-[10px] text-gray-400 dark:text-gray-500">
              {{ formatStorage(storageUsed) }} / {{ formatStorage(storageLimit) }}
            </p>
          </div>
        </div>

        <!-- Upload Button -->
        <button
          class="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-primary-700 transition-colors"
          @click="showUploadModal = true"
        >
          <PlusIcon class="h-5 w-5" />
          에셋 업로드
        </button>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
      <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <!-- Search -->
        <div class="relative flex-1 lg:max-w-sm">
          <MagnifyingGlassIcon
            class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
          />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="파일명 또는 태그 검색..."
            class="w-full rounded-lg border border-gray-300 bg-white py-2 pl-10 pr-9 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100 dark:placeholder-gray-500"
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
          <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
            <button
              v-for="opt in typeOptions"
              :key="opt.value ?? 'all'"
              class="px-3 py-1.5 text-xs font-medium first:rounded-l-lg last:rounded-r-lg transition-colors"
              :class="
                activeTypeFilter === opt.value
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                  : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700'
              "
              @click="onTypeFilter(opt.value)"
            >
              {{ opt.label }}
            </button>
          </div>

          <!-- Tag Filter (dropdown) -->
          <select
            :value="activeTagFilter ?? ''"
            class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-xs text-gray-700 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300"
            @change="onTagFilter(($event.target as HTMLSelectElement).value || undefined)"
          >
            <option value="">태그 전체</option>
            <option v-for="tag in allTags" :key="tag" :value="tag">{{ tag }}</option>
          </select>

          <!-- Clear Filters -->
          <button
            v-if="hasActiveFilters"
            class="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
            @click="clearFilters"
          >
            <XMarkIcon class="h-3.5 w-3.5" />
            필터 초기화
          </button>

          <!-- View Mode Toggle -->
          <div class="ml-auto flex rounded-lg border border-gray-300 dark:border-gray-600">
            <button
              class="rounded-l-lg px-3 py-1.5 text-sm transition-colors"
              :class="
                viewMode === 'grid'
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                  : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700'
              "
              title="그리드 보기"
              @click="assetsStore.viewMode = 'grid'"
            >
              <Squares2X2Icon class="h-5 w-5" />
            </button>
            <button
              class="rounded-r-lg px-3 py-1.5 text-sm transition-colors"
              :class="
                viewMode === 'list'
                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                  : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700'
              "
              title="리스트 보기"
              @click="assetsStore.viewMode = 'list'"
            >
              <ListBulletIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="flex flex-col gap-6 lg:flex-row">
      <!-- Folder Sidebar -->
      <FolderSidebar />

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
            class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            @change="toggleSelectAll"
          />
          <span class="text-sm text-gray-500 dark:text-gray-400">
            전체 선택 ({{ filteredAssets.length }}개)
          </span>
        </div>

        <!-- Empty State -->
        <div
          v-if="filteredAssets.length === 0"
          class="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-300 bg-white px-6 py-16 dark:border-gray-700 dark:bg-gray-800"
        >
          <ArchiveBoxXMarkIcon class="mb-4 h-12 w-12 text-gray-300 dark:text-gray-600" />
          <h3 class="mb-1 text-base font-medium text-gray-900 dark:text-white">
            {{ hasActiveFilters ? '필터 조건에 맞는 에셋이 없습니다' : '아직 에셋이 없어요' }}
          </h3>
          <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
            {{ hasActiveFilters ? '필터를 변경하거나 초기화해 보세요.' : '영상, 이미지, 오디오 파일을 업로드하여 관리해보세요.' }}
          </p>
          <button
            v-if="!hasActiveFilters"
            class="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
            @click="showUploadModal = true"
          >
            <CloudArrowUpIcon class="h-5 w-5" />
            첫 에셋 업로드하기
          </button>
          <button
            v-else
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="clearFilters"
          >
            필터 초기화
          </button>
        </div>

        <!-- Grid View -->
        <div
          v-else-if="viewMode === 'grid'"
          class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"
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
            @move="openMoveModal"
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
                <tr class="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-400">
                  <th class="w-10 px-4 py-3">
                    <input
                      type="checkbox"
                      :checked="isAllSelected"
                      class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
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
                  @move="openMoveModal"
                />
              </tbody>
            </table>
          </div>
        </div>

        <!-- Result count -->
        <p
          v-if="filteredAssets.length > 0"
          class="mt-3 text-center text-xs text-gray-400 dark:text-gray-500"
        >
          총 {{ filteredAssets.length }}개 에셋
        </p>
      </div>
    </div>

    <!-- Bulk Action Bar -->
    <Transition name="slide-up">
      <div
        v-if="hasSelection"
        class="fixed bottom-6 left-1/2 z-40 flex -translate-x-1/2 items-center gap-3 rounded-2xl border border-gray-200 bg-white px-5 py-3 shadow-xl dark:border-gray-700 dark:bg-gray-800"
      >
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ selectedCount }}개 선택됨
        </span>
        <div class="h-5 w-px bg-gray-200 dark:bg-gray-700" />
        <button
          class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="openBulkMoveModal"
        >
          <FolderArrowDownIcon class="h-4 w-4" />
          이동
        </button>
        <button
          class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-900/20"
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

    <!-- Move to Folder Modal (Single) -->
    <Teleport to="body">
      <Transition name="modal">
        <div
          v-if="showMoveModal"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <div class="absolute inset-0 bg-black/50" @click="showMoveModal = false" />
          <div class="relative w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-gray-800">
            <h3 class="mb-4 text-lg font-bold text-gray-900 dark:text-white">폴더 이동</h3>
            <select
              v-model="moveTargetFolderId"
              class="mb-4 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option :value="null">전체 (루트)</option>
              <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                {{ folder.name }}
              </option>
            </select>
            <div class="flex justify-end gap-3">
              <button
                class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                @click="showMoveModal = false"
              >
                취소
              </button>
              <button
                class="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
                @click="confirmMove"
              >
                이동
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Bulk Move Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div
          v-if="showBulkMoveModal"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
        >
          <div class="absolute inset-0 bg-black/50" @click="showBulkMoveModal = false" />
          <div class="relative w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl dark:bg-gray-800">
            <h3 class="mb-2 text-lg font-bold text-gray-900 dark:text-white">폴더 이동</h3>
            <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
              {{ selectedCount }}개의 에셋을 이동할 폴더를 선택하세요.
            </p>
            <select
              v-model="bulkMoveFolderId"
              class="mb-4 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-900 dark:text-gray-100"
            >
              <option :value="null">전체 (루트)</option>
              <option v-for="folder in folders" :key="folder.id" :value="folder.id">
                {{ folder.name }}
              </option>
            </select>
            <div class="flex justify-end gap-3">
              <button
                class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
                @click="showBulkMoveModal = false"
              >
                취소
              </button>
              <button
                class="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
                @click="confirmBulkMove"
              >
                이동
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
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
