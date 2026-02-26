<script setup lang="ts">
import {
  LightBulbIcon,
} from '@heroicons/vue/24/outline'
import type { SeoAnalysis } from '@/types/videoSeo'
import SeoScoreGauge from './SeoScoreGauge.vue'

defineProps<{
  analysis: SeoAnalysis
}>()

const emit = defineEmits<{
  select: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVER_CLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.YOUTUBE

const scoreBarColor = (score: number) => {
  if (score >= 80) return 'bg-green-500'
  if (score >= 60) return 'bg-yellow-500'
  return 'bg-red-500'
}

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
  } catch {
    return iso
  }
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm transition-shadow hover:shadow-md"
    @click="emit('select', analysis.id)"
  >
    <!-- Header: Title + Platform + Date -->
    <div class="mb-4 flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-1">
          {{ analysis.videoTitle }}
        </h3>
        <div class="mt-1 flex items-center gap-2">
          <span
            :class="[getPlatformStyle(analysis.platform).bg, getPlatformStyle(analysis.platform).text]"
            class="rounded-full px-2 py-0.5 text-xs font-medium"
          >
            {{ analysis.platform }}
          </span>
          <span class="text-xs text-gray-400 dark:text-gray-500">
            {{ formatDate(analysis.analyzedAt) }}
          </span>
        </div>
      </div>

      <!-- Overall score gauge -->
      <div class="ml-4 shrink-0">
        <SeoScoreGauge :score="analysis.overallScore" size="sm" />
      </div>
    </div>

    <!-- Score bars -->
    <div class="mb-4 space-y-2">
      <div class="flex items-center gap-2">
        <span class="w-14 text-xs text-gray-500 dark:text-gray-400">제목</span>
        <div class="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
          <div :class="scoreBarColor(analysis.titleScore)" class="h-full rounded-full transition-all duration-500" :style="{ width: `${analysis.titleScore}%` }" />
        </div>
        <span class="w-8 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">{{ analysis.titleScore }}</span>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-14 text-xs text-gray-500 dark:text-gray-400">설명</span>
        <div class="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
          <div :class="scoreBarColor(analysis.descriptionScore)" class="h-full rounded-full transition-all duration-500" :style="{ width: `${analysis.descriptionScore}%` }" />
        </div>
        <span class="w-8 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">{{ analysis.descriptionScore }}</span>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-14 text-xs text-gray-500 dark:text-gray-400">태그</span>
        <div class="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
          <div :class="scoreBarColor(analysis.tagScore)" class="h-full rounded-full transition-all duration-500" :style="{ width: `${analysis.tagScore}%` }" />
        </div>
        <span class="w-8 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">{{ analysis.tagScore }}</span>
      </div>

      <div class="flex items-center gap-2">
        <span class="w-14 text-xs text-gray-500 dark:text-gray-400">썸네일</span>
        <div class="flex-1 h-2 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
          <div :class="scoreBarColor(analysis.thumbnailScore)" class="h-full rounded-full transition-all duration-500" :style="{ width: `${analysis.thumbnailScore}%` }" />
        </div>
        <span class="w-8 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">{{ analysis.thumbnailScore }}</span>
      </div>
    </div>

    <!-- Suggestions -->
    <div v-if="analysis.suggestions.length > 0" class="rounded-lg bg-amber-50 p-3 dark:bg-amber-900/10">
      <div class="mb-1.5 flex items-center gap-1.5">
        <LightBulbIcon class="h-3.5 w-3.5 text-amber-600 dark:text-amber-400" />
        <span class="text-xs font-medium text-amber-700 dark:text-amber-300">개선 제안</span>
      </div>
      <ul class="space-y-1">
        <li
          v-for="(suggestion, index) in analysis.suggestions.slice(0, 3)"
          :key="index"
          class="text-xs text-amber-800 dark:text-amber-200"
        >
          &bull; {{ suggestion }}
        </li>
      </ul>
    </div>
  </div>
</template>
