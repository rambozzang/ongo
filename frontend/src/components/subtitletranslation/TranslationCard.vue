<script setup lang="ts">
import {
  LanguageIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { SubtitleTranslation, SupportedLanguage } from '@/types/subtitleTranslation'

const props = defineProps<{
  translation: SubtitleTranslation
  languages: SupportedLanguage[]
}>()

const emit = defineEmits<{
  click: [id: number]
}>()

const statusConfig: Record<string, { label: string; bg: string; text: string }> = {
  PENDING: { label: '대기중', bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  TRANSLATING: { label: '번역중', bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  COMPLETED: { label: '완료', bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
  FAILED: { label: '실패', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  REVIEWING: { label: '검토중', bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
}

const getStatusStyle = (status: string) => statusConfig[status] ?? statusConfig.PENDING

const getLanguageName = (code: string) => {
  const lang = props.languages.find((l) => l.code === code)
  return lang ? lang.nativeName : code.toUpperCase()
}

const progressColor = (progress: number) => {
  if (progress >= 100) return 'bg-green-500'
  if (progress >= 50) return 'bg-blue-500'
  return 'bg-yellow-500'
}

const qualityColor = (quality: number) => {
  if (quality >= 90) return 'text-green-600 dark:text-green-400'
  if (quality >= 70) return 'text-yellow-600 dark:text-yellow-400'
  if (quality > 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-400'
}

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', translation.id)"
  >
    <!-- Header -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <LanguageIcon class="h-5 w-5 text-primary-500" />
      <span class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">
        {{ translation.videoTitle }}
      </span>
    </div>

    <!-- Language + Status Row -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span class="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ getLanguageName(translation.sourceLanguage) }} &rarr; {{ getLanguageName(translation.targetLanguage) }}
      </span>
      <span
        :class="[getStatusStyle(translation.status).bg, getStatusStyle(translation.status).text]"
        class="rounded-full px-2 py-0.5 text-xs font-semibold"
      >
        {{ getStatusStyle(translation.status).label }}
      </span>
    </div>

    <!-- Progress Bar -->
    <div class="mb-3">
      <div class="mb-1 flex items-center justify-between">
        <span class="text-xs text-gray-500 dark:text-gray-400">진행률</span>
        <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">{{ translation.progress }}%</span>
      </div>
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          :class="progressColor(translation.progress)"
          class="h-full rounded-full transition-all duration-300"
          :style="{ width: `${translation.progress}%` }"
        />
      </div>
    </div>

    <!-- Metrics -->
    <div class="mb-3 grid grid-cols-3 gap-3">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">라인수</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ translation.translatedLineCount }}/{{ translation.sourceLineCount }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">품질 점수</p>
        <p class="text-sm font-semibold" :class="qualityColor(translation.quality)">
          {{ translation.quality > 0 ? `${translation.quality}점` : '-' }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">비용</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ translation.cost }} 크레딧
        </p>
      </div>
    </div>

    <!-- Created At -->
    <div class="flex items-center gap-1.5 border-t border-gray-100 pt-3 dark:border-gray-800">
      <ClockIcon class="h-3.5 w-3.5 text-gray-400" />
      <span class="text-xs text-gray-500 dark:text-gray-400">
        {{ formatDate(translation.createdAt) }}
      </span>
    </div>
  </div>
</template>
