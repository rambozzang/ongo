<template>
  <div>
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ t('upload.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ t('upload.description') }}</p>
      </div>
      <div class="flex items-center gap-3">
        <!-- Queue button -->
        <button
          v-if="uploadQueueStore.queue.length > 0"
          class="btn-secondary inline-flex items-center gap-2"
          @click="queuePanelOpen = true"
        >
          <QueueListIcon class="h-5 w-5" />
          {{ t('upload.queue') }}
          <span class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
            {{ uploadQueueStore.queue.length }}
          </span>
        </button>
      </div>
    </div>

    <PageGuide :title="$t('upload.pageGuideTitle')" :items="($tm('upload.pageGuide') as string[])" />

    <!-- Step indicator -->
    <nav aria-label="업로드 단계" class="mb-8 flex items-center justify-center gap-4">
      <div v-for="(label, i) in stepLabels" :key="i" class="flex items-center gap-2">
        <div
          class="flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium"
          :class="
            i + 1 < step
              ? 'bg-green-500 text-white'
              : i + 1 === step
                ? 'bg-primary-600 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
          "
          :aria-current="i + 1 === step ? 'step' : undefined"
          :aria-label="`${i + 1}단계: ${label}${i + 1 < step ? ' (완료)' : ''}`"
          role="listitem"
        >
          <CheckIcon v-if="i + 1 < step" class="h-4 w-4" />
          <span v-else>{{ i + 1 }}</span>
        </div>
        <span
          class="hidden text-sm tablet:inline"
          :class="i + 1 <= step ? 'text-primary-600 font-medium' : 'text-gray-400 dark:text-gray-500'"
        >
          {{ label }}
        </span>
        <span v-if="i < 2" class="hidden h-px w-8 tablet:block" :class="i + 1 < step ? 'bg-green-400' : 'bg-gray-300 dark:bg-gray-600'" />
      </div>
    </nav>

    <!-- Step 1: File Upload -->
    <div v-if="step === 1" class="card">
      <FileDropzone
        v-if="!uploadStore.file"
        @select="handleFileSelect"
        @select-multiple="handleMultipleFileSelect"
        @select-images="handleImageSelect"
        @error="(msg) => notify.error(msg)"
      />

      <!-- Video upload progress -->
      <UploadProgress
        v-else-if="!uploadStore.isImage"
        :file-name="uploadStore.file.name"
        :progress="uploadStore.progress"
        :uploading="uploadStore.uploading"
        :completed="uploadCompleted"
        :error="uploadStore.uploadError"
        @pause="uploadStore.pauseUpload()"
        @resume="uploadStore.resumeUpload()"
        @cancel="handleUploadCancel"
      />

      <!-- Image story builder -->
      <StoryBuilder
        v-else
        :images="uploadStore.imageFiles"
        @update:images="(files) => (uploadStore.imageFiles = files)"
        @remove="handleImageRemove"
        @add="handleImageAdd"
      />

      <div class="mt-6 flex justify-end">
        <button
          :disabled="!uploadCompleted"
          class="btn-primary"
          @click="step = 2"
        >
          {{ t('upload.nextStep') }}
        </button>
      </div>
    </div>

    <!-- Bulk Upload Queue -->
    <BulkUploadQueue v-if="step === 1" />

    <!-- Step 2: Metadata -->
    <div v-if="step === 2" class="space-y-6">
      <!-- AI Pipeline Toggle (video only) -->
      <div v-if="!uploadStore.isImage" class="flex items-center gap-3">
        <button
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            !pipelineMode
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          ]"
          @click="pipelineMode = false"
        >
          {{ t('upload.metaInput') }}
        </button>
        <button
          :class="[
            'inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            pipelineMode
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          ]"
          @click="pipelineMode = true"
        >
          <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15.3M14.25 3.104c.251.023.501.05.75.082M19.8 15.3l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23.693L5 14.5m14.8.8l1.402 1.402c1.232 1.232.65 3.318-1.067 3.611A48.309 48.309 0 0112 21c-2.773 0-5.491-.235-8.135-.687-1.718-.293-2.3-2.379-1.067-3.61L5 14.5" />
          </svg>
          {{ t('upload.aiPipeline') }}
        </button>
      </div>

      <!-- AI Pipeline Builder / Progress (video only) -->
      <div v-if="pipelineMode && !uploadStore.isImage" class="card">
        <AiPipelineProgress
          v-if="pipelineId"
          :pipeline-id="pipelineId"
          @completed="handlePipelineCompleted"
          @cancelled="handlePipelineCancelled"
        />
        <AiPipelineBuilder
          v-else
          :video-id="videoId ?? 0"
          :channel-id="channelStore.channels.length > 0 ? channelStore.channels[0].id : undefined"
          :credit-balance="creditStore.totalBalance"
          @start="handleStartPipeline"
        />
      </div>

      <div v-if="!pipelineMode || uploadStore.isImage" class="card">
        <MetadataForm
          v-model="metadata"
          :video-preview-url="videoPreviewUrl"
          :thumbnail-candidates="thumbnailCandidates"
          :ai-generating="aiGenerating"
          :use-stt="useStt"
          :has-enough-credits="creditStore.hasEnoughCredits(useStt ? 15 : 5)"
          @update:thumbnail-url="(url) => (metadata.thumbnailUrl = url)"
          @update:use-stt="(v) => (useStt = v)"
          @generate-ai="handleAiGenerate"
        />
      </div>

      <!-- Platform Preview Panel -->
      <PlatformPreviewPanel
        v-if="channelStore.connectedPlatforms.length > 0"
        :title="metadata.title"
        :description="metadata.description"
        :thumbnail="metadata.thumbnailUrl || videoPreviewUrl || undefined"
        :channel-name="channelStore.channels[0]?.channelName"
        :tags="metadata.tags"
        :platforms="channelStore.connectedPlatforms"
      />

      <!-- Optimization Suggestions -->
      <OptimizationSuggestionList
        v-if="channelStore.connectedPlatforms.length > 0"
        :results="optimizationResults"
        :loading="optimizationLoading"
        @apply-all="handleApplyOptimization"
      />

      <div class="flex justify-between">
        <button
          class="btn-secondary"
          @click="step = 1"
        >
          {{ t('upload.previousStep') }}
        </button>
        <button
          :disabled="!metadata.title.trim()"
          class="btn-primary"
          @click="handleStep2Next"
        >
          {{ t('upload.nextStep') }}
        </button>
      </div>
    </div>

    <!-- Step 3: Platform Publish -->
    <div v-if="step === 3" class="card">
      <PlatformSelector
        :connected-platforms="channelStore.connectedPlatforms"
        :base-title="metadata.title"
        :base-description="metadata.description"
        :base-tags="metadata.tags"
        :base-visibility="metadata.visibility"
        :scheduled-at="scheduledAt"
        :ai-schedule-suggestions="scheduleSuggestions"
        @update:platforms="(configs) => (platformConfigs = configs)"
        @update:scheduled-at="(v) => (scheduledAt = v)"
      />

      <!-- Step 3 Platform Preview (comparison mode) -->
      <PlatformPreviewPanel
        v-if="platformConfigs.length > 0"
        :platforms="platformConfigs.map(c => c.platform)"
        :platform-metadata="platformConfigsAsMetadata"
        :thumbnail="metadata.thumbnailUrl || videoPreviewUrl || undefined"
        :channel-name="channelStore.channels[0]?.channelName"
        comparison-mode
        class="mt-6"
      />

      <div class="mt-6 flex justify-between">
        <button
          class="btn-secondary"
          @click="step = 2"
        >
          {{ t('upload.previousStep') }}
        </button>
        <div class="flex items-center gap-2">
          <button
            :disabled="platformConfigs.length === 0 || publishing"
            class="btn-secondary inline-flex items-center gap-2"
            @click="handleAddToQueue"
          >
            <QueueListIcon class="h-5 w-5" />
            {{ t('upload.addToQueue') }}
          </button>
          <button
            :disabled="platformConfigs.length === 0 || publishing"
            class="btn-primary inline-flex items-center gap-2"
            @click="handlePublish"
          >
            <LoadingSpinner v-if="publishing" size="sm" />
            {{ scheduledAt ? t('upload.publishScheduled') : t('upload.publishNow') }}
          </button>
        </div>
      </div>
    </div>

    <!-- Processing Progress (shown after publish starts, video only) -->
    <div v-if="publishedVideoId && !uploadStore.isImage" class="mt-6 space-y-6">
      <ProcessingProgressPanel :video-id="publishedVideoId" />
      <ThumbnailSelector
        v-if="autoThumbnails.length > 0"
        :thumbnails="autoThumbnails"
        :selected-index="selectedThumbIndex"
        :video-id="publishedVideoId"
        @select="handleThumbnailSelect"
      />
    </div>

    <!-- Upload Queue Panel -->
    <UploadQueuePanel v-model="queuePanelOpen" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { storeToRefs } from 'pinia'

