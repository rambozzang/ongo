<script setup lang="ts">
import { computed } from 'vue'
import {
  LanguageIcon,
  DocumentTextIcon,
} from '@heroicons/vue/24/outline'
import type { TranslationJob } from '@/types/contentTranslator'
import TranslationStatusBadge from './TranslationStatusBadge.vue'

const props = defineProps<{
  job: TranslationJob
}>()

const contentTypeLabels: Record<string, string> = {
  TITLE: '제목',
  DESCRIPTION: '설명',
  TAGS: '태그',
  SUBTITLE: '자막',
  ALL: '전체',
}

const contentTypeLabel = computed(() => contentTypeLabels[props.job.contentType] ?? props.job.contentType)

const languagePair = computed(() => `${props.job.sourceLanguage} → ${props.job.targetLanguage}`)

const qualityColor = computed(() => {
  const q = props.job.quality
  if (q === null) return 'text-gray-400 dark:text-gray-500'
  if (q >= 90) return 'text-green-600 dark:text-green-400'
  if (q >= 70) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const formatDate = (iso: string) => {
  try {
    return new Date(iso).toLocaleDateString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-200 hover:shadow-lg dark:border-gray-700 dark:bg-gray-900 dark:hover:shadow-gray-900/50">
    <!-- Header: Video title + Status -->
    <div class="mb-3 flex items-start justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-1">
        {{ job.videoTitle }}
      </h3>
      <TranslationStatusBadge :status="job.status" />
    </div>

    <!-- Language pair & Content type -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span class="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        <LanguageIcon class="h-3 w-3" />
        {{ languagePair }}
      </span>
      <span class="inline-flex items-center gap-1 rounded-full bg-purple-50 px-2 py-0.5 text-xs font-medium text-purple-700 dark:bg-purple-900/30 dark:text-purple-300">
        <DocumentTextIcon class="h-3 w-3" />
        {{ contentTypeLabel }}
      </span>
    </div>

    <!-- Original text preview -->
    <div class="mb-3 rounded-lg bg-gray-50 p-2.5 dark:bg-gray-800/50">
      <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">원문</p>
      <p class="text-sm text-gray-800 dark:text-gray-200 line-clamp-2">{{ job.originalText }}</p>
    </div>

    <!-- Translated text preview -->
    <div v-if="job.translatedText" class="mb-3 rounded-lg bg-green-50 p-2.5 dark:bg-green-900/10">
      <p class="text-xs text-gray-500 dark:text-gray-400 mb-1">번역</p>
      <p class="text-sm text-gray-800 dark:text-gray-200 line-clamp-2">{{ job.translatedText }}</p>
    </div>

    <!-- Footer: Quality score + Date -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div v-if="job.quality !== null" class="flex items-center gap-1">
        <span class="text-xs text-gray-500 dark:text-gray-400">품질:</span>
        <span :class="qualityColor" class="text-sm font-semibold">{{ job.quality }}점</span>
      </div>
      <span v-else class="text-xs text-gray-400 dark:text-gray-500">품질 평가 대기</span>
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatDate(job.createdAt) }}
      </span>
    </div>
  </div>
</template>
