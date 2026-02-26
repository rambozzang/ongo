<script setup lang="ts">
import { UserIcon } from '@heroicons/vue/24/outline'
import type { ContentVersion } from '@/types/contentVersioning'
import PerformanceCompare from './PerformanceCompare.vue'

defineProps<{
  version: ContentVersion
}>()

const changeTypeConfig: Record<string, { label: string; bg: string; text: string }> = {
  TITLE: { label: '제목', bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  THUMBNAIL: { label: '썸네일', bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-300' },
  DESCRIPTION: { label: '설명', bg: 'bg-teal-100 dark:bg-teal-900/30', text: 'text-teal-700 dark:text-teal-300' },
  TAGS: { label: '태그', bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300' },
  SUBTITLE: { label: '자막', bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  VIDEO: { label: '영상', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
}

const getChangeTypeStyle = (type: string) => changeTypeConfig[type] ?? changeTypeConfig.TITLE

const formatDate = (dateStr: string) => {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="relative pl-6">
    <!-- Timeline dot -->
    <div class="absolute left-0 top-1.5 h-3 w-3 rounded-full border-2 border-primary-500 bg-white dark:bg-gray-900" />

    <div class="rounded-lg border border-gray-100 bg-white p-3 dark:border-gray-800 dark:bg-gray-900">
      <!-- Header -->
      <div class="mb-2 flex flex-wrap items-center gap-2">
        <span class="text-xs font-bold text-primary-600 dark:text-primary-400">
          v{{ version.versionNumber }}
        </span>
        <span
          :class="[getChangeTypeStyle(version.changeType).bg, getChangeTypeStyle(version.changeType).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          {{ getChangeTypeStyle(version.changeType).label }}
        </span>
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ formatDate(version.createdAt) }}
        </span>
      </div>

      <!-- Description -->
      <p class="mb-2 text-sm text-gray-700 dark:text-gray-300">
        {{ version.changeDescription }}
      </p>

      <!-- Author -->
      <div class="mb-2 flex items-center gap-1.5">
        <UserIcon class="h-3.5 w-3.5 text-gray-400" />
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ version.createdBy }}
        </span>
      </div>

      <!-- Performance Compare -->
      <PerformanceCompare
        v-if="version.performanceBefore && version.performanceAfter"
        :before="version.performanceBefore"
        :after="version.performanceAfter"
      />
    </div>
  </div>
</template>