import { CheckIcon } from '@heroicons/vue/24/solid'
import { QueueListIcon } from '@heroicons/vue/24/outline'
import FileDropzone from '@/components/upload/FileDropzone.vue'
import UploadProgress from '@/components/upload/UploadProgress.vue'
import StoryBuilder from '@/components/upload/StoryBuilder.vue'
import MetadataForm from '@/components/upload/MetadataForm.vue'
import PlatformSelector from '@/components/upload/PlatformSelector.vue'
import PlatformPreviewPanel from '@/components/preview/PlatformPreviewPanel.vue'
import OptimizationSuggestionList from '@/components/preview/OptimizationSuggestionList.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import BulkUploadQueue from '@/components/upload/BulkUploadQueue.vue'
import UploadQueuePanel from '@/components/upload/UploadQueuePanel.vue'
import ProcessingProgressPanel from '@/components/video/ProcessingProgressPanel.vue'
import ThumbnailSelector from '@/components/video/ThumbnailSelector.vue'
import AiPipelineBuilder from '@/components/ai/AiPipelineBuilder.vue'
import AiPipelineProgress from '@/components/ai/AiPipelineProgress.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useUploadStore } from '@/stores/upload'
import { useChannelStore } from '@/stores/channel'
import { useCreditStore } from '@/stores/credit'
import { useNotificationStore } from '@/stores/notification'
import { useUploadQueueStore } from '@/stores/uploadQueue'
import { useLocale } from '@/composables/useLocale'
import { videoApi } from '@/api/video'
import { aiApi } from '@/api/ai'
import type { Platform } from '@/types/channel'
import type { PlatformPublishConfig, OptimizationResult } from '@/types/video'
import type { ScheduleSuggestion, PipelineStepType, AiPipelineResponse } from '@/types/ai'


