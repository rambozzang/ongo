<template>
  <div class="card">
    <!-- Header -->
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('video.processing.title') }}
      </h3>
      <span
        v-if="connected"
        class="inline-flex items-center gap-1.5 rounded-full bg-green-100 px-2.5 py-1 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-400"
      >
        <span class="h-1.5 w-1.5 animate-pulse rounded-full bg-green-500" />
        {{ t('video.processing.live') }}
      </span>
      <span
        v-else-if="connectionError"
        class="inline-flex items-center gap-1.5 rounded-full bg-red-100 px-2.5 py-1 text-xs font-medium text-red-700 dark:bg-red-900/30 dark:text-red-400"
      >
        {{ t('video.processing.disconnected') }}
      </span>
    </div>

    <!-- Overall Progress -->
    <div v-if="stages.length > 0" class="mb-6">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ t('video.processing.overall') }}
        </span>
        <span class="text-sm font-semibold text-primary-600 dark:text-primary-400">
          {{ overallPercent }}%
        </span>
      </div>
      <div class="h-3 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          class="h-full rounded-full bg-primary-500 transition-all duration-500 ease-out"
          :style="{ width: `${overallPercent}%` }"
        />
      </div>
      <p v-if="estimatedTimeRemaining" class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">
        {{ t('video.processing.estimatedTime', { time: estimatedTimeRemaining }) }}
      </p>
    </div>

    <!-- Stage List -->
    <div class="space-y-4">
      <div
        v-for="stage in stages"
        :key="stageKey(stage)"
        class="rounded-lg border border-gray-200 p-4 transition-colors dark:border-gray-700"
        :class="stageContainerClass(stage)"
      >
        <!-- Stage Header -->
        <div class="mb-2 flex items-center justify-between">
          <div class="flex items-center gap-2">
            <!-- Stage Icon -->
            <component
              :is="stageIcon(stage.stage)"
              class="h-4 w-4"
              :class="stageIconColor(stage)"
            />
            <!-- Stage Label -->
            <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ stageLabel(stage) }}
            </span>
            <!-- Platform Badge -->
            <PlatformBadge
              v-if="stage.platform"
              :platform="stage.platform"
              class="scale-90"
            />
          </div>
          <!-- Percentage -->
          <span
            class="text-sm font-semibold"
            :class="stagePercentColor(stage)"
          >
            {{ stage.progressPercent }}%
          </span>
        </div>

        <!-- Progress Bar -->
        <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full transition-all duration-500 ease-out"
            :class="stageBarColor(stage)"
            :style="{ width: `${stage.progressPercent}%` }"
          />
        </div>

        <!-- Status Message -->
        <p
          v-if="stage.message"
          class="mt-1.5 text-xs text-gray-500 dark:text-gray-400"
        >
          {{ stage.message }}
        </p>
      </div>
    </div>

    <!-- Empty State (no stages yet) -->
    <div
      v-if="stages.length === 0 && !connectionError"
      class="flex flex-col items-center justify-center py-12"
    >
      <div class="mb-3 h-8 w-8 animate-spin rounded-full border-2 border-gray-300 border-t-primary-600 dark:border-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ t('video.processing.waiting') }}
      </p>
    </div>

    <!-- Connection Error State -->
    <div
      v-if="connectionError && stages.length === 0"
      class="flex flex-col items-center justify-center py-12"
    >
      <ExclamationTriangleIcon class="mb-3 h-10 w-10 text-red-400" />
      <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
        {{ t('video.processing.connectionError') }}
      </p>
      <button
        class="btn-secondary inline-flex items-center gap-1.5 text-sm"
        @click="reconnect"
      >
        <ArrowPathIcon class="h-4 w-4" />
        {{ t('action.retry') }}
      </button>
    </div>

    <!-- All Complete State -->
    <div
      v-if="allComplete && stages.length > 0"
      class="mt-4 flex items-center gap-2 rounded-lg bg-green-50 px-4 py-3 dark:bg-green-900/20"
    >
      <CheckCircleIcon class="h-5 w-5 text-green-500" />
      <p class="text-sm font-medium text-green-700 dark:text-green-400">
        {{ t('video.processing.complete') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  MagnifyingGlassIcon,
  PhotoIcon,
  LanguageIcon,
  CloudArrowUpIcon,
  ExclamationTriangleIcon,
  ArrowPathIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import type { VideoProcessingProgress, ProcessingStage } from '@/types/video'
import { authApi } from '@/api/auth'
import PlatformBadge from '@/components/common/PlatformBadge.vue'

// ---- i18n ----

const { t } = useI18n({ useScope: 'global' })

// ---- Props ----

const props = defineProps<{
  videoId: number
}>()

// ---- State ----

const stages = ref<VideoProcessingProgress[]>([])
const connected = ref(false)
const connectionError = ref(false)
const startTime = ref<number | null>(null)

let eventSource: EventSource | null = null
let reconnectTimeout: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5

// ---- Computed ----

/** Overall progress across all stages */
const overallPercent = computed(() => {
  if (stages.value.length === 0) return 0
  const total = stages.value.reduce((sum, s) => sum + s.progressPercent, 0)
  return Math.round(total / stages.value.length)
})

/** Whether all stages are at 100% */
const allComplete = computed(() => {
  return stages.value.length > 0 && stages.value.every((s) => s.progressPercent >= 100)
})

/** Estimated time remaining based on progress rate */
const estimatedTimeRemaining = computed(() => {
  if (!startTime.value || overallPercent.value === 0 || overallPercent.value >= 100) return null

  const elapsed = (Date.now() - startTime.value) / 1000
  const rate = overallPercent.value / elapsed
  if (rate <= 0) return null

  const remainingPercent = 100 - overallPercent.value
  const remainingSeconds = remainingPercent / rate

  return formatTimeRemaining(remainingSeconds)
})

// ---- Methods ----

/** Connect to SSE endpoint — SSE 전용 단기 토큰 사용 */
async function connectSSE() {
  if (eventSource) {
    eventSource.close()
  }

  connectionError.value = false
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

  // SSE 전용 단기 토큰 발급 (5분 만료, URL 노출에 안전)
  let sseToken: string | null = null
  try {
    const result = await authApi.getSseToken()
    sseToken = result.token
  } catch {
    // 토큰 발급 실패 시 연결 불가
    connectionError.value = true
    return
  }

  const url = `${baseUrl}/videos/${props.videoId}/progress?token=${encodeURIComponent(sseToken)}`

  eventSource = new EventSource(url)

  eventSource.onopen = () => {
    connected.value = true
    connectionError.value = false
    reconnectAttempts = 0
    if (!startTime.value) {
      startTime.value = Date.now()
    }
  }

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)
      if (Array.isArray(data)) {
        (data as VideoProcessingProgress[]).forEach(updateStage)
      } else {
        updateStage(data as VideoProcessingProgress)
      }
    } catch {
      // Silently ignore malformed messages
    }
  }

  eventSource.addEventListener('progress', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      if (Array.isArray(data)) {
        (data as VideoProcessingProgress[]).forEach(updateStage)
      } else {
        updateStage(data as VideoProcessingProgress)
      }
    } catch {
      // Silently ignore malformed messages
    }
  })

  eventSource.addEventListener('complete', () => {
    connected.value = false
    eventSource?.close()
    eventSource = null
  })

  eventSource.onerror = () => {
    connected.value = false

    if (allComplete.value) {
      // Processing finished, no need to reconnect
      eventSource?.close()
      eventSource = null
      return
    }

    // Attempt reconnection with exponential backoff
    if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
      const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
      reconnectAttempts++
      reconnectTimeout = setTimeout(() => {
        connectSSE()
      }, delay)
    } else {
      connectionError.value = true
      eventSource?.close()
      eventSource = null
    }
  }
}

