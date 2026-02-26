<script setup lang="ts">
import { ref } from 'vue'
import {
  DocumentDuplicateIcon,
  ChevronDownIcon,
  ChevronUpIcon,
} from '@heroicons/vue/24/outline'
import type { ContentVersionGroup } from '@/types/contentVersioning'
import VersionItem from './VersionItem.vue'

defineProps<{
  group: ContentVersionGroup
}>()

const isExpanded = ref(false)

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header -->
    <div
      class="flex cursor-pointer items-center justify-between p-4 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800/50"
      @click="isExpanded = !isExpanded"
    >
      <div class="min-w-0 flex-1">
        <div class="mb-1.5 flex flex-wrap items-center gap-2">
          <DocumentDuplicateIcon class="h-4 w-4 text-primary-500" />
          <span class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">
            {{ group.contentTitle }}
          </span>
          <span
            :class="[getPlatformStyle(group.platform).bg, getPlatformStyle(group.platform).text]"
            class="rounded-full px-2 py-0.5 text-xs font-medium"
          >
            {{ group.platform }}
          </span>
        </div>
        <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
          <span>총 <span class="font-semibold text-gray-900 dark:text-gray-100">{{ group.totalVersions }}</span>개 버전</span>
          <span>최신 <span class="font-semibold text-primary-600 dark:text-primary-400">v{{ group.latestVersion }}</span></span>
        </div>
      </div>
      <component
        :is="isExpanded ? ChevronUpIcon : ChevronDownIcon"
        class="h-5 w-5 flex-shrink-0 text-gray-400 transition-transform"
      />
    </div>

    <!-- Version Timeline (expandable) -->
    <Transition name="expand">
      <div v-if="isExpanded" class="border-t border-gray-100 p-4 dark:border-gray-800">
        <div class="relative space-y-3">
          <!-- Timeline line -->
          <div class="absolute left-[5px] top-0 h-full w-0.5 bg-gray-200 dark:bg-gray-700" />
          <VersionItem
            v-for="ver in group.versions"
            :key="ver.id"
            :version="ver"
          />
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}
.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}
.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 2000px;
}
</style>
