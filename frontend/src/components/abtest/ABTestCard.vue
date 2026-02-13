<script setup lang="ts">
import { computed } from 'vue'
import { TrophyIcon, PlayIcon, PauseIcon, TrashIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import type { ABTest } from '@/types/abtest'

interface Props {
  test: ABTest
}

interface Emits {
  (e: 'start', id: number): void
  (e: 'stop', id: number): void
  (e: 'delete', id: number): void
  (e: 'view-results', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const typeLabel = computed(() => {
  const labels = {
    thumbnail: '썸네일',
    title: '제목',
    description: '설명'
  }
  return labels[props.test.type]
})

const statusLabel = computed(() => {
  const labels = {
    draft: '초안',
    running: '진행 중',
    completed: '완료',
    cancelled: '취소됨'
  }
  return labels[props.test.status]
})

const statusColor = computed(() => {
  const colors = {
    draft: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    running: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    completed: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    cancelled: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
  }
  return colors[props.test.status]
})

const progressPercentage = computed(() => {
  if (props.test.status === 'draft') return 0
  if (props.test.status === 'completed' || props.test.status === 'cancelled') return 100

  const start = new Date(props.test.startDate).getTime()
  const duration = props.test.duration * 60 * 60 * 1000
  const elapsed = Date.now() - start
  return Math.min(100, Math.round((elapsed / duration) * 100))
})

const totalImpressions = computed(() => {
  return props.test.variants.reduce((sum, v) => sum + v.impressions, 0)
})

const winner = computed(() => {
  return props.test.variants.find(v => v.isWinner)
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-6 hover:shadow-lg transition-shadow">
    <!-- Header -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex-1">
        <div class="flex items-center gap-2 mb-2">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white">{{ test.name }}</h3>
          <TrophyIcon v-if="winner" class="w-5 h-5 text-yellow-500" />
        </div>
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ test.videoTitle }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="px-2.5 py-1 text-xs font-medium rounded-full bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400">
          {{ typeLabel }}
        </span>
        <span :class="['px-2.5 py-1 text-xs font-medium rounded-full', statusColor]">
          {{ statusLabel }}
        </span>
      </div>
    </div>

    <!-- Variants Comparison -->
    <div class="grid grid-cols-2 gap-4 mb-4">
      <div v-for="variant in test.variants" :key="variant.id" class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ variant.label }}</span>
          <CheckCircleIcon v-if="variant.isWinner" class="w-5 h-5 text-green-500" />
        </div>

        <!-- Thumbnail preview -->
        <div v-if="test.type === 'thumbnail' && variant.thumbnailUrl" class="relative aspect-video rounded-lg overflow-hidden bg-gray-100 dark:bg-gray-700">
          <img :src="variant.thumbnailUrl" :alt="variant.label" class="w-full h-full object-cover" />
        </div>

        <!-- Text preview -->
        <div v-else-if="test.type === 'title' || test.type === 'description'" class="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
          <p class="text-sm text-gray-700 dark:text-gray-300 line-clamp-2">{{ variant.content }}</p>
        </div>

        <!-- Metrics -->
        <div class="space-y-1">
          <div class="flex justify-between text-xs">
            <span class="text-gray-600 dark:text-gray-400">노출</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ variant.impressions.toLocaleString() }}</span>
          </div>
          <div class="flex justify-between text-xs">
            <span class="text-gray-600 dark:text-gray-400">CTR</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ variant.ctr.toFixed(1) }}%</span>
          </div>
          <div class="flex justify-between text-xs">
            <span class="text-gray-600 dark:text-gray-400">시청 시간</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ variant.watchTime }}초</span>
          </div>
        </div>

        <!-- Metrics bar -->
        <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
          <div
            :class="['h-full transition-all', variant.isWinner ? 'bg-green-500' : 'bg-blue-500']"
            :style="{ width: test.variants.length > 1 ? `${(variant.ctr / Math.max(...test.variants.map(v => v.ctr))) * 100}%` : '0%' }"
          ></div>
        </div>
      </div>
    </div>

    <!-- Progress bar for running tests -->
    <div v-if="test.status === 'running'" class="mb-4">
      <div class="flex justify-between text-xs text-gray-600 dark:text-gray-400 mb-1">
        <span>진행률</span>
        <span>{{ progressPercentage }}%</span>
      </div>
      <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div class="h-full bg-green-500 transition-all" :style="{ width: `${progressPercentage}%` }"></div>
      </div>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-3 gap-4 mb-4 p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
      <div>
        <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">총 노출</div>
        <div class="text-lg font-semibold text-gray-900 dark:text-white">{{ totalImpressions.toLocaleString() }}</div>
      </div>
      <div>
        <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">샘플 크기</div>
        <div class="text-lg font-semibold text-gray-900 dark:text-white">{{ test.sampleSize.toLocaleString() }}</div>
      </div>
      <div>
        <div class="text-xs text-gray-600 dark:text-gray-400 mb-1">신뢰도</div>
        <div class="text-lg font-semibold text-gray-900 dark:text-white">{{ test.confidence }}%</div>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-2">
      <button
        v-if="test.status === 'completed'"
        @click="emit('view-results', test.id)"
        class="px-4 py-2 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
      >
        결과 보기
      </button>
      <button
        v-if="test.status === 'draft'"
        @click="emit('start', test.id)"
        class="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-green-600 hover:bg-green-700 dark:bg-green-600 dark:hover:bg-green-700 rounded-lg transition-colors"
      >
        <PlayIcon class="w-4 h-4" />
        시작하기
      </button>
      <button
        v-if="test.status === 'running'"
        @click="emit('stop', test.id)"
        class="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
      >
        <PauseIcon class="w-4 h-4" />
        중지하기
      </button>
      <button
        v-if="test.status === 'draft' || test.status === 'cancelled'"
        @click="emit('delete', test.id)"
        class="flex items-center gap-2 px-4 py-2 text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors"
      >
        <TrashIcon class="w-4 h-4" />
        삭제
      </button>
    </div>
  </div>
</template>
