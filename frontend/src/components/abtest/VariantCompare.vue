<script setup lang="ts">
import { computed } from 'vue'
import { TrophyIcon } from '@heroicons/vue/24/outline'
import type { TestVariant, TestType } from '@/types/abtest'

interface Props {
  variants: TestVariant[]
  type: TestType
}

const props = defineProps<Props>()

const maxCTR = computed(() => Math.max(...props.variants.map(v => v.ctr)))
</script>

<template>
  <div class="grid grid-cols-2 gap-6">
    <div v-for="variant in variants" :key="variant.id" class="space-y-3">
      <!-- Header with label and winner badge -->
      <div class="flex items-center justify-between">
        <h4 class="text-lg font-semibold text-gray-900 dark:text-white">{{ variant.label }}</h4>
        <div v-if="variant.isWinner" class="flex items-center gap-1.5 px-3 py-1 bg-yellow-100 dark:bg-yellow-900/30 rounded-full">
          <TrophyIcon class="w-4 h-4 text-yellow-600 dark:text-yellow-400" />
          <span class="text-sm font-medium text-yellow-600 dark:text-yellow-400">우승</span>
        </div>
      </div>

      <!-- Content preview -->
      <div class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
        <!-- Thumbnail preview -->
        <div v-if="type === 'thumbnail' && variant.thumbnailUrl" class="aspect-video bg-gray-100 dark:bg-gray-700">
          <img :src="variant.thumbnailUrl" :alt="variant.label" class="w-full h-full object-cover" />
        </div>

        <!-- Text preview -->
        <div v-else class="p-4 bg-gray-50 dark:bg-gray-700/50">
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ variant.content }}</p>
        </div>
      </div>

      <!-- Metrics -->
      <div class="space-y-3">
        <!-- Impressions -->
        <div>
          <div class="flex justify-between text-sm mb-1">
            <span class="text-gray-600 dark:text-gray-400">노출 수</span>
            <span class="font-semibold text-gray-900 dark:text-white">{{ variant.impressions.toLocaleString() }}</span>
          </div>
          <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: `${(variant.impressions / Math.max(...variants.map(v => v.impressions))) * 100}%` }"
            ></div>
          </div>
        </div>

        <!-- Clicks -->
        <div>
          <div class="flex justify-between text-sm mb-1">
            <span class="text-gray-600 dark:text-gray-400">클릭 수</span>
            <span class="font-semibold text-gray-900 dark:text-white">{{ variant.clicks.toLocaleString() }}</span>
          </div>
          <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: `${(variant.clicks / Math.max(...variants.map(v => v.clicks))) * 100}%` }"
            ></div>
          </div>
        </div>

        <!-- CTR -->
        <div>
          <div class="flex justify-between text-sm mb-1">
            <span class="text-gray-600 dark:text-gray-400">클릭률 (CTR)</span>
            <span class="font-semibold text-gray-900 dark:text-white">{{ variant.ctr.toFixed(2) }}%</span>
          </div>
          <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: `${(variant.ctr / maxCTR) * 100}%` }"
            ></div>
          </div>
        </div>

        <!-- Watch time -->
        <div>
          <div class="flex justify-between text-sm mb-1">
            <span class="text-gray-600 dark:text-gray-400">평균 시청 시간</span>
            <span class="font-semibold text-gray-900 dark:text-white">{{ variant.watchTime }}초</span>
          </div>
          <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: `${(variant.watchTime / Math.max(...variants.map(v => v.watchTime))) * 100}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