const uploadStore = useUploadStore()
const channelStore = useChannelStore()
const creditStore = useCreditStore()
const notify = useNotificationStore()
const uploadQueueStore = useUploadQueueStore()
const { t } = useLocale()

// step and metadata live in the store so they survive route navigation
const { step, metadata } = storeToRefs(uploadStore)

const stepLabels = computed(() =>
  uploadStore.isImage
    ? [t('upload.steps.imageSelect'), t('upload.steps.metadata'), t('upload.steps.publish')]
    : [t('upload.steps.fileSelect'), t('upload.steps.metadata'), t('upload.steps.publish')],
)

const videoId = ref<number | null>(null)
const videoPreviewUrl = ref<string | null>(null)
const thumbnailCandidates = ref<string[]>([])
const useStt = ref(false)
const aiGenerating = ref(false)
const publishing = ref(false)
const publishedVideoId = ref<number | null>(null)
const autoThumbnails = ref<string[]>([])
const selectedThumbIndex = ref(0)
const scheduledAt = ref<string | undefined>(undefined)
const scheduleSuggestions = ref<ScheduleSuggestion[]>([])
const queuePanelOpen = ref(false)
const pipelineMode = ref(false)
const pipelineId = ref<string | null>(null)
let thumbnailPollTimer: ReturnType<typeof setTimeout> | null = null

const platformConfigs = ref<PlatformPublishConfig[]>([])

const platformConfigsAsMetadata = computed(() => {
  const map: Partial<Record<Platform, { title: string; description: string; tags: string[] }>> = {}
  for (const config of platformConfigs.value) {
    map[config.platform] = {
      title: config.title,
      description: config.description,
      tags: config.tags,
    }
  }
  return map
})

