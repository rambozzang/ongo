<template>
  <div
    class="flex items-center justify-between rounded-lg border border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-900"
  >
    <div class="flex items-center gap-3">
      <!-- Section type label -->
      <span
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
        :class="sectionTypeBadge"
      >
        {{ sectionTypeLabel }}
      </span>

      <!-- Title -->
      <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ section.title }}
      </span>
    </div>

    <!-- Visibility toggle -->
    <button
      class="relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer items-center rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
      :class="section.isVisible ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
      role="switch"
      :aria-checked="section.isVisible"
      @click="$emit('toggleVisibility', section.id)"
    >
      <span
        class="inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform duration-200"
        :class="section.isVisible ? 'translate-x-6' : 'translate-x-1'"
      />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PortfolioSection } from '@/types/portfolioBuilder'

const props = defineProps<{
  section: PortfolioSection
}>()

defineEmits<{
  toggleVisibility: [id: number]
}>()

const sectionTypeConfig = computed(() => {
  const configs: Record<PortfolioSection['sectionType'], { label: string; color: string }> = {
    INTRO: {
      label: '소개',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    STATS: {
      label: '통계',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    VIDEOS: {
      label: '영상',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
    BRANDS: {
      label: '브랜드',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300',
    },
    TESTIMONIALS: {
      label: '후기',
      color: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300',
    },
    CONTACT: {
      label: '연락처',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
  }
  return configs[props.section.sectionType] ?? configs.CONTACT
})

const sectionTypeBadge = computed(() => sectionTypeConfig.value.color)
const sectionTypeLabel = computed(() => sectionTypeConfig.value.label)
</script>
