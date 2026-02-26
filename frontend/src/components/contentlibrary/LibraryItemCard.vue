<script setup lang="ts">
import { computed } from 'vue'
import {
  FilmIcon,
  PhotoIcon,
  MusicalNoteIcon,
  DocumentIcon,
  TrashIcon,
  FolderIcon,
} from '@heroicons/vue/24/outline'
import type { LibraryItem } from '@/types/contentLibrary'

const props = defineProps<{
  item: LibraryItem
}>()

defineEmits<{
  delete: [id: number]
}>()

const typeConfig = computed(() => {
  const configs: Record<LibraryItem['type'], { icon: typeof FilmIcon; color: string; bgColor: string }> = {
    VIDEO: {
      icon: FilmIcon,
      color: 'text-red-500 dark:text-red-400',
      bgColor: 'bg-red-50 dark:bg-red-900/20',
    },
    IMAGE: {
      icon: PhotoIcon,
      color: 'text-blue-500 dark:text-blue-400',
      bgColor: 'bg-blue-50 dark:bg-blue-900/20',
    },
    AUDIO: {
      icon: MusicalNoteIcon,
      color: 'text-purple-500 dark:text-purple-400',
      bgColor: 'bg-purple-50 dark:bg-purple-900/20',
    },
    DOCUMENT: {
      icon: DocumentIcon,
      color: 'text-amber-500 dark:text-amber-400',
      bgColor: 'bg-amber-50 dark:bg-amber-900/20',
    },
  }
  return configs[props.item.type] ?? configs.DOCUMENT
})

const platformBadge = computed(() => {
  if (!props.item.platform) return null
  const configs: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    INSTAGRAM: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return configs[props.item.platform.toUpperCase()] ?? null
})

function formatFileSize(bytes: number): string {
  if (bytes >= 1073741824) return `${(bytes / 1073741824).toFixed(1)} GB`
  if (bytes >= 1048576) return `${(bytes / 1048576).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${bytes} B`
}
</script>

<template>
  <div class="group rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900">
    <!-- Type icon + Title -->
    <div class="mb-3 flex items-start gap-3">
      <div
        class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg"
        :class="typeConfig.bgColor"
      >
        <component :is="typeConfig.icon" class="h-5 w-5" :class="typeConfig.color" />
      </div>
      <div class="min-w-0 flex-1">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ item.title }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ formatFileSize(item.fileSize) }}
        </p>
      </div>
    </div>

    <!-- Platform + Folder -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        v-if="platformBadge"
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="platformBadge"
      >
        {{ item.platform }}
      </span>
      <span
        v-if="item.folderName"
        class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-400"
      >
        <FolderIcon class="h-3 w-3" />
        {{ item.folderName }}
      </span>
    </div>

    <!-- Tags -->
    <div v-if="item.tags.length > 0" class="mb-3 flex flex-wrap gap-1">
      <span
        v-for="tag in item.tags.slice(0, 4)"
        :key="tag"
        class="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        #{{ tag }}
      </span>
      <span
        v-if="item.tags.length > 4"
        class="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        +{{ item.tags.length - 4 }}
      </span>
    </div>

    <!-- Footer: Date + Delete -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ new Date(item.uploadedAt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' }) }}
      </span>
      <button
        class="rounded-lg p-1.5 text-gray-400 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:hover:bg-red-900/20 dark:hover:text-red-400"
        @click.stop="$emit('delete', item.id)"
      >
        <TrashIcon class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>
