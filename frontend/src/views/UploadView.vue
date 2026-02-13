<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">영상 업로드</h1>

      <!-- Queue button -->
      <button
        v-if="uploadQueueStore.queue.length > 0"
        class="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-all hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        @click="queuePanelOpen = true"
      >
        <QueueListIcon class="h-5 w-5" />
        업로드 큐
        <span class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
          {{ uploadQueueStore.queue.length }}
        </span>
      </button>
    </div>

    <!-- Step indicator -->
    <div class="mb-8 flex items-center justify-center gap-4">
      <div v-for="(label, i) in STEP_LABELS" :key="i" class="flex items-center gap-2">
        <div
          class="flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium"
          :class="
            i + 1 < step
              ? 'bg-green-500 text-white'
              : i + 1 === step
                ? 'bg-primary-600 text-white'
                : 'bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
          "
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
    </div>

    <!-- Step 1: File Upload -->
    <div v-if="step === 1" class="card">
      <FileDropzone
        v-if="!uploadStore.file"
        @select="handleFileSelect"
        @select-multiple="handleMultipleFileSelect"
        @error="(msg) => notify.error(msg)"
      />
      <UploadProgress
        v-else
        :file-name="uploadStore.file.name"
        :progress="uploadStore.progress"
        :uploading="uploadStore.uploading"
        :completed="uploadCompleted"
        :error="uploadStore.uploadError"
        @pause="uploadStore.pauseUpload()"
        @resume="uploadStore.resumeUpload()"
        @cancel="handleUploadCancel"
      />

      <div class="mt-6 flex justify-end">
        <button
          :disabled="!uploadCompleted"
          class="rounded-lg bg-primary-600 px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
          @click="step = 2"
        >
          다음 단계
        </button>
      </div>
    </div>

    <!-- Bulk Upload Queue -->
    <BulkUploadQueue v-if="step === 1" />

    <!-- Step 2: Metadata -->
    <div v-if="step === 2" class="space-y-6">
      <!-- AI Pipeline Toggle -->
      <div class="flex items-center gap-3">
        <button
          :class="[
            'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
            !pipelineMode
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          ]"
          @click="pipelineMode = false"
        >
          메타 입력
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
          AI 파이프라인
        </button>
      </div>

      <!-- AI Pipeline Builder / Progress -->
      <div v-if="pipelineMode" class="card">
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
          :credit-balance="creditStore.balance"
          @start="handleStartPipeline"
        />
      </div>

      <div v-if="!pipelineMode" class="card">
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
          class="rounded-lg border border-gray-300 dark:border-gray-600 px-6 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="step = 1"
        >
          이전
        </button>
        <button
          :disabled="!metadata.title.trim()"
          class="rounded-lg bg-primary-600 px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
          @click="handleStep2Next"
        >
          다음 단계
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

      <div class="mt-6 flex justify-between">
        <button
          class="rounded-lg border border-gray-300 dark:border-gray-600 px-6 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="step = 2"
        >
          이전
        </button>
        <div class="flex items-center gap-2">
          <button
            :disabled="platformConfigs.length === 0 || publishing"
            class="inline-flex items-center gap-2 rounded-lg border border-primary-600 bg-white px-6 py-2.5 text-sm font-medium text-primary-600 transition-colors hover:bg-primary-50 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-gray-800 dark:hover:bg-gray-700"
            @click="handleAddToQueue"
          >
            <QueueListIcon class="h-5 w-5" />
            큐에 추가
          </button>
          <button
            :disabled="platformConfigs.length === 0 || publishing"
            class="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50"
            @click="handlePublish"
          >
            <LoadingSpinner v-if="publishing" size="sm" />
            {{ scheduledAt ? '예약 게시' : '즉시 게시' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Upload Queue Panel -->
    <UploadQueuePanel v-model="queuePanelOpen" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CheckIcon } from '@heroicons/vue/24/solid'
import { QueueListIcon } from '@heroicons/vue/24/outline'
import FileDropzone from '@/components/upload/FileDropzone.vue'
import UploadProgress from '@/components/upload/UploadProgress.vue'
import MetadataForm from '@/components/upload/MetadataForm.vue'
import type { MetadataFormData } from '@/components/upload/MetadataForm.vue'
import PlatformSelector from '@/components/upload/PlatformSelector.vue'
import PlatformPreviewPanel from '@/components/preview/PlatformPreviewPanel.vue'
import OptimizationSuggestionList from '@/components/preview/OptimizationSuggestionList.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import BulkUploadQueue from '@/components/upload/BulkUploadQueue.vue'
import UploadQueuePanel from '@/components/upload/UploadQueuePanel.vue'
import AiPipelineBuilder from '@/components/ai/AiPipelineBuilder.vue'
import AiPipelineProgress from '@/components/ai/AiPipelineProgress.vue'
import { useUploadStore } from '@/stores/upload'
import { useChannelStore } from '@/stores/channel'
import { useCreditStore } from '@/stores/credit'
import { useNotificationStore } from '@/stores/notification'
import { useUploadQueueStore } from '@/stores/uploadQueue'
import { videoApi } from '@/api/video'
import { aiApi } from '@/api/ai'
import type { PlatformPublishConfig, OptimizationResult } from '@/types/video'
import type { ScheduleSuggestion, PipelineStepType, AiPipelineResponse } from '@/types/ai'

const STEP_LABELS = ['파일 선택', '메타 입력', '플랫폼 게시']

const router = useRouter()
const uploadStore = useUploadStore()
const channelStore = useChannelStore()
const creditStore = useCreditStore()
const notify = useNotificationStore()
const uploadQueueStore = useUploadQueueStore()

const step = ref(1)
const videoId = ref<number | null>(null)
const videoPreviewUrl = ref<string | null>(null)
const thumbnailCandidates = ref<string[]>([])
const useStt = ref(false)
const aiGenerating = ref(false)
const publishing = ref(false)
const scheduledAt = ref<string | undefined>(undefined)
const scheduleSuggestions = ref<ScheduleSuggestion[]>([])
const queuePanelOpen = ref(false)
const pipelineMode = ref(false)
const pipelineId = ref<string | null>(null)

const metadata = ref<MetadataFormData>({
  title: '',
  description: '',
  tags: [],
  category: '',
  visibility: 'PUBLIC',
  thumbnailUrl: '',
})

const platformConfigs = ref<PlatformPublishConfig[]>([])

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
  // Auto-fix simple issues: truncate title if too long for any platform
  const maxTitleLen = 100 // Minimum across YouTube and Naver Clip
  if (metadata.value.title.length > maxTitleLen) {
    metadata.value.title = metadata.value.title.substring(0, maxTitleLen)
  }

  // Truncate description if > 2200 (Instagram limit)
  if (metadata.value.description && metadata.value.description.length > 2200) {
    metadata.value.description = metadata.value.description.substring(0, 2200)
  }

  // Limit tags to 15 (YouTube max)
  if (metadata.value.tags.length > 15) {
    metadata.value.tags = metadata.value.tags.slice(0, 15)
  }

  notify.success('최적화 자동 수정이 적용되었습니다.')
}