/** Update or insert a stage in the stages list */
function updateStage(progress: VideoProcessingProgress) {
  const key = progress.platform
    ? `${progress.stage}-${progress.platform}`
    : progress.stage

  const idx = stages.value.findIndex((s) => {
    const existingKey = s.platform
      ? `${s.stage}-${s.platform}`
      : s.stage
    return existingKey === key
  })

  if (idx >= 0) {
    stages.value[idx] = progress
  } else {
    stages.value.push(progress)
    // Sort stages in logical order
    stages.value.sort((a, b) => STAGE_ORDER[a.stage] - STAGE_ORDER[b.stage])
  }
}

/** Manual reconnect */
function reconnect() {
  reconnectAttempts = 0
  connectionError.value = false
  connectSSE()
}

/** Generate a unique key for a stage */
function stageKey(stage: VideoProcessingProgress): string {
  return stage.platform ? `${stage.stage}-${stage.platform}` : stage.stage
}

/** Get the display label for a processing stage */
function stageLabel(stage: VideoProcessingProgress): string {
  const labels: Record<ProcessingStage, string> = {
    PROBE: t('video.processing.stages.probe'),
    THUMBNAIL: t('video.processing.stages.thumbnail'),
    CAPTION: t('video.processing.stages.caption'),
    UPLOAD: t('video.processing.stages.upload'),
  }
  return labels[stage.stage] || stage.stage
}

