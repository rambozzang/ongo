<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  FolderIcon,
  ArchiveBoxIcon,
  FilmIcon,
  PhotoIcon,
  ServerStackIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import LibraryItemCard from '@/components/contentlibrary/LibraryItemCard.vue'
import LibraryFolderCard from '@/components/contentlibrary/LibraryFolderCard.vue'
import LibraryTypeFilter from '@/components/contentlibrary/LibraryTypeFilter.vue'
import { useContentLibraryStore } from '@/stores/contentLibrary'
import type { LibraryItem } from '@/types/contentLibrary'

const { t } = useI18n({ useScope: 'global' })
const store = useContentLibraryStore()

type FilterType = 'ALL' | LibraryItem['type']
const typeFilter = ref<FilterType>('ALL')
const selectedFolderId = ref<number | null>(null)

const filteredItems = computed(() => {
  let list = store.items
  if (selectedFolderId.value !== null) {
    list = list.filter((i) => i.folderId === selectedFolderId.value)
  }
  if (typeFilter.value !== 'ALL') {
    list = list.filter((i) => i.type === typeFilter.value)
  }
  return list
})

function handleSelectFolder(id: number) {
  selectedFolderId.value = selectedFolderId.value === id ? null : id
}

function handleDeleteItem(id: number) {
  if (confirm(t('contentLibrary.confirmDelete'))) {
    store.deleteItem(id)
  }
}

function formatFileSize(bytes: number): string {
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}

onMounted(async () => {
  await Promise.all([store.fetchItems(), store.fetchFolders(), store.fetchSummary()])
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentLibrary.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentLibrary.description') }}
        </p>
      </div>
    </div>

    <PageGuide
      :title="$t('contentLibrary.pageGuideTitle')"
      :items="($tm('contentLibrary.pageGuide') as string[])"
    />

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" full-page />

    <template v-else>
      <!-- Summary Stats -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3 desktop:grid-cols-5">
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ArchiveBoxIcon class="h-5 w-5 text-blue-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.totalItems') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalItems.toLocaleString('ko-KR') }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ServerStackIcon class="h-5 w-5 text-green-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.totalSize') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatFileSize(store.summary.totalSize) }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <FilmIcon class="h-5 w-5 text-red-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.videoCount') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.videoCount }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <PhotoIcon class="h-5 w-5 text-purple-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.imageCount') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.imageCount }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <FolderIcon class="h-5 w-5 text-amber-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.folderCount') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalFolders }}
          </p>
        </div>
      </div>

      <!-- Folders -->
      <div v-if="store.folders.length > 0" class="mb-6">
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentLibrary.folders') }}
          </h2>
          <button
            v-if="selectedFolderId !== null"
            class="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            @click="selectedFolderId = null"
          >
            {{ $t('contentLibrary.showAll') }}
          </button>
        </div>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-4 desktop:grid-cols-6">
          <LibraryFolderCard
            v-for="folder in store.folders"
            :key="folder.id"
            :folder="folder"
            @select="handleSelectFolder"
          />
        </div>
      </div>

      <!-- Type Filter -->
      <div class="mb-6">
        <LibraryTypeFilter v-model="typeFilter" />
      </div>

      <!-- Item Grid -->
      <div v-if="filteredItems.length > 0" class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <LibraryItemCard
          v-for="item in filteredItems"
          :key="item.id"
          :item="item"
          @delete="handleDeleteItem"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
      >
        <ArchiveBoxIcon class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
        <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('contentLibrary.noItems') }}</p>
        <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('contentLibrary.noItemsDesc') }}</p>
      </div>
    </template>
  </div>
</template>