const uploadCompleted = computed(
  () => uploadStore.progress.percentage === 100 && !uploadStore.uploading && !uploadStore.uploadError,
)

onMounted(async () => {
  uploadStore.resetUpload()
  uploadQueueStore.clearAll()
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
})

function handleFileSelect(file: File) {
  videoPreviewUrl.value = URL.createObjectURL(file)
  uploadStore.startUpload(file)
}

async function handleMultipleFileSelect(files: File[]) {
  await uploadQueueStore.addFiles(files)
  notify.success(`${files.length}개 파일이 업로드 큐에 추가되었습니다.`)
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
    notify.error('제목을 입력해주세요.')
    return
  }

  // Create video record if not yet created
  if (!videoId.value) {
    try {
      const video = await videoApi.create({
        title: metadata.value.title,
        description: metadata.value.description || undefined,
        tags: metadata.value.tags.length > 0 ? metadata.value.tags : undefined,
        category: metadata.value.category || undefined,
        thumbnailUrl: metadata.value.thumbnailUrl || undefined,
        visibility: metadata.value.visibility,
      })
      videoId.value = video.id
      thumbnailCandidates.value = video.thumbnailCandidates || []
    } catch {
      notify.error('영상 정보 저장에 실패했습니다.')
      return
    }
  } else {
    // Update existing
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
      notify.error('영상 정보 업데이트에 실패했습니다.')
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
    notify.error('파일 업로드를 먼저 완료해주세요.')
    return
  }

  const requiredCredits = useStt.value ? 15 : 5
  if (!creditStore.hasEnoughCredits(requiredCredits)) {
    notify.error('크레딧이 부족합니다.')
    return
  }

  aiGenerating.value = true
  try {
    const res = await aiApi.generateMeta({
      videoId: videoId.value ?? undefined,
      useStt: useStt.value,
      platforms: channelStore.connectedPlatforms,
      tone: 'FRIENDLY',
      category: metadata.value.category || '엔터테인먼트',
    })

    // Apply first platform result to base metadata
    if (res.platformResults.length > 0) {
      const first = res.platformResults[0]
      if (first.titleCandidates.length > 0) {
        metadata.value.title = first.titleCandidates[0]
      }
      metadata.value.description = first.description
    }
    if (res.hashtags.length > 0) {
      metadata.value.tags = res.hashtags.slice(0, 30)
    }

    await creditStore.fetchBalance()
    notify.success(`AI 메타데이터가 생성되었습니다. (${res.creditsUsed} 크레딧 사용)`)
  } catch {
    notify.error('AI 메타데이터 생성에 실패했습니다.')
  } finally {
    aiGenerating.value = false
  }
}

