<template>
  <!-- 영상 상세 - Dev Guide Section 10.2 -->
  <div>
    <!-- Header with back navigation -->
    <div class="mb-6 flex items-center gap-3">
      <button
        class="rounded-lg p-1.5 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
        @click="router.push('/videos')"
      >
        <ArrowLeftIcon class="h-5 w-5" />
      </button>
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">영상 상세</h1>
    </div>

    <LoadingSpinner v-if="loading" full-page />

    <!-- Not Found -->
    <div v-else-if="!video" class="card py-16 text-center">
      <ExclamationTriangleIcon class="mx-auto mb-4 h-12 w-12 text-gray-400 dark:text-gray-500" />
      <p class="text-lg font-medium text-gray-600 dark:text-gray-300">영상을 찾을 수 없습니다</p>
      <button class="btn-primary mt-4" @click="router.push('/videos')">영상 목록으로</button>
    </div>

    <template v-else>
      <!-- Video Preview + Basic Info -->
      <div class="mb-6 grid gap-6 desktop:grid-cols-3">
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
                <VideoCameraIcon class="h-16 w-16 text-gray-600" />
              </div>
              <!-- Duration overlay -->
              <span
                v-if="video.duration"
                class="absolute bottom-2 right-2 rounded bg-black/80 px-1.5 py-0.5 text-xs font-medium text-white"
              >
                {{ formatDuration(video.duration) }}
              </span>
            </div>
            <!-- Status bar -->
            <div class="flex items-center justify-between border-t border-gray-100 dark:border-gray-700 px-4 py-3">
              <StatusBadge :status="video.status" />
              <span class="text-xs text-gray-500 dark:text-gray-400">{{ video.resolution ?? '-' }}</span>
            </div>
          </div>
        </div>

        <!-- Basic Info -->
        <div class="desktop:col-span-2">
          <div class="card h-full">
            <div class="mb-4 flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="mb-1 flex items-center gap-2">
                  <h2 class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ video.title }}</h2>
                  <FavoriteButton :video-id="video.id" />
                </div>
                <p v-if="video.description" class="line-clamp-3 text-sm text-gray-600 dark:text-gray-300">
                  {{ video.description }}
                </p>
              </div>
            </div>

            <!-- Info Grid -->
            <div class="mb-5 grid grid-cols-2 gap-4 tablet:grid-cols-4">
              <div>
                <p class="text-xs font-medium text-gray-500 dark:text-gray-400">업로드일</p>
                <p class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ formatDate(video.createdAt) }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 dark:text-gray-400">영상 길이</p>
                <p class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.duration ? formatDuration(video.duration) : '-' }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 dark:text-gray-400">파일 크기</p>
                <p class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.fileSize ? formatFileSize(video.fileSize) : '-' }}
                </p>
              </div>
              <div>
                <p class="text-xs font-medium text-gray-500 dark:text-gray-400">카테고리</p>
                <p class="mt-0.5 text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ video.category ?? '미지정' }}
                </p>
              </div>
            </div>

            <!-- Tags -->
            <div v-if="video.tags.length > 0" class="mb-5">
              <p class="mb-2 text-xs font-medium text-gray-500">태그</p>
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
              <p class="mb-2 text-xs font-medium text-gray-500">업로드 플랫폼</p>
              <div class="flex flex-wrap gap-2">
                <div
                  v-for="upload in video.uploads"
                  :key="upload.id"
                  class="flex items-center gap-1.5"
                >
                  <PlatformBadge :platform="upload.platform" />
                  <StatusBadge :status="upload.status" />
                </div>
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="flex flex-wrap gap-2 border-t border-gray-100 dark:border-gray-700 pt-4">
              <button class="btn-primary" @click="showPreviewModal = true">
                <PlayIcon class="mr-1.5 h-4 w-4" />
                미리보기
              </button>
              <button class="btn-primary" @click="handleEdit">
                <PencilSquareIcon class="mr-1.5 h-4 w-4" />
                수정
              </button>
              <button class="btn-secondary" @click="handleRecycle">
                <ArrowPathRoundedSquareIcon class="mr-1.5 h-4 w-4" />
                재활용
              </button>
              <button class="btn-secondary" @click="handleReUpload">
                <ArrowPathIcon class="mr-1.5 h-4 w-4" />
                재업로드
              </button>
              <button class="btn-danger" @click="showDeleteModal = true">
                <TrashIcon class="mr-1.5 h-4 w-4" />
                삭제
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Platform Variants Section -->
      <div v-if="video.variants && video.variants.length > 0" class="mb-6">
        <div class="card">
          <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">플랫폼별 변환본</h3>
          <div class="space-y-3">
            <div
              v-for="variant in video.variants"
              :key="variant.platform"
              class="flex items-center justify-between rounded-lg border border-gray-100 dark:border-gray-700 p-3"
            >
              <div class="flex items-center gap-3">
                <PlatformBadge :platform="variant.platform" />
                <div>
                  <div class="flex items-center gap-2">
                    <!-- Status indicator -->
                    <span
                      v-if="variant.status === 'PENDING'"
                      class="inline-flex items-center gap-1 text-xs font-medium text-gray-500 dark:text-gray-400"
                    >
                      <span class="h-2 w-2 rounded-full bg-gray-400 animate-pulse" />
                      대기 중
                    </span>
                    <span
                      v-else-if="variant.status === 'PROCESSING'"
                      class="inline-flex items-center gap-1 text-xs font-medium text-blue-600 dark:text-blue-400"
                    >
                      <svg class="h-3 w-3 animate-spin" viewBox="0 0 24 24" fill="none">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                      변환 중
                    </span>
                    <span
                      v-else-if="variant.status === 'COMPLETED'"
                      class="inline-flex items-center gap-1 text-xs font-medium text-green-600 dark:text-green-400"
                    >
                      <svg class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
                      </svg>
                      변환 완료
                    </span>
                    <span
                      v-else-if="variant.status === 'FAILED'"
                      class="inline-flex items-center gap-1 text-xs font-medium text-red-600 dark:text-red-400"
                    >
                      <svg class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                      </svg>
                      변환 실패
                    </span>
                  </div>
                  <p v-if="variant.width && variant.height" class="mt-0.5 text-xs text-gray-400 dark:text-gray-500">
                    {{ variant.width }}x{{ variant.height }}
                    <span v-if="variant.fileSizeBytes"> · {{ formatFileSize(variant.fileSizeBytes) }}</span>
                  </p>
                  <p v-if="variant.errorMessage" class="mt-0.5 text-xs text-red-500">
                    {{ variant.errorMessage }}
                  </p>
                </div>
              </div>
              <button
                v-if="variant.status === 'FAILED'"
                class="btn-secondary text-xs"
                @click="handleRetryTranscode(variant.platform)"
              >
                <ArrowPathIcon class="mr-1 h-3 w-3" />
                재시도
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
        <!-- Platform Tab Selector -->
        <div class="mb-4 flex gap-1 overflow-x-auto rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
          <button
            v-for="upload in video.uploads"
            :key="upload.platform"
            class="flex-shrink-0 rounded-md px-4 py-2 text-sm font-medium transition-colors"
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
        <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
          <!-- Views -->
          <div class="card text-center">
            <EyeIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">조회수</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.views ?? 0) }}
            </p>
            <div
              v-if="currentAnalytics?.viewsChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-xs font-medium"
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
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">좋아요</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.likes ?? 0) }}
            </p>
            <div
              v-if="currentAnalytics?.likesChange != null"
              class="mt-1 flex items-center justify-center gap-0.5 text-xs font-medium"
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
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">댓글</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.comments ?? 0) }}
            </p>
          </div>

          <!-- Shares -->
          <div class="card text-center">
            <ShareIcon class="mx-auto mb-2 h-6 w-6 text-gray-400 dark:text-gray-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">공유</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompactNumber(currentAnalytics?.shares ?? 0) }}
            </p>
          </div>
        </div>

        <!-- Charts Row -->
        <div class="mb-6 grid gap-6 desktop:grid-cols-2">
          <!-- Daily Views Trend Line Chart Placeholder -->
          <div class="card">
            <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">일별 조회수 추이</h3>
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
                    class="pointer-events-none absolute -top-10 left-1/2 z-10 hidden -translate-x-1/2 whitespace-nowrap rounded bg-gray-900 px-2 py-1 text-xs text-white shadow group-hover:block"
                  >
                    {{ formatShortDate(point.date) }}: {{ formatCompactNumber(point.totalViews) }}
                  </div>
                </div>
              </div>
              <!-- X-axis labels -->
              <div class="mt-2 flex justify-between text-xs text-gray-400 dark:text-gray-500">
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
                <p class="text-sm text-gray-400 dark:text-gray-500">데이터가 충분하지 않습니다</p>
              </div>
            </div>
          </div>

          <!-- Platform Comparison Grouped Bar Chart Placeholder -->
          <div class="card">
            <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">플랫폼 간 비교</h3>
            <div v-if="analyticsData.length > 0" class="h-64 space-y-4 overflow-y-auto">
              <!-- Views comparison -->
              <div>
                <p class="mb-1 text-xs font-medium text-gray-500">조회수</p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`views-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-xs text-gray-600 dark:text-gray-300">
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
                    <span class="w-14 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.views) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Likes comparison -->
              <div>
                <p class="mb-1 text-xs font-medium text-gray-500">좋아요</p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`likes-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-xs text-gray-600 dark:text-gray-300">
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
                    <span class="w-14 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.likes) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Comments comparison -->
              <div>
                <p class="mb-1 text-xs font-medium text-gray-500">댓글</p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`comments-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-xs text-gray-600 dark:text-gray-300">
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
                    <span class="w-14 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.comments) }}
                    </span>
                  </div>
                </div>
              </div>
              <!-- Shares comparison -->
              <div>
                <p class="mb-1 text-xs font-medium text-gray-500">공유</p>
                <div class="space-y-1">
                  <div
                    v-for="a in analyticsData"
                    :key="`shares-${a.platform}`"
                    class="flex items-center gap-2"
                  >
                    <span class="w-20 text-xs text-gray-600 dark:text-gray-300">
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
                    <span class="w-14 text-right text-xs font-medium text-gray-700 dark:text-gray-300">
                      {{ formatCompactNumber(a.shares) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="flex h-64 items-center justify-center">
              <div class="text-center">
                <ChartBarIcon class="mx-auto mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" />
                <p class="text-sm text-gray-400 dark:text-gray-500">분석 데이터가 없습니다</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Recent Comments Section (Phase 2 placeholder) -->
        <div class="card">
          <div class="flex items-center justify-between">
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">최근 댓글</h3>
            <span class="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs font-medium text-gray-500 dark:text-gray-400">
              Phase 2
            </span>
          </div>
          <div class="mt-6 flex flex-col items-center justify-center py-8 text-center">
            <ChatBubbleLeftEllipsisIcon class="mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
            <p class="text-sm text-gray-500 dark:text-gray-400">
              댓글 통합 관리 기능은 Phase 2에서 제공됩니다.
            </p>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
              플랫폼별 댓글 조회 및 답글 작성이 가능해집니다.
            </p>
          </div>
        </div>
      </div>

      <!-- No Uploads State -->
      <div v-else class="card py-12 text-center">
        <CloudArrowUpIcon class="mx-auto mb-3 h-12 w-12 text-gray-300 dark:text-gray-600" />
        <p class="mb-1 text-base font-medium text-gray-600 dark:text-gray-300">아직 플랫폼에 업로드되지 않았습니다</p>
        <p class="mb-4 text-sm text-gray-400 dark:text-gray-500">
          영상을 플랫폼에 업로드하면 성과 데이터를 확인할 수 있습니다.
        </p>
        <button class="btn-primary" @click="handleReUpload">
          <ArrowPathIcon class="mr-1.5 h-4 w-4" />
          업로드하기
        </button>
      </div>
    </template>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      title="영상 삭제"
      message="이 영상을 삭제하시겠습니까? 연결된 모든 플랫폼 데이터도 함께 삭제됩니다. 이 작업은 되돌릴 수 없습니다."
      confirm-text="삭제"
      cancel-text="취소"
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
import { useVideoStore } from '@/stores/video'
import { videoApi } from '@/api/video'
import { analyticsApi } from '@/api/analytics'
import type { VideoAnalytics } from '@/types/analytics'
import type { Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

// ---- Props ----

const props = defineProps<{
  id: string
}>()

// ---- Router & Store ----

const router = useRouter()
const videoStore = useVideoStore()
const { currentVideo: video, loading } = storeToRefs(videoStore)

// ---- Reactive State ----

const showDeleteModal = ref(false)
const showRecycleModal = ref(false)
const showPreviewModal = ref(false)
const selectedPlatform = ref<Platform | null>(null)
const analyticsData = ref<VideoAnalytics[]>([])
const analyticsLoading = ref(false)

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

/** Format seconds into mm:ss or h:mm:ss */
function formatDuration(seconds: number): string {
  const hrs = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60
  if (hrs > 0) {
    return `${hrs}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

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

/** Format large numbers with Korean units (천/만/억) */
function formatCompactNumber(n: number): string {
  if (n >= 100_000_000) return `${(n / 100_000_000).toFixed(1)}억`
  if (n >= 10_000) return `${(n / 10_000).toFixed(1)}만`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}천`
  return n.toLocaleString('ko-KR')
}

/** Format a change percentage with sign */
function formatChange(value: number): string {
  if (value === 0) return '0%'
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`
}

/** Return Tailwind color class based on positive/negative/zero change */
function changeColorClass(value: number): string {
  if (value > 0) return 'text-green-600'
  if (value < 0) return 'text-red-500'
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
  router.push(`/upload?edit=${props.id}`)
}

function handleRecycle() {
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await videoStore.fetchVideo(Number(props.id))
}

function handleReUpload() {
  router.push(`/upload?reupload=${props.id}`)
}

async function handleRetryTranscode(platform: Platform) {
  try {
    await videoApi.retryTranscode(Number(props.id), platform)
    await videoStore.fetchVideo(Number(props.id))
  } catch {
    // Error handled by API client interceptor
  }
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
  try {
    analyticsData.value = await analyticsApi.videoAnalytics(videoId)
  } catch {
    analyticsData.value = []
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
