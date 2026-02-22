<template>
  <div class="card">
    <!-- Header -->
    <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
      {{ t('video.mediaInfo.title') }}
    </h3>

    <!-- Loading State -->
    <div v-if="!mediaInfo" class="flex flex-col items-center justify-center py-12">
      <div class="mb-3 h-8 w-8 animate-spin rounded-full border-2 border-gray-300 border-t-primary-600 dark:border-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.analyzing') }}</p>
    </div>

    <!-- Media Info Content -->
    <div v-else class="space-y-6">
      <!-- Video Track -->
      <div>
        <h4 class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
          <FilmIcon class="h-4 w-4 text-blue-500" />
          {{ t('video.mediaInfo.videoTrack') }}
        </h4>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3">
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.codec') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.videoCodec ?? '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.resolution') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ resolutionText }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.fps') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.fps ? `${mediaInfo.fps} fps` : '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.bitrate') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ videoBitrateText }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.duration') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ durationText }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.profile') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.profile ?? '-' }}</p>
          </div>
        </div>
      </div>

      <!-- Audio Track -->
      <div>
        <h4 class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
          <SpeakerWaveIcon class="h-4 w-4 text-green-500" />
          {{ t('video.mediaInfo.audioTrack') }}
        </h4>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3">
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.audioCodec') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.audioCodec ?? '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.audioBitrate') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ audioBitrateText }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.sampleRate') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ sampleRateText }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.channels') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ channelsText }}</p>
          </div>
        </div>
      </div>

      <!-- Color Info -->
      <div>
        <h4 class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
          <SwatchIcon class="h-4 w-4 text-purple-500" />
          {{ t('video.mediaInfo.colorInfo') }}
        </h4>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3">
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.colorSpace') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.colorSpace ?? '-' }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
            <p class="text-xs text-gray-500 dark:text-gray-400">{{ t('video.mediaInfo.pixelFormat') }}</p>
            <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ mediaInfo.pixelFormat ?? '-' }}</p>
          </div>
        </div>
      </div>

      <!-- Platform Compatibility -->
      <div>
        <h4 class="mb-3 flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
          <GlobeAltIcon class="h-4 w-4 text-orange-500" />
          {{ t('video.mediaInfo.platformCompatibility') }}
        </h4>
        <div class="grid grid-cols-2 gap-3 tablet:grid-cols-4">
          <div
            v-for="platform in platforms"
            :key="platform.key"
            class="flex items-center gap-2 rounded-lg border px-3 py-2 transition-colors"
            :class="
              isPlatformCompatible(platform.key)
                ? 'border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-900/20'
                : 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
            "
          >
            <CheckCircleIcon
              v-if="isPlatformCompatible(platform.key)"
              class="h-4 w-4 flex-shrink-0 text-green-600 dark:text-green-400"
            />
            <XCircleIcon
              v-else
              class="h-4 w-4 flex-shrink-0 text-red-600 dark:text-red-400"
            />
            <span
              class="text-sm font-medium"
              :class="
                isPlatformCompatible(platform.key)
                  ? 'text-green-700 dark:text-green-300'
                  : 'text-red-700 dark:text-red-300'
              "
            >
              {{ platform.label }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  FilmIcon,
  SpeakerWaveIcon,
  SwatchIcon,
  GlobeAltIcon,
  CheckCircleIcon,
  XCircleIcon,
} from '@heroicons/vue/24/outline'
import type { VideoMediaInfo } from '@/types/video'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

// ---- i18n ----

const { t } = useI18n()

// ---- Props ----

const props = defineProps<{
  mediaInfo: VideoMediaInfo | null
}>()

// ---- Constants ----

/** Maximum supported resolution per platform for compatibility check */
const PLATFORM_MAX_RESOLUTION: Partial<Record<Platform, { width: number; height: number }>> = {
  YOUTUBE: { width: 3840, height: 2160 },
  TIKTOK: { width: 1080, height: 1920 },
  INSTAGRAM: { width: 1080, height: 1920 },
  NAVER_CLIP: { width: 1080, height: 1920 },
}

const platforms = Object.entries(PLATFORM_CONFIG).map(([key, config]) => ({
  key: key as Platform,
  label: config.label,
}))

// ---- Computed ----

const resolutionText = computed(() => {
  if (!props.mediaInfo?.width || !props.mediaInfo?.height) return '-'
  return `${props.mediaInfo.width} x ${props.mediaInfo.height}`
})

const videoBitrateText = computed(() => {
  if (!props.mediaInfo?.bitrateKbps) return '-'
  return formatBitrateKbps(props.mediaInfo.bitrateKbps)
})

const audioBitrateText = computed(() => {
  if (!props.mediaInfo?.audioBitrateKbps) return '-'
  return formatBitrateKbps(props.mediaInfo.audioBitrateKbps)
})

const durationText = computed(() => {
  if (!props.mediaInfo?.durationMs) return '-'
  return formatDuration(props.mediaInfo.durationMs / 1000)
})

const sampleRateText = computed(() => {
  if (!props.mediaInfo?.sampleRate) return '-'
  return `${(props.mediaInfo.sampleRate / 1000).toFixed(1)} kHz`
})

const channelsText = computed(() => {
  if (!props.mediaInfo?.audioChannels) return '-'
  if (props.mediaInfo.audioChannels === 1) return 'Mono'
  if (props.mediaInfo.audioChannels === 2) return 'Stereo'
  return `${props.mediaInfo.audioChannels}ch`
})

// ---- Methods ----

/** Check if video resolution is compatible with a specific platform */
function isPlatformCompatible(platform: Platform): boolean {
  if (!props.mediaInfo?.width || !props.mediaInfo?.height) return false
  const limit = PLATFORM_MAX_RESOLUTION[platform]
  if (!limit) return false
  return props.mediaInfo.width <= limit.width && props.mediaInfo.height <= limit.height
}

/** Format kbps value to human-readable string */
function formatBitrateKbps(kbps: number): string {
  if (kbps >= 1000) {
    return `${(kbps / 1000).toFixed(1)} Mbps`
  }
  return `${kbps} kbps`
}

/** Format seconds to HH:MM:SS or MM:SS */
function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${m}:${String(s).padStart(2, '0')}`
}
</script>
