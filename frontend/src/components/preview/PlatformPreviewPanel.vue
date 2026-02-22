<template>
  <div class="platform-preview-panel">
    <!-- Header with toggle -->
    <button
      class="mb-4 flex w-full items-center justify-between rounded-lg border border-gray-300 bg-white px-4 py-3 text-left transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:hover:bg-gray-700"
      @click="collapsed = !collapsed"
    >
      <div class="flex items-center gap-2">
        <svg
          class="h-5 w-5 text-primary-600 dark:text-primary-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
          />
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
          />
        </svg>
        <span class="font-semibold text-gray-900 dark:text-gray-100">{{ t('preview.platformPreview') }}</span>
        <span
          v-if="platforms.length > 0"
          class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
        >
          {{ platforms.length }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <!-- Tab/Grid toggle button -->
        <button
          v-if="platforms.length > 1"
          class="rounded-md p-1 text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-700 dark:hover:text-gray-200"
          :title="isGridView ? t('preview.switchToTab') : t('preview.switchToGrid')"
          @click.stop="isGridView = !isGridView"
        >
          <Squares2X2Icon v-if="!isGridView" class="h-5 w-5" />
          <ListBulletIcon v-else class="h-5 w-5" />
        </button>
        <svg
          class="h-5 w-5 text-gray-600 transition-transform dark:text-gray-400"
          :class="{ 'rotate-180': !collapsed }"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </button>

    <!-- Preview Content -->
    <div
      v-if="!collapsed && platforms.length > 0"
      class="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-800/50"
    >
      <!-- Comparison Grid View -->
      <div v-if="isGridView" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div
          v-for="platform in platforms"
          :key="platform"
          class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-600 dark:bg-gray-800"
        >
          <!-- Platform header with char count badge -->
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div
                class="h-2.5 w-2.5 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              />
              <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ PLATFORM_CONFIG[platform].label }}
              </span>
            </div>
            <CharCountBadge :platform="platform" :meta="getMetaForPlatform(platform)" />
          </div>
          <!-- Scaled preview -->
          <div class="origin-top-left scale-[0.85] overflow-hidden">
            <component
              :is="previewComponent(platform)"
              :title="getMetaForPlatform(platform).title"
              :description="getMetaForPlatform(platform).description"
              :thumbnail="thumbnail"
              :channel-name="channelName"
              :tags="getMetaForPlatform(platform).tags"
            />
          </div>
        </div>
      </div>

      <!-- Tab View (default) -->
      <template v-else>
        <!-- Tab Navigation -->
        <div class="mb-4 flex flex-wrap gap-2">
          <button
            v-for="platform in platforms"
            :key="platform"
            class="rounded-lg px-4 py-2 text-sm font-medium transition-all"
            :class="
              selectedPlatform === platform
                ? 'bg-white text-primary-600 shadow-sm dark:bg-gray-700 dark:text-primary-400'
                : 'text-gray-600 hover:bg-white/50 dark:text-gray-400 dark:hover:bg-gray-700/50'
            "
            @click="selectedPlatform = platform"
          >
            <div class="flex items-center gap-2">
              <div
                class="h-2 w-2 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              />
              {{ PLATFORM_CONFIG[platform].label }}
              <CharCountBadge :platform="platform" :meta="getMetaForPlatform(platform)" />
            </div>
          </button>
        </div>

        <!-- Platform Preview -->
        <div class="preview-container">
          <component
            :is="previewComponent(selectedPlatform!)"
            v-if="selectedPlatform"
            :title="getMetaForPlatform(selectedPlatform).title"
            :description="getMetaForPlatform(selectedPlatform).description"
            :thumbnail="thumbnail"
            :channel-name="channelName"
            :tags="getMetaForPlatform(selectedPlatform).tags"
          />
        </div>
      </template>

      <!-- Info text -->
      <div class="mt-4 flex items-start gap-2 rounded-lg bg-blue-50 p-3 dark:bg-blue-900/20">
        <svg
          class="mt-0.5 h-5 w-5 flex-shrink-0 text-blue-600 dark:text-blue-400"
          fill="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"
          />
        </svg>
        <p class="text-sm text-blue-800 dark:text-blue-300">
          {{ t('preview.platformInfo') }}
        </p>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="!collapsed && platforms.length === 0"
      class="rounded-lg border border-gray-200 bg-gray-50 p-8 text-center dark:border-gray-700 dark:bg-gray-800/50"
    >
      <svg
        class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
        />
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"
        />
      </svg>
      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ t('preview.selectPlatform') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { Squares2X2Icon, ListBulletIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import YouTubePreview from './YouTubePreview.vue'
import TikTokPreview from './TikTokPreview.vue'
import InstagramPreview from './InstagramPreview.vue'
import NaverClipPreview from './NaverClipPreview.vue'
import CharCountBadge from './CharCountBadge.vue'

interface PlatformMeta {
  title: string
  description: string
  tags: string[]
}

interface Props {
  title?: string
  description?: string
  thumbnail?: string
  channelName?: string
  tags?: string[]
  platforms: Platform[]
  platformMetadata?: Partial<Record<Platform, PlatformMeta>>
  comparisonMode?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  description: '',
  thumbnail: '',
  channelName: '',
  tags: () => [],
  platforms: () => [],
  platformMetadata: undefined,
  comparisonMode: false,
})

const collapsed = ref(false)
const selectedPlatform = ref<Platform | null>(null)
const isGridView = ref(props.comparisonMode)

// Resolve the platform-specific metadata (custom overrides base)
function getMetaForPlatform(platform: Platform): PlatformMeta {
  const custom = props.platformMetadata?.[platform]
  return {
    title: custom?.title || props.title || '',
    description: custom?.description || props.description || '',
    tags: custom?.tags || props.tags || [],
  }
}

// Map platform to preview component
const platformComponents: Partial<Record<Platform, Component>> = {
  YOUTUBE: YouTubePreview,
  TIKTOK: TikTokPreview,
  INSTAGRAM: InstagramPreview,
  NAVER_CLIP: NaverClipPreview,
}

function previewComponent(platform: Platform): Component {
  return platformComponents[platform] ?? YouTubePreview
}

// Auto-select first platform when platforms change
watch(
  () => props.platforms,
  (newPlatforms) => {
    if (newPlatforms.length > 0) {
      // If current selection is not in new platforms, switch to first
      if (!selectedPlatform.value || !newPlatforms.includes(selectedPlatform.value)) {
        selectedPlatform.value = newPlatforms[0]
      }
    } else {
      selectedPlatform.value = null
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.platform-preview-panel {
  @apply w-full;
}

.preview-container {
  @apply w-full;
}
</style>