// ─── Optimization check ──────────────────────────────────
const optimizationResults = ref<OptimizationResult[]>([])
const optimizationLoading = ref(false)
let optimizationDebounce: ReturnType<typeof setTimeout> | null = null

watch(
  () => [metadata.value.title, metadata.value.description, metadata.value.tags, step.value],
  () => {
    if (step.value !== 2 || !metadata.value.title.trim()) return
    if (optimizationDebounce) clearTimeout(optimizationDebounce)
    optimizationDebounce = setTimeout(() => runOptimizationCheck(), 800)
  },
  { deep: true },
)

async function runOptimizationCheck() {
  if (!metadata.value.title.trim() || channelStore.connectedPlatforms.length === 0) return
  optimizationLoading.value = true
  try {
    const result = await videoApi.optimizationCheck({
      title: metadata.value.title,
      description: metadata.value.description || undefined,
      tags: metadata.value.tags,
      thumbnailUrl: metadata.value.thumbnailUrl || undefined,
      platforms: channelStore.connectedPlatforms,
    })
    optimizationResults.value = result.results
  } catch {
    optimizationResults.value = []
  } finally {
    optimizationLoading.value = false
  }
}

function handleApplyOptimization() {
  const maxTitleLen = 100
  if (metadata.value.title.length > maxTitleLen) {
    metadata.value.title = metadata.value.title.substring(0, maxTitleLen)
  }

  if (metadata.value.description && metadata.value.description.length > 2200) {
    metadata.value.description = metadata.value.description.substring(0, 2200)
  }

  if (metadata.value.tags.length > 15) {
    metadata.value.tags = metadata.value.tags.slice(0, 15)
  }

  notify.success(t('upload.success.optimized'))
}

const uploadCompleted = computed(() => {
  if (uploadStore.isImage) {
    return uploadStore.imageFiles.length > 0
  }
  return uploadStore.progress.percentage === 100 && !uploadStore.uploading && !uploadStore.uploadError
})

onMounted(async () => {
  if (uploadStore.hasActiveSession) {
    // Returning from another page (e.g. /channels) — restore session state
    videoId.value = uploadStore.videoId
    if (uploadStore.file && !uploadStore.isImage) {
      videoPreviewUrl.value = URL.createObjectURL(uploadStore.file)
    }
  } else {
    // Fresh visit — reset everything
    uploadStore.resetUpload()
    uploadQueueStore.clearAll()
  }
  try {
    await Promise.all([
      channelStore.fetchChannels(),
      creditStore.fetchBalance(),
    ])
  } catch {
    // API failures should not block page rendering
  }
})

onUnmounted(() => {
  if (videoPreviewUrl.value) {
    URL.revokeObjectURL(videoPreviewUrl.value)
    videoPreviewUrl.value = null
  }
  if (thumbnailPollTimer) {
    clearTimeout(thumbnailPollTimer)
    thumbnailPollTimer = null
  }
})

function handleFileSelect(file: File) {
  videoPreviewUrl.value = URL.createObjectURL(file)
  uploadStore.startUpload(file)
}

function handleImageSelect(files: File[]) {
  uploadStore.startImageUpload(files)
}

function handleImageRemove(index: number) {
  const updated = [...uploadStore.imageFiles]
  updated.splice(index, 1)
  uploadStore.imageFiles = updated
  if (updated.length === 0) {
    uploadStore.resetUpload()
  }
}

function handleImageAdd(files: File[]) {
  uploadStore.imageFiles = [...uploadStore.imageFiles, ...files]
}

async function handleMultipleFileSelect(files: File[]) {
  await uploadQueueStore.addFiles(files)
  notify.success(t('upload.success.queuedFiles', { count: files.length }))
}

function handleUploadCancel() {
  if (videoPreviewUrl.value) {
    URL.revokeObjectURL(videoPreviewUrl.value)
    videoPreviewUrl.value = null
  }
  uploadStore.resetUpload()
}

