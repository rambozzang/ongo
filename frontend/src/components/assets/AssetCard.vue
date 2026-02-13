<script setup lang="ts">
import { computed } from 'vue'
import type { Asset } from '@/types/asset'
import {
  VideoCameraIcon,
  PhotoIcon,
  MusicalNoteIcon,
  DocumentIcon,
  EyeIcon,
  TrashIcon,
  FolderArrowDownIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  asset: Asset
  selected: boolean
  viewMode: 'grid' | 'list'
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
  (e: 'preview', asset: Asset): void
  (e: 'delete', id: number): void
  (e: 'move', id: number): void
}>()

const typeConfig = computed(() => {
  const configs: Record<string, { label: string; color: string; bgColor: string }> = {
    VIDEO: {
      label: '영상',
      color: 'text-blue-700 dark:text-blue-400',
      bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    },
    IMAGE: {
      label: '이미지',
      color: 'text-green-700 dark:text-green-400',
      bgColor: 'bg-green-100 dark:bg-green-900/30',
    },
    AUDIO: {
      label: '오디오',
      color: 'text-purple-700 dark:text-purple-400',
      bgColor: 'bg-purple-100 dark:bg-purple-900/30',
    },
    TEMPLATE: {
      label: '템플릿',
      color: 'text-orange-700 dark:text-orange-400',
      bgColor: 'bg-orange-100 dark:bg-orange-900/30',
    },
  }
  return configs[props.asset.type] ?? configs.TEMPLATE
})

const typeIcon = computed(() => {
  const icons: Record<string, any> = {
    VIDEO: VideoCameraIcon,
    IMAGE: PhotoIcon,
    AUDIO: MusicalNoteIcon,
    TEMPLATE: DocumentIcon,
  }
  return icons[props.asset.type] ?? DocumentIcon
})

function formatFileSize(bytes: number): string {
  if (bytes >= 1_073_741_824) {
    return (bytes / 1_073_741_824).toFixed(1) + ' GB'
  }
  if (bytes >= 1_048_576) {
    return (bytes / 1_048_576).toFixed(1) + ' MB'
  }
  if (bytes >= 1024) {
    return (bytes / 1024).toFixed(1) + ' KB'
  }
  return bytes + ' B'
}

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) {
    return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }
  return `${m}:${s.toString().padStart(2, '0')}`
}
</script>

