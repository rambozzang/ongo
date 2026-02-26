<script setup lang="ts">
import { computed } from 'vue'
import {
  PhotoIcon,
  EyeIcon,
  LockClosedIcon,
  SwatchIcon,
} from '@heroicons/vue/24/outline'
import type { MoodBoard } from '@/types/moodBoard'

interface Props {
  board: MoodBoard
}

interface Emits {
  (e: 'select', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const categoryConfig: Record<string, { color: string }> = {
  '시즌': { color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400' },
  '테크': { color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
  '브이로그': { color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400' },
  '디자인': { color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' },
  '뷰티': { color: 'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-400' },
  '음식': { color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400' },
  '여행': { color: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-400' },
}

const categoryStyle = computed(() => {
  return categoryConfig[props.board.category] ?? {
    color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
  }
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group overflow-hidden"
    @click="emit('select', board.id)"
  >
    <!-- Cover image or placeholder -->
    <div class="relative h-36 bg-gray-100 dark:bg-gray-800 flex items-center justify-center overflow-hidden">
      <img
        v-if="board.coverImage"
        :src="board.coverImage"
        :alt="board.name"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
      />
      <div v-else class="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
        <SwatchIcon class="w-10 h-10" />
        <span class="text-xs">커버 이미지 없음</span>
      </div>

      <!-- Category badge -->
      <span
        :class="['absolute top-2 left-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', categoryStyle.color]"
      >
        {{ board.category }}
      </span>

      <!-- Visibility badge -->
      <span
        class="absolute top-2 right-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium"
        :class="board.isPublic
          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
          : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
        "
      >
        <EyeIcon v-if="board.isPublic" class="w-3 h-3" />
        <LockClosedIcon v-else class="w-3 h-3" />
        {{ board.isPublic ? '공개' : '비공개' }}
      </span>
    </div>

    <!-- Content -->
    <div class="p-4">
      <!-- Title & description -->
      <div class="mb-3">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
          {{ board.name }}
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 mt-1">
          {{ board.description }}
        </p>
      </div>

      <!-- Item count -->
      <div class="flex items-center gap-2 mb-3 text-sm text-gray-600 dark:text-gray-400">
        <PhotoIcon class="w-4 h-4" />
        <span>{{ board.itemCount }}개 아이템</span>
      </div>

      <!-- Tags -->
      <div v-if="board.tags.length > 0" class="flex flex-wrap gap-1 mb-3">
        <span
          v-for="tag in board.tags.slice(0, 5)"
          :key="tag"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          #{{ tag }}
        </span>
        <span
          v-if="board.tags.length > 5"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          +{{ board.tags.length - 5 }}
        </span>
      </div>

      <!-- Footer -->
      <p class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatDate(board.createdAt) }}
      </p>
    </div>
  </div>
</template>