async function handleStep2Next() {
  if (!metadata.value.title.trim()) {
    notify.error(t('upload.error.titleRequired'))
    return
  }

  // For video uploads, the videoId is already created during initUpload (Tus flow)
  if (!videoId.value && uploadStore.videoId) {
    videoId.value = uploadStore.videoId
  }

  // Create video/content record if not yet created (image uploads, etc.)
  if (!videoId.value) {
    try {
      const video = await videoApi.create({
        title: metadata.value.title,
        description: metadata.value.description || undefined,
        tags: metadata.value.tags.length > 0 ? metadata.value.tags : undefined,
        category: metadata.value.category || undefined,
        thumbnailUrl: metadata.value.thumbnailUrl || undefined,
        visibility: metadata.value.visibility,
        mediaType: uploadStore.mediaType,
      })
      videoId.value = video.id
      thumbnailCandidates.value = video.thumbnailCandidates || []

      // For images, upload them now
      if (uploadStore.isImage && uploadStore.imageFiles.length > 0) {
        try {
          await uploadStore.uploadImagesToServer(video.id)
        } catch {
          notify.error(t('upload.error.imageFailed'))
          return
        }
      }
    } catch {
      notify.error(t('upload.error.saveFailed'))
      return
    }
  } else {
    try {
      await videoApi.update(videoId.value, {
        title: metadata.value.title,
        description: metadata.value.description || undefined,
        tags: metadata.value.tags.length > 0 ? metadata.value.tags : undefined,
        category: metadata.value.category || undefined,
        thumbnailUrl: metadata.value.thumbnailUrl || undefined,
        visibility: metadata.value.visibility,
      })
    } catch {
      notify.error(t('upload.error.updateFailed'))
      return
    }
  }

  // Load AI schedule suggestions
  if (channelStore.channels.length > 0) {
    try {
      const res = await aiApi.suggestSchedule({ channelId: channelStore.channels[0].id })
      scheduleSuggestions.value = res.suggestions
    } catch {
      // Non-critical, continue without suggestions
    }
  }

  step.value = 3
}

async function handleAiGenerate() {
  if (!videoId.value && !uploadStore.uploadUrl) {
    notify.error(t('upload.error.fileRequired'))
    return
  }

  const requiredCredits = useStt.value ? 15 : 5
  if (!creditStore.hasEnoughCredits(requiredCredits)) {
    notify.error(t('upload.error.creditsInsufficient'))
    return
  }

  aiGenerating.value = true
  try {
    const res = await aiApi.generateMeta({
      videoId: videoId.value ?? undefined,
      useStt: useStt.value,
      targetPlatforms: channelStore.connectedPlatforms,
      tone: 'FRIENDLY',
      category: metadata.value.category || '엔터테인먼트',
    })

    if (res.platforms.length > 0) {
      const first = res.platforms[0]
      if (first.titleCandidates.length > 0) {
        metadata.value.title = first.titleCandidates[0]
      }
      metadata.value.description = first.description
    }
    // Collect hashtags from all platforms
    const allHashtags = res.platforms.flatMap((p) => p.hashtags)
    if (allHashtags.length > 0) {
      metadata.value.tags = allHashtags.slice(0, 30)
    }

    await creditStore.fetchBalance()
    notify.success(t('upload.success.aiGenerated', { credits: res.creditsUsed }))
  } catch {
    notify.error(t('upload.error.uploadFailed'))
  } finally {
    aiGenerating.value = false
  }
}

async function handleStartPipeline(steps: PipelineStepType[], channelId?: number) {
  // Use videoId from upload store if available (video was created during Tus init)
  if (!videoId.value && uploadStore.videoId) {
    videoId.value = uploadStore.videoId
  }

  if (!videoId.value) {
    try {
      const video = await videoApi.create({
        title: metadata.value.title || t('upload.defaultTitle'),
        description: metadata.value.description || undefined,
        tags: metadata.value.tags.length > 0 ? metadata.value.tags : undefined,
        category: metadata.value.category || undefined,
        visibility: metadata.value.visibility,
      })
      videoId.value = video.id
    } catch {
      notify.error(t('upload.error.saveFailed'))
      return
    }
  }

  try {
    const res = await aiApi.startPipeline({
      videoId: videoId.value,
      steps: steps,
      channelId: channelId,
    })
    pipelineId.value = res.pipelineId
    notify.success(t('upload.success.pipelineStarted'))
  } catch {
    notify.error(t('upload.error.uploadFailed'))
  }
}

