<template>
  <!-- 영상 상세 - Dev Guide Section 10.2 -->
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header with back navigation -->
    <div class="mb-6 flex items-center gap-3">
      <button
        class="rounded-lg p-1.5 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
        :aria-label="$t('videoDetail.backToList')"
        @click="router.push('/videos')"
      >
        <ArrowLeftIcon class="h-5 w-5" />
      </button>
      <h1 class="text-h1 font-bold text-gray-900 dark:text-gray-100">{{ $t('videoDetail.title') }}</h1>
    </div>

    <PageGuide
      :title="$t('videoDetail.pageGuideTitle')"
      :items="($tm('videoDetail.pageGuide') as string[])"
    />

    <LoadingSpinner v-if="loading" full-page />

    <div v-else-if="videoStore.detailLoadError" class="card py-16 text-center" role="alert">
      <ExclamationTriangleIcon class="mx-auto mb-4 h-12 w-12 text-error-strong" />
      <p class="text-title font-medium text-content">{{ videoStore.detailLoadError }}</p>
      <button class="btn-primary mt-4" @click="videoStore.fetchVideo(Number(props.id))">
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- Not Found -->
    <div v-else-if="!video" class="card py-16 text-center">
      <ExclamationTriangleIcon class="mx-auto mb-4 h-12 w-12 text-gray-400 dark:text-gray-500" />
      <p class="text-title font-medium text-gray-600 dark:text-gray-300">{{ $t('videoDetail.notFound') }}</p>
      <button class="btn-primary mt-4" @click="router.push('/videos')">
        {{ $t('videoDetail.backToList') }}
      </button>
    </div>

    <template v-else>
      <!-- Video Preview + Basic Info -->
      <div class="page-grid page-grid--wide mb-6">
        <!-- Video Preview (thumbnail placeholder) -->
        <div class="desktop:col-span-1">
          <div class="card overflow-hidden p-0">
            <div class="relative aspect-video w-full bg-gray-900">
              <img
                v-if="video.thumbnailUrl"
                :src="video.thumbnailUrl"
                :alt="video.title"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full items-center justify-center">
                <VideoCameraIcon class="h-16 w-16 text-gray-600 dark:text-gray-300" />
              </div>
            </div>
            <!-- Status bar -->
            <div class="flex items-center justify-between border-t border-gray-100 dark:border-gray-700 px-4 py-3">
              <StatusBadge :status="video.status" />
            </div>
          </div>
        </div>

        <!-- Basic Info -->
        <div class="desktop:col-span-2">
          <div class="card h-full">
            <div class="mb-4 flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="mb-1 flex items-center gap-2">
                  <h2 class="text-h2 font-bold text-gray-900 dark:text-gray-100">{{ video.title }}</h2>
                  <FavoriteButton :video-id="video.id" />
                </div>
                <p v-if="video.description" class="line-clamp-3 text-body text-gray-600 dark:text-gray-300">
                  {{ video.description }}
                </p>
              </div>
            </div>

            <!-- Info Grid -->
            <div class="mb-5 grid grid-cols-2 gap-4 tablet:grid-cols-4">
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videoDetail.uploadedAt') }}</p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ formatDate(video.createdAt) }}
                </p>
              </div>
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('video.mediaInfo.fileSize') }}
                </p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.fileSize ? formatFileSize(video.fileSize) : '-' }}
                </p>
              </div>
              <div>
                <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videoDetail.category') }}</p>
                <p class="mt-0.5 text-body font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.category ?? $t('videoDetail.uncategorized') }}
                </p>
              </div>
            </div>

            <!-- Tags -->
            <div v-if="video.tags.length > 0" class="mb-5">
              <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">
                {{ $t('videoDetail.tags') }}
              </p>
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="tag in video.tags"
                  :key="tag"
                  class="badge-gray"
                >
                  #{{ tag }}
                </span>
              </div>
            </div>

            <!-- Platform Upload Badges -->
            <div v-if="video.uploads.length > 0" class="mb-5">
              <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">
                {{ $t('videoDetail.uploadPlatforms') }}
              </p>
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="upload in video.uploads"
                  :key="upload.id"
                  class="flex min-w-0 flex-wrap items-center gap-1.5"
                >
                  <PlatformBadge :platform="upload.platform" />
                  <span v-if="upload.channelName" class="text-body-xs text-gray-500 dark:text-gray-400">
                    {{ upload.channelName }}
                  </span>
                  <StatusBadge :status="upload.status" />
                  <a
                    v-if="upload.platformUrl"
                    :href="upload.platformUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="text-body-xs text-primary-600 underline underline-offset-2 dark:text-primary-400"
                  >
                    {{ $t('videoDetail.openPlatform') }}
                  </a>
                  <span v-if="upload.errorMessage" class="basis-full text-body-xs text-error-strong">
                    {{ upload.errorMessage }}
                  </span>
                  <button
                    v-if="upload.status === 'FAILED' || upload.status === 'REJECTED' || upload.status === 'UNCONFIRMED'"
                    type="button"
                    class="basis-full text-left text-body-xs font-semibold text-primary-600 underline underline-offset-2 disabled:opacity-50 dark:text-primary-400"
                    :disabled="retryingUploadId === upload.id"
                    @click="handleUploadRecovery(upload)"
                  >
                    {{ retryingUploadId === upload.id
                      ? $t('videoDetail.recoveringUpload')
                      : upload.status === 'UNCONFIRMED'
                        ? $t('videoDetail.recheckUpload')
                        : $t('videoDetail.retryUpload') }}
                  </button>
                </div>
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="flex flex-wrap gap-2 border-t border-gray-100 dark:border-gray-700 pt-4">
              <button class="btn-primary" @click="showPreviewModal = true">
                <PlayIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videoDetail.preview') }}
              </button>
              <button class="btn-primary" @click="handleEdit">
                <PencilSquareIcon class="mr-1.5 h-4 w-4" />
                {{ $t('action.edit') }}
              </button>
              <button class="btn-secondary" @click="handleRecycle">
                <ArrowPathRoundedSquareIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videos.contextRecycle') }}
              </button>
              <button class="btn-secondary" @click="handleReUpload">
                <ArrowPathIcon class="mr-1.5 h-4 w-4" />
                {{ $t('videos.contextReupload') }}
              </button>
              <button class="btn-danger" @click="showDeleteModal = true">
                <TrashIcon class="mr-1.5 h-4 w-4" />
                {{ $t('action.delete') }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Performance Score Section -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <PerformanceScore :video="video" :analytics="analyticsData" />
      </div>

      <!-- AI Performance Score Card -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <PerformanceScoreCard :video-id="video.id" />
      </div>

      <!-- Anomaly Alerts -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <AnomalyAlertCard @analyze="handleAnalyzeAnomaly" />
      </div>

      <!-- Platform Analytics Section -->
      <div v-if="video.uploads.length > 0" class="mb-6">
        <div
          v-if="analyticsError"
          class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
          role="alert"
        >
          <span class="flex-1">{{ analyticsError }}</span>
          <button
            type="button"
            class="btn-secondary min-h-11"
            :disabled="analyticsLoading"
            @click="video && fetchAnalytics(video.id)"
          >
            {{ analyticsLoading ? $t('action.loading') : $t('action.retry') }}
          </button>
        </div>

        <!-- Platform Tab Selector -->
        <div class="mb-4 flex gap-1 overflow-x-auto rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
          <button
            v-for="upload in video.uploads"
            :key="upload.platform"
            class="flex-shrink-0 rounded-md px-4 py-2 text-body font-medium transition-colors"
            :class="
              selectedPlatform === upload.platform
                ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                : 'text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-gray-100'
            "
            @click="selectedPlatform = upload.platform"
          >
            <span
              class="mr-1.5 inline-block h-2 w-2 rounded-full"
              :style="{ backgroundColor: PLATFORM_CONFIG[upload.platform].color }"
            />
            {{ PLATFORM_CONFIG[upload.platform].label }}
          </button>
        </div>

        <!-- Performance Cards -->
        <div class="page-grid page-grid--metrics mb-6">
          <!-- Views -->
          <div class="card text-center">
            <EyeIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.views') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.views ?? 0) }}
            </p>
            <div
              v-if="currentAnalytics?.viewsChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-caption"
              :class="changeColorClass(currentAnalytics.viewsChange)"
            >
              <ArrowTrendingUpIcon v-if="currentAnalytics.viewsChange > 0" class="h-3 w-3" />
              <ArrowTrendingDownIcon v-else-if="currentAnalytics.viewsChange < 0" class="h-3 w-3" />
              {{ formatChange(currentAnalytics.viewsChange) }}
            </div>
          </div>

          <!-- Likes -->
          <div class="card text-center">
            <HeartIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.likes') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.likes ?? 0) }}
            </p>
            <div
              v-if="currentAnalytics?.likesChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-caption"
              :class="changeColorClass(currentAnalytics.likesChange)"
            >
              <ArrowTrendingUpIcon v-if="currentAnalytics.likesChange > 0" class="h-3 w-3" />
              <ArrowTrendingDownIcon v-else-if="currentAnalytics.likesChange < 0" class="h-3 w-3" />
              {{ formatChange(currentAnalytics.likesChange) }}
            </div>
          </div>

          <!-- Comments -->
          <div class="card text-center">
            <ChatBubbleLeftEllipsisIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.comments') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.comments ?? 0) }}
            </p>
          </div>

          <!-- Shares -->
          <div class="card text-center">
            <ShareIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-caption text-gray-500 dark:text-gray-400">{{ $t('videos.shares') }}</p>
            <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.shares ?? 0) }}
            </p>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="page-grid page-grid--split mb-6">
          <!-- Daily Views Trend Line Chart Placeholder -->
          <div class="card">
            <h3 class="mb-4 text-h3 text-gray-900 dark:text-gray-100">
              {{ $t('videoDetail.dailyViewsTrend') }}
            </h3>
            <div
              v-if="currentAnalytics && currentAnalytics.dailyTrend.length > 0"
              class="relative h-64"
            >
              <!-- Simple bar visualization as chart placeholder -->
              <div class="flex h-full items-end gap-1">
                <div
                  v-for="(point, idx) in currentAnalytics.dailyTrend"
                  :key="idx"
                  class="group relative flex flex-1 flex-col items-center justify-end"
                >
                  <div
                    class="w-full rounded-t transition-colors"
                    :style="{
                      height: `${trendBarHeight(point.totalViews)}%`,
                      backgroundColor: selectedPlatform ? PLATFORM_CONFIG[selectedPlatform].color : '#6B7280',
                      opacity: 0.7,
                    }"
                  />
                  <!-- Tooltip on hover -->
                  <div
                    class="pointer-events-none absolute -top-10 left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-body-xs text-white shadow group-hover:block"
                  >
                    {{ formatShortDate(point.date) }}: {{ formatCompactNumber(point.totalViews) }}
                  </div>
                </div>
              </div>
              <!-- X-axis labels -->
              <div class="mt-2 flex justify-between text-body-xs text-gray-400 dark:text-gray-500">
                <span>{{ formatShortDate(currentAnalytics.dailyTrend[0]?.date) }}</span>
                <span>
                  {{
                    formatShortDate(
                      currentAnalytics.dailyTrend[
                        Math.floor(currentAnalytics.dailyTrend.length / 2)
                      ]?.date
                    )
                  }}
                </span>
                <span>
                  {{
                    formatShortDate(
                      currentAnalytics.dailyTrend[currentAnalytics.dailyTrend.length - 1]?.date
                    )
                  }}
                </span>
              </div>
            </div>
            <div v-else class="flex h-64 items-center justify-center">
              <div class="text-center">
                <ChartBarIcon class="mx-auto mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="text-body text-gray-400 dark:text-gray-500">
                  {{ $t('videoDetail.notEnoughData') }}
                </p>
              </div>
            </div>
          </div>

          <!-- Platform Comparison Grouped Bar Chart Placeholder -->
          <div class="card">
            <h3 class="mb-4 text-h3 text-gray-900 dark:text-gray-100">
              {{ $t('videoDetail.platformComparison') }}
            </h3>
            <div v-if="analyticsData.length > 0" class="h-64 space-y-4 overflow-y-auto">
              <!-- Views comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.views') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`views-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.views, maxViews)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.views) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Likes comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.likes') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`likes-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.likes, maxLikes)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.likes) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Comments comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.comments') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`comments-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.comments, maxComments)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.comments) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Shares comparison -->
              <div>
                <p class="mb-1 text-caption text-gray-500 dark:text-gray-400">
                  {{ $t('videos.shares') }}
                </p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`shares-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-body-xs text-gray-600 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[a.platform].label }}
                    </span>
                    <div class="h-4 flex-1 rounded-full bg-gray-100 dark:bg-gray-800">
                      <div
                        class="h-full rounded-full transition-all"
                        :style="{
                          width: `${comparisonBarWidth(a.shares, maxShares)}%`,
                          backgroundColor: PLATFORM_CONFIG[a.platform].color,
                        }"
                      />
                    </div>
                    <span class="w-14 text-right text-caption text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.shares) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="flex h-64 items-center justify-center">
              <div class="text-center">
                <ChartBarIcon class="mx-auto mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="text-body text-gray-400 dark:text-gray-500">
                  {{ $t('videoDetail.noAnalyticsData') }}
                </p>
              </div>
            </div>
          </div>
        </div>

      </div>

      <!-- No Uploads State -->
      <div v-else class="card py-12 text-center">
        <CloudArrowUpIcon class="mx-auto mb-3 h-12 w-12 text-gray-300 dark:text-gray-600" />
        <p class="mb-1 text-body-lg font-medium text-gray-600 dark:text-gray-300">
          {{ $t('videoDetail.noUploadsTitle') }}
        </p>
        <p class="mb-4 text-body text-gray-400 dark:text-gray-500">
          {{ $t('videoDetail.noUploadsDescription') }}
        </p>
        <button class="btn-primary" @click="handleReUpload">
          <ArrowPathIcon class="mr-1.5 h-4 w-4" />
          {{ $t('videoDetail.uploadNow') }}
        </button>
      </div>
    </template>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      :title="$t('videos.deleteTitle')"
      :message="$t('videoDetail.deleteMessage')"
      :confirm-text="$t('action.delete')"
      :cancel-text="$t('action.cancel')"
      danger
      @confirm="handleDelete"
    />

    <!-- Recycle Modal -->
    <RecycleModal
      v-if="video"
      v-model="showRecycleModal"
      :video="video"
      @confirm="handleRecycleConfirm"
    />

    <!-- Video Preview Modal -->
    <VideoPreviewModal
      v-if="video"
      v-model="showPreviewModal"
      :video="video"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  ArrowLeftIcon,
  PencilSquareIcon,
  TrashIcon,
  ArrowPathIcon,
  ArrowPathRoundedSquareIcon,
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftEllipsisIcon,
  ShareIcon,
  VideoCameraIcon,
  ChartBarIcon,
  CloudArrowUpIcon,
  ExclamationTriangleIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
} from '@heroicons/vue/24/outline'
import { PlayIcon } from '@heroicons/vue/24/solid'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import RecycleModal from '@/components/video/RecycleModal.vue'
import PerformanceScore from '@/components/video/PerformanceScore.vue'
import PerformanceScoreCard from '@/components/analytics/PerformanceScoreCard.vue'
import AnomalyAlertCard from '@/components/analytics/AnomalyAlertCard.vue'
import FavoriteButton from '@/components/video/FavoriteButton.vue'
import VideoPreviewModal from '@/components/video/VideoPreviewModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useLocale } from '@/composables/useLocale'
import { useVideoStore } from '@/stores/video'
import { analyticsApi } from '@/api/analytics'
import { videoApi } from '@/api/video'
import type { VideoAnalytics } from '@/types/analytics'
import type { VideoUpload } from '@/types/video'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

// ---- Props ----

const props = defineProps<{
  id: string
}>()

// ---- Router & Store ----

const router = useRouter()
const { t } = useI18n()
const { currentLocale } = useLocale()
const videoStore = useVideoStore()
const { currentVideo: video, isLoadingDetail: loading } = storeToRefs(videoStore)

// ---- Reactive State ----

const showDeleteModal = ref(false)
const showRecycleModal = ref(false)
const showPreviewModal = ref(false)
const selectedPlatform = ref<Platform | null>(null)
const analyticsData = ref<VideoAnalytics[]>([])
const analyticsLoading = ref(false)
const analyticsError = ref<string | null>(null)
const retryingUploadId = ref<number | null>(null)

// ---- Computed ----

/** Analytics data for the currently selected platform tab */
const currentAnalytics = computed(() => {
  if (!selectedPlatform.value) return null
  return analyticsData.value.find((a) => a.platform === selectedPlatform.value) ?? null
})

/** Max metric values for scaling comparison bar widths */
const maxViews = computed(() => Math.max(...analyticsData.value.map((a) => a.views), 1))
const maxLikes = computed(() => Math.max(...analyticsData.value.map((a) => a.likes), 1))
const maxComments = computed(() => Math.max(...analyticsData.value.map((a) => a.comments), 1))
const maxShares = computed(() => Math.max(...analyticsData.value.map((a) => a.shares), 1))

/** Max daily trend value for scaling bar heights */
const maxTrendValue = computed(() => {
  if (!currentAnalytics.value) return 1
  return Math.max(...currentAnalytics.value.dailyTrend.map((p) => p.totalViews), 1)
})

// ---- Formatters ----

/** Format bytes into human-readable MB/GB */
function formatFileSize(bytes: number): string {
  if (bytes >= 1_073_741_824) {
    return `${(bytes / 1_073_741_824).toFixed(1)} GB`
  }
  if (bytes >= 1_048_576) {
    return `${(bytes / 1_048_576).toFixed(1)} MB`
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${bytes} B`
}