async function handleStartPipeline(steps: PipelineStepType[], channelId?: number) {
  if (!videoId.value) {
    // Create video record first if not yet created
    try {
      const video = await videoApi.create({
        title: metadata.value.title || '임시 제목',
        description: metadata.value.description || undefined,
        tags: metadata.value.tags.length > 0 ? metadata.value.tags : undefined,
        category: metadata.value.category || undefined,
        visibility: metadata.value.visibility,
      })
      videoId.value = video.id
    } catch {
      notify.error('영상 정보 저장에 실패했습니다.')
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
    notify.success('AI 파이프라인이 시작되었습니다.')
  } catch {
    notify.error('AI 파이프라인 시작에 실패했습니다.')
  }
}

function handlePipelineCompleted(pipeline: AiPipelineResponse) {
  // Apply results to metadata if available
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
  notify.success('AI 파이프라인이 완료되었습니다.')
}

function handlePipelineCancelled() {
  pipelineId.value = null
  creditStore.fetchBalance()
  notify.success('AI 파이프라인이 취소되었습니다.')
}

async function handleAddToQueue() {
  if (!videoId.value || !uploadStore.file) {
    notify.error('영상 파일을 먼저 업로드해주세요.')
    return
  }

  if (platformConfigs.value.length === 0) {
    notify.error('게시할 플랫폼을 선택해주세요.')
    return
  }

  // Add to queue
  uploadQueueStore.addToQueue({
    file: uploadStore.file,
    fileName: uploadStore.file.name,
    fileSize: uploadStore.file.size,
    title: metadata.value.title,
    platforms: platformConfigs.value.map((config) => config.platform),
    metadata: {
      title: metadata.value.title,
      description: metadata.value.description,
      tags: metadata.value.tags,
    },
  })

  notify.success('업로드 큐에 추가되었습니다.')
  queuePanelOpen.value = true

  // Reset for next upload
  uploadStore.resetUpload()
  step.value = 1
  videoId.value = null
  if (videoPreviewUrl.value) {
    URL.revokeObjectURL(videoPreviewUrl.value)
  }
  videoPreviewUrl.value = null
  metadata.value = {
    title: '',
    description: '',
    tags: [],
    category: '',
    visibility: 'PUBLIC',
    thumbnailUrl: '',
  }
  platformConfigs.value = []
}

async function handlePublish() {
  if (!videoId.value) {
    notify.error('영상 정보가 없습니다.')
    return
  }

  if (platformConfigs.value.length === 0) {
    notify.error('게시할 플랫폼을 선택해주세요.')
    return
  }

  publishing.value = true
  try {
    await videoApi.publish(videoId.value, {
      platforms: platformConfigs.value,
      scheduledAt: scheduledAt.value,
    })

    notify.success(scheduledAt.value ? '예약 게시가 설정되었습니다.' : '영상이 게시 중입니다.')
    uploadStore.resetUpload()
    router.push('/videos')
  } catch {
    notify.error('게시에 실패했습니다. 다시 시도해주세요.')
  } finally {
    publishing.value = false
  }
}
</script>