/** Get the icon component for a processing stage */
function stageIcon(stage: ProcessingStage): Component {
  const icons: Record<ProcessingStage, Component> = {
    PROBE: MagnifyingGlassIcon,
    THUMBNAIL: PhotoIcon,
    CAPTION: LanguageIcon,
    UPLOAD: CloudArrowUpIcon,
  }
  return icons[stage] || CloudArrowUpIcon
}

/** Stage icon color based on progress */
function stageIconColor(stage: VideoProcessingProgress): string {
  if (stage.progressPercent >= 100) return 'text-green-500'
  if (stage.progressPercent > 0) return 'text-primary-500'
  return 'text-gray-400 dark:text-gray-500'
}

/** Stage container styling */
function stageContainerClass(stage: VideoProcessingProgress): string {
  if (stage.progressPercent >= 100) {
    return 'bg-green-50/50 dark:bg-green-900/10'
  }
  if (stage.progressPercent > 0) {
    return 'bg-primary-50/30 dark:bg-primary-900/10'
  }
  return ''
}

/** Stage percentage text color */
function stagePercentColor(stage: VideoProcessingProgress): string {
  if (stage.progressPercent >= 100) return 'text-green-600 dark:text-green-400'
  if (stage.progressPercent > 0) return 'text-primary-600 dark:text-primary-400'
  return 'text-gray-400 dark:text-gray-500'
}

/** Stage progress bar color */
function stageBarColor(stage: VideoProcessingProgress): string {
  if (stage.progressPercent >= 100) return 'bg-green-500'
  return 'bg-primary-500'
}

/** Format remaining time in seconds to a readable string */
function formatTimeRemaining(seconds: number): string {
  if (seconds < 60) {
    return t('video.processing.timeSeconds', { seconds: Math.ceil(seconds) })
  }
  const minutes = Math.ceil(seconds / 60)
  return t('video.processing.timeMinutes', { minutes })
}

// ---- Constants ----

/** Ordering of stages for display sorting */
const STAGE_ORDER: Record<ProcessingStage, number> = {
  PROBE: 0,
  THUMBNAIL: 1,
  CAPTION: 2,
  UPLOAD: 3,
}

// ---- Lifecycle ----

onMounted(() => {
  connectSSE()
})

onUnmounted(() => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (reconnectTimeout) {
    clearTimeout(reconnectTimeout)
    reconnectTimeout = null
  }
})
</script>