function handlePipelineCompleted(pipeline: AiPipelineResponse) {
  const results = pipeline.results
  if (results['GENERATE_META']) {
    const metaResult = results['GENERATE_META'] as Record<string, unknown>
    const platforms = metaResult['platforms'] as Array<Record<string, unknown>> | undefined
    if (platforms && platforms.length > 0) {
      const first = platforms[0]
      const candidates = first['titleCandidates'] as string[] | undefined
      if (candidates && candidates.length > 0) {
        metadata.value.title = candidates[0]
      }
      if (first['description']) {
        metadata.value.description = first['description'] as string
      }
    }
  }
  if (results['GENERATE_HASHTAGS']) {
    const hashResult = results['GENERATE_HASHTAGS'] as Record<string, unknown>
    const platforms = hashResult['platforms'] as Array<Record<string, unknown>> | undefined
    if (platforms && platforms.length > 0) {
      const hashtags = platforms[0]['hashtags'] as string[] | undefined
      if (hashtags) {
        metadata.value.tags = hashtags.slice(0, 30)
      }
    }
  }

  creditStore.fetchBalance()
  pipelineId.value = null
  pipelineMode.value = false
  notify.success(t('upload.success.pipelineCompleted'))
}

function handlePipelineCancelled() {
  pipelineId.value = null
  creditStore.fetchBalance()
  notify.success(t('upload.success.pipelineCancelled'))
}

async function handleAddToQueue() {
  if (!videoId.value || !uploadStore.file) {
    notify.error(t('upload.error.fileRequired'))
    return
  }

  if (platformConfigs.value.length === 0) {
    notify.error(t('upload.error.platformRequired'))
    return
  }

  uploadQueueStore.addToQueue({
    file: uploadStore.file,
    fileName: uploadStore.file.name,
    fileSize: uploadStore.file.size,
    title: metadata.value.title,
    mediaType: uploadStore.mediaType,
    platforms: platformConfigs.value.map((config) => config.platform),
    metadata: {
      title: metadata.value.title,
      description: metadata.value.description,
      tags: metadata.value.tags,
    },
  })

  notify.success(t('upload.success.queued'))
  queuePanelOpen.value = true

  // Reset for next upload
  if (videoPreviewUrl.value) {
    URL.revokeObjectURL(videoPreviewUrl.value)
  }
  videoPreviewUrl.value = null
  videoId.value = null
  platformConfigs.value = []
  uploadStore.resetUpload()
}

async function handlePublish() {
  if (!videoId.value) {
    notify.error(t('upload.error.contentRequired'))
    return
  }

  if (platformConfigs.value.length === 0) {
    notify.error(t('upload.error.platformRequired'))
    return
  }

  publishing.value = true
  try {
    await videoApi.publish(videoId.value, {
      platforms: platformConfigs.value,
      scheduledAt: scheduledAt.value,
    })

    notify.success(scheduledAt.value ? t('upload.success.scheduled') : t('upload.success.publishing'))
    publishedVideoId.value = videoId.value

    // Poll for thumbnails only for video content
    if (!uploadStore.isImage) {
      pollThumbnails(videoId.value)
    }
  } catch {
    notify.error(t('upload.error.publishFailed'))
  } finally {
    publishing.value = false
  }
}

function pollThumbnails(vid: number, attempt = 0) {
  if (attempt >= 30) return
  thumbnailPollTimer = setTimeout(async () => {
    try {
      const result = await videoApi.getThumbnails(vid)
      if (result.thumbnails && result.thumbnails.length > 0) {
        autoThumbnails.value = result.thumbnails
        selectedThumbIndex.value = result.selectedIndex ?? 0
        return
      }
    } catch {
      // Thumbnails not ready yet
    }
    pollThumbnails(vid, attempt + 1)
  }, 10000)
}

async function handleThumbnailSelect(index: number) {
  if (!publishedVideoId.value) return
  try {
    await videoApi.selectThumbnail(publishedVideoId.value, index)
    selectedThumbIndex.value = index
  } catch {
    notify.error(t('video.thumbnail.selectError'))
  }
}
</script>