<template>
  <!-- Grid View -->
  <div
    v-if="viewMode === 'grid'"
    class="group relative cursor-pointer overflow-hidden rounded-xl border border-gray-200 bg-white transition-all hover:shadow-lg dark:border-gray-700 dark:bg-gray-800"
    :class="{ 'ring-2 ring-primary-500 dark:ring-primary-400': selected }"
    @click="emit('preview', asset)"
  >
    <!-- Checkbox -->
    <div class="absolute left-3 top-3 z-10">
      <input
        type="checkbox"
        :checked="selected"
        class="h-4 w-4 rounded border-gray-300 bg-white/80 text-primary-600 focus:ring-primary-500"
        @click.stop
        @change.stop="emit('select', asset.id)"
      />
    </div>

    <!-- Type Badge -->
    <div class="absolute right-3 top-3 z-10">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="[typeConfig.bgColor, typeConfig.color]"
      >
        {{ typeConfig.label }}
      </span>
    </div>

    <!-- Thumbnail Area -->
    <div class="relative aspect-video w-full bg-gray-100 dark:bg-gray-900">
      <img
        v-if="asset.thumbnail"
        :src="asset.thumbnail"
        :alt="asset.name"
        class="h-full w-full object-cover"
      />
      <div v-else class="flex h-full items-center justify-center">
        <component
          :is="typeIcon"
          class="h-12 w-12 text-gray-300 dark:text-gray-600"
        />
      </div>

      <!-- Duration overlay for video/audio -->
      <span
        v-if="asset.duration"
        class="absolute bottom-2 right-2 rounded bg-black/75 px-1.5 py-0.5 text-xs font-medium text-white"
      >
        {{ formatDuration(asset.duration) }}
      </span>

      <!-- Hover overlay with actions -->
      <div
        class="absolute inset-0 flex items-center justify-center gap-2 bg-black/40 opacity-0 transition-opacity group-hover:opacity-100"
        @click.stop
      >
        <button
          class="rounded-full bg-white/90 p-2 text-gray-700 shadow-md transition-transform hover:scale-110 hover:bg-white dark:bg-gray-800/90 dark:text-gray-200 dark:hover:bg-gray-800"
          title="미리보기"
          @click="emit('preview', asset)"
        >
          <EyeIcon class="h-5 w-5" />
        </button>
        <button
          class="rounded-full bg-white/90 p-2 text-gray-700 shadow-md transition-transform hover:scale-110 hover:bg-white dark:bg-gray-800/90 dark:text-gray-200 dark:hover:bg-gray-800"
          title="폴더 이동"
          @click="emit('move', asset.id)"
        >
          <FolderArrowDownIcon class="h-5 w-5" />
        </button>
        <button
          class="rounded-full bg-white/90 p-2 text-red-600 shadow-md transition-transform hover:scale-110 hover:bg-white dark:bg-gray-800/90 dark:hover:bg-gray-800"
          title="삭제"
          @click="emit('delete', asset.id)"
        >
          <TrashIcon class="h-5 w-5" />
        </button>
      </div>
    </div>

    <!-- Info -->
    <div class="p-3">
      <h3
        class="mb-1 truncate text-sm font-medium text-gray-900 dark:text-gray-100"
        :title="asset.name"
      >
        {{ asset.name }}
      </h3>
      <p class="mb-2 text-xs text-gray-500 dark:text-gray-400">
        {{ formatFileSize(asset.fileSize) }}
        <template v-if="asset.width && asset.height">
          &middot; {{ asset.width }}x{{ asset.height }}
        </template>
      </p>
      <!-- Tags -->
      <div v-if="asset.tags.length > 0" class="flex flex-wrap gap-1">
        <span
          v-for="tag in asset.tags.slice(0, 3)"
          :key="tag"
          class="inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          {{ tag }}
        </span>
        <span
          v-if="asset.tags.length > 3"
          class="inline-block rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-gray-700 dark:text-gray-400"
        >
          +{{ asset.tags.length - 3 }}
        </span>
      </div>
    </div>
  </div>

  <!-- List View -->
  <tr
    v-else
    class="cursor-pointer transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
    :class="{ 'bg-primary-50 dark:bg-primary-900/20': selected }"
    @click="emit('preview', asset)"
  >
    <td class="px-4 py-3" @click.stop>
      <input
        type="checkbox"
        :checked="selected"
        class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
        @change="emit('select', asset.id)"
      />
    </td>
    <td class="px-4 py-3">
      <div class="flex items-center gap-3">
        <div class="h-10 w-16 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-900">
          <img
            v-if="asset.thumbnail"
            :src="asset.thumbnail"
            :alt="asset.name"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full items-center justify-center">
            <component :is="typeIcon" class="h-5 w-5 text-gray-300 dark:text-gray-600" />
          </div>
        </div>
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100" :title="asset.name">
            {{ asset.name }}
          </p>
          <div class="flex flex-wrap gap-1 mt-0.5">
            <span
              v-for="tag in asset.tags.slice(0, 2)"
              :key="tag"
              class="inline-block rounded-full bg-gray-100 px-1.5 py-0 text-[10px] text-gray-500 dark:bg-gray-700 dark:text-gray-400"
            >
              {{ tag }}
            </span>
            <span
              v-if="asset.tags.length > 2"
              class="text-[10px] text-gray-400 dark:text-gray-500"
            >
              +{{ asset.tags.length - 2 }}
            </span>
          </div>
        </div>
      </div>
    </td>
    <td class="px-4 py-3">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="[typeConfig.bgColor, typeConfig.color]"
      >
        {{ typeConfig.label }}
      </span>
    </td>
    <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300">
      {{ formatFileSize(asset.fileSize) }}
    </td>
    <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">
      <template v-if="asset.duration">{{ formatDuration(asset.duration) }}</template>
      <template v-else-if="asset.width && asset.height">{{ asset.width }}x{{ asset.height }}</template>
      <template v-else>-</template>
    </td>
    <td class="px-4 py-3" @click.stop>
      <div class="flex items-center gap-1">
        <button
          class="rounded-full p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          title="폴더 이동"
          @click="emit('move', asset.id)"
        >
          <FolderArrowDownIcon class="h-4 w-4" />
        </button>
        <button
          class="rounded-full p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          title="삭제"
          @click="emit('delete', asset.id)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </td>
  </tr>
</template>
