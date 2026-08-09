<template>
  <div class="platform-preview-panel">
    <!-- Header with toggle -->
    <div class="mb-4 flex w-full items-center justify-between rounded-lg border border-line-control bg-surface-card px-4 py-3 transition-colors hover:bg-surface-raised">
      <button
        type="button"
        :aria-expanded="!collapsed"
        class="flex min-h-11 min-w-0 flex-1 items-center gap-2 text-left"
        @click="collapsed = !collapsed"
      >
        <svg class="h-5 w-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
        </svg>
        <span class="font-semibold text-content">{{ t('preview.platformPreview') }}</span>
        <span v-if="platforms.length > 0" class="rounded-full bg-accent-dim px-2 py-0.5 text-body-xs font-semibold text-accent">
          {{ platforms.length }}
        </span>
      </button>
      <div class="flex items-center gap-2">
        <!-- Tab/Grid toggle button -->
        <button
          v-if="platforms.length > 1"
          type="button"
          class="min-h-11 min-w-11 rounded-md p-1 text-content-tertiary transition-colors hover:bg-surface-raised hover:text-content"
          :aria-label="isGridView ? t('preview.switchToTab') : t('preview.switchToGrid')"
          :title="isGridView ? t('preview.switchToTab') : t('preview.switchToGrid')"
          @click.stop="isGridView = !isGridView"
        >
          <Squares2X2Icon v-if="!isGridView" class="h-5 w-5" />
          <ListBulletIcon v-else class="h-5 w-5" />
        </button>
        <svg
          class="h-5 w-5 text-content-tertiary transition-transform"
          :class="{ 'rotate-180': !collapsed }"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
        </svg>
      </div>
    </div>

    <!-- Preview Content -->
    <div
      v-if="!collapsed && platforms.length > 0"
      class="rounded-lg border border-line bg-surface-raised p-4"
    >
      <!-- Comparison Grid View -->
      <div v-if="isGridView" class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div
          v-for="platform in platforms"
          :key="platform"
          class="rounded-lg border border-line-control bg-surface-card p-3"
        >
          <!-- Platform header with char count badge -->
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div
                class="h-2.5 w-2.5 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              />
                <span class="text-body font-semibold text-content">
                {{ PLATFORM_CONFIG[platform].label }}
              </span>
            </div>
            <CharCountBadge
              :platform="platform"
              :meta="getMetaForPlatform(platform)"
              :limits="props.platformLimits?.[platform]"
            />
          </div>
          <!-- Scaled preview -->
          <div class="origin-top-left scale-[0.85] overflow-hidden">
            <component
              :is="previewComponent(platform)"
              :platform="platform"
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
            type="button"
            :aria-pressed="selectedPlatform === platform"
            class="rounded-lg px-4 py-2 text-body font-medium transition-all"
            :class="
                selectedPlatform === platform
                ? 'bg-surface-card text-accent shadow-sm'
                : 'text-content-secondary hover:bg-surface-card/50'
            "
            @click="selectedPlatform = platform"
          >
            <div class="flex items-center gap-2">
              <div
                class="h-2 w-2 rounded-full"
                :style="{ backgroundColor: PLATFORM_CONFIG[platform].color }"
              />
              {{ PLATFORM_CONFIG[platform].label }}
              <CharCountBadge
                :platform="platform"
                :meta="getMetaForPlatform(platform)"
                :limits="props.platformLimits?.[platform]"
              />
            </div>
          </button>
        </div>

        <!-- Platform Preview -->
        <div class="preview-container">
          <component
            :is="previewComponent(selectedPlatform!)"
            v-if="selectedPlatform"
            :platform="selectedPlatform"
            :title="getMetaForPlatform(selectedPlatform).title"
            :description="getMetaForPlatform(selectedPlatform).description"
            :thumbnail="thumbnail"
            :channel-name="channelName"
            :tags="getMetaForPlatform(selectedPlatform).tags"
          />
        </div>
      </template>

      <!-- Info text -->
      <div class="mt-4 flex items-start gap-2 rounded-lg bg-info-subtle p-3">
        <svg
          class="mt-0.5 h-5 w-5 flex-shrink-0 text-info-strong"
          fill="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"
          />
        </svg>
        <p class="text-body text-info-strong">
          {{ t('preview.platformInfo') }}
        </p>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="!collapsed && platforms.length === 0"
      class="rounded-lg border border-line bg-surface-raised p-8 text-center"
    >
      <svg
        class="mx-auto mb-3 h-12 w-12 text-content-quaternary"
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
      <p class="text-body text-content-secondary">
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
import GenericPlatformPreview from './GenericPlatformPreview.vue'

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
  platformLimits?: Partial<Record<Platform, { title?: number; description?: number; tags?: number }>>
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
  platformLimits: undefined,
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
  return platformComponents[platform] ?? GenericPlatformPreview
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
