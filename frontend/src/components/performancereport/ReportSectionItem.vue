<script setup lang="ts">
import { computed } from 'vue'
import {
  ChartBarIcon,
  HeartIcon,
  StarIcon,
  LightBulbIcon,
  ClipboardDocumentListIcon,
  CpuChipIcon,
} from '@heroicons/vue/24/outline'
import type { ReportSection } from '@/types/performanceReport'

interface Props {
  section: ReportSection
}

const props = defineProps<Props>()

const sectionIcon = computed(() => {
  const icons: Record<string, typeof ChartBarIcon> = {
    OVERVIEW: ClipboardDocumentListIcon,
    ENGAGEMENT: HeartIcon,
    TOP_CONTENT: StarIcon,
    RECOMMENDATION: LightBulbIcon,
    ANALYTICS: ChartBarIcon,
    AI_INSIGHTS: CpuChipIcon,
  }
  return icons[props.section.sectionType] ?? ClipboardDocumentListIcon
})

const iconColor = computed(() => {
  const colors: Record<string, string> = {
    OVERVIEW: 'text-blue-500 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30',
    ENGAGEMENT: 'text-pink-500 dark:text-pink-400 bg-pink-100 dark:bg-pink-900/30',
    TOP_CONTENT: 'text-yellow-500 dark:text-yellow-400 bg-yellow-100 dark:bg-yellow-900/30',
    RECOMMENDATION: 'text-green-500 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
    ANALYTICS: 'text-purple-500 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30',
    AI_INSIGHTS: 'text-indigo-500 dark:text-indigo-400 bg-indigo-100 dark:bg-indigo-900/30',
  }
  return colors[props.section.sectionType] ?? 'text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800'
})
</script>

<template>
  <div
    class="flex items-start gap-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
  >
    <!-- Icon -->
    <div
      :class="['flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center', iconColor]"
    >
      <component :is="sectionIcon" class="w-5 h-5" />
    </div>

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ section.title }}
      </h4>
      <p class="mt-1 text-sm text-gray-600 dark:text-gray-400 whitespace-pre-line">
        {{ section.content }}
      </p>
    </div>
  </div>
</template>