/** Format ISO date string to Korean locale long date */
function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

/** Format ISO date string to short M/D format */
function formatShortDate(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

/** Compact number formatter — locale-aware units (ko: 천/만/억, en: K/M/B) */
const compactNumberFormat = computed(
  () => new Intl.NumberFormat(currentLocale.value, { notation: 'compact', maximumFractionDigits: 1 })
)

/** Format large numbers with locale-specific units */
function formatCompactNumber(n: number): string {
  return compactNumberFormat.value.format(n)
}

/** Format a change percentage with sign */
function formatChange(value: number): string {
  if (value === 0) return '0%'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

/** Return Tailwind color class based on positive/negative/zero change */
function changeColorClass(value: number): string {
  if (value > 0) return 'text-success-strong'
  if (value < 0) return 'text-error-strong'
  return 'text-gray-400'
}

/** Calculate bar height percentage for trend chart */
function trendBarHeight(value: number): number {
  if (maxTrendValue.value === 0) return 0
  return Math.max((value / maxTrendValue.value) * 100, 2)
}

/** Calculate bar width percentage for comparison chart */
function comparisonBarWidth(value: number, max: number): number {
  if (max === 0) return 0
  return Math.max((value / max) * 100, 2)
}

// ---- Actions ----

function handleAnalyzeAnomaly(videoId: number) {
  router.push(`/videos/${videoId}`)
}

function handleEdit() {
  // DRAFTs use the production compose flow so their server-persisted
  // platform overrides are restored. Published videos keep the legacy
  // metadata editor because it also handles remote platform updates.
  if (video.value?.status === 'DRAFT') {
    router.push({ path: '/compose', query: { videoId: String(props.id) } })
    return
  }
  router.push(`/upload?edit=${props.id}`)
}

function handleRecycle() {
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await videoStore.fetchVideo(Number(props.id))
}

async function handleUploadRecovery(upload: VideoUpload) {
  if (!video.value) return
  retryingUploadId.value = upload.id
  try {
    if (upload.status === 'UNCONFIRMED') {
      await videoApi.recheckUpload(video.value.id, upload.id)
    } else {
      await videoApi.retryUpload(video.value.id, upload.id)
    }
    await videoStore.fetchVideo(video.value.id)
  } catch {
    // API client의 서버 오류가 전역 알림으로 표시된다.
  } finally {
    retryingUploadId.value = null
  }
}

function handleReUpload() {
  router.push(`/upload?reupload=${props.id}`)
}

async function handleDelete() {
  try {
    await videoStore.deleteVideo(Number(props.id))
    router.push('/videos')
  } catch {
    // Error handled by API client interceptor
  }
}

async function fetchAnalytics(videoId: number) {
  analyticsLoading.value = true
  analyticsError.value = null
  try {
    analyticsData.value = await analyticsApi.videoAnalytics(videoId)
  } catch {
    analyticsError.value = t('videoDetail.analyticsLoadFailed')
  } finally {
    analyticsLoading.value = false
  }
}

// ---- Lifecycle ----

onMounted(async () => {
  const videoId = Number(props.id)
  await videoStore.fetchVideo(videoId)

  if (video.value && video.value.uploads.length > 0) {
    selectedPlatform.value = video.value.uploads[0].platform
    await fetchAnalytics(videoId)
  }
})

// Set default selected platform when video data loads
watch(video, (v) => {
  if (v && v.uploads.length > 0 && !selectedPlatform.value) {
    selectedPlatform.value = v.uploads[0].platform
  }
})
</script>
