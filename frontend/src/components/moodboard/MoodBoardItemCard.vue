<script setup lang="ts">
import {
  PhotoIcon,
  DocumentTextIcon,
} from '@heroicons/vue/24/outline'
import type { MoodBoardItem } from '@/types/moodBoard'

interface Props {
  item: MoodBoardItem
}

defineProps<Props>()

const typeConfig: Record<string, { label: string; icon: string; color: string }> = {
  IMAGE: {
    label: '이미지',
    icon: 'photo',
    color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  },
  COLOR: {
    label: '컬러',
    icon: 'color',
    color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
  },
  TEXT: {
    label: '텍스트',
    icon: 'text',
    color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  },
}

function getTypeConfig(type: string) {
  return typeConfig[type] ?? { label: type, icon: 'text', color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 overflow-hidden group">
    <!-- Type badge -->
    <div class="px-3 pt-3 pb-1">
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', getTypeConfig(item.type).color]"
      >
        {{ getTypeConfig(item.type).label }}
      </span>
    </div>

    <!-- Content by type -->
    <div class="px-3 pb-3">
      <!-- IMAGE type -->
      <template v-if="item.type === 'IMAGE'">
        <div class="relative mt-2 rounded-lg overflow-hidden bg-gray-100 dark:bg-gray-800 h-40 flex items-center justify-center">
          <img
            v-if="item.imageUrl"
            :src="item.imageUrl"
            :alt="item.title"
            class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
          />
          <div v-else class="flex flex-col items-center gap-1 text-gray-400 dark:text-gray-500">
            <PhotoIcon class="w-8 h-8" />
            <span class="text-xs">이미지 미리보기</span>
          </div>
        </div>
      </template>

      <!-- COLOR type -->
      <template v-else-if="item.type === 'COLOR'">
        <div class="mt-2 flex items-center gap-3">
          <div
            class="w-20 h-20 rounded-lg border border-gray-200 dark:border-gray-600 shadow-inner flex-shrink-0"
            :style="{ backgroundColor: item.color ?? '#cccccc' }"
          ></div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-mono font-medium text-gray-900 dark:text-gray-100">
              {{ item.color ?? '-' }}
            </p>
            <p v-if="item.note" class="text-xs text-gray-500 dark:text-gray-400 mt-1 line-clamp-2">
              {{ item.note }}
            </p>
          </div>
        </div>
      </template>

      <!-- TEXT type -->
      <template v-else>
        <div class="mt-2 rounded-lg bg-gray-50 dark:bg-gray-800 p-3 min-h-[80px]">
          <DocumentTextIcon class="w-5 h-5 text-gray-400 dark:text-gray-500 mb-2" />
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap line-clamp-4">
            {{ item.note ?? '-' }}
          </p>
        </div>
      </template>

      <!-- Title -->
      <h4 class="mt-2 text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ item.title }}
      </h4>

      <!-- Note (for IMAGE type only) -->
      <p
        v-if="item.type === 'IMAGE' && item.note"
        class="mt-1 text-xs text-gray-500 dark:text-gray-400 line-clamp-2"
      >
        {{ item.note }}
      </p>

      <!-- Source URL -->
      <a
        v-if="item.sourceUrl"
        :href="item.sourceUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="mt-2 inline-flex items-center text-xs text-primary-600 dark:text-primary-400 hover:underline"
        @click.stop
      >
        출처 보기 &rarr;
      </a>
    </div>
  </div>
</template>
