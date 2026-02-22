<template>
  <div ref="pageContainerRef" class="relative" :class="{ 'overflow-hidden': isMobile }">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">영상 관리</h1>
      <router-link to="/upload" class="btn-primary inline-flex items-center gap-2">
        <PlusIcon class="h-5 w-5" />
        새 영상 업로드
      </router-link>
    </div>

    <PageGuide title="영상 관리" :items="[
      '제목·태그 검색과 플랫폼(YT/TT/IG/NV)·상태(게시/예약/임시)·날짜 필터로 원하는 영상을 빠르게 찾으세요',
      '그리드/리스트 뷰를 전환하고, 조회수순·좋아요순·최신순으로 정렬할 수 있습니다',
      '선택 모드를 켜면 여러 영상을 체크하여 일괄 삭제·게시·카테고리 변경·AI 배치 처리가 가능합니다',
      '즐겨찾기 필터로 중요 영상만 모아보고, 영상 카드의 우클릭 메뉴에서 수정·재활용·재업로드·삭제를 실행하세요',
      '내보내기 버튼으로 영상 목록을 CSV/Excel로 다운로드할 수 있습니다',
    ]" />

    <!-- Toolbar: Search + Filters + Sort + View Toggle -->
    <div class="card mb-6 space-y-4">
      <!-- Row 1: Search + View Toggle -->
      <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
        <!-- Search -->
        <div class="relative flex-1 tablet:max-w-sm">
          <MagnifyingGlassIcon
            class="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
            aria-hidden="true"
          />
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="제목 또는 태그 검색..."
            aria-label="영상 검색"
            class="input w-full pl-10"
            @input="onSearchInput"
          />
          <button
            v-if="searchKeyword"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
            @click="clearSearch"
          >
            <XMarkIcon class="h-4 w-4" />
          </button>
        </div>

        <!-- Sort + View Toggle + Selection Toggle + Export -->
        <div class="flex items-center gap-3">
          <!-- Sort -->
          <select v-model="sortField" class="input text-sm" @change="onSortChange">
            <option value="createdAt">최신순</option>
            <option value="views">조회수순</option>
            <option value="likes">좋아요순</option>
          </select>

          <!-- Export Button -->
          <ExportDropdown
            v-if="displayedVideos && displayedVideos.content.length > 0"
            :data="exportData"
            :columns="exportColumns"
            :filename-prefix="exportFilename"
          />

          <!-- Selection Mode Toggle -->
          <button
            class="rounded-lg border px-3 py-2 text-sm transition-colors"
            :class="
              selectionMode
                ? 'border-primary-500 bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                : 'border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="toggleSelectionMode"
            :aria-pressed="selectionMode"
            aria-label="선택 모드"
          >
            <CheckIcon class="h-5 w-5" />
          </button>

          <!-- View Mode Toggle -->
          <div class="flex rounded-lg border border-gray-300 dark:border-gray-600" role="group" aria-label="보기 모드">
            <button
              class="rounded-l-lg px-3 py-2 text-sm"
              :class="
                viewMode === 'grid'
                  ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                  : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
              @click="viewMode = 'grid'"
              :aria-pressed="viewMode === 'grid'"
              aria-label="그리드 보기"
            >
              <Squares2X2Icon class="h-5 w-5" />
            </button>
            <button
              class="rounded-r-lg px-3 py-2 text-sm"
              :class="
                viewMode === 'list'
                  ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                  : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
              @click="viewMode = 'list'"
              :aria-pressed="viewMode === 'list'"
              aria-label="리스트 보기"
            >
              <ListBulletIcon class="h-5 w-5" />
            </button>
          </div>
        </div>
      </div>

      <!-- Row 2: Filters -->
      <div class="flex flex-wrap items-center gap-3">
        <!-- Favorites Filter Toggle -->
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors"
          :class="
            filterFavoritesOnly
              ? 'border-amber-500 bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400'
              : 'border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
          "
          @click="filterFavoritesOnly = !filterFavoritesOnly"
          :aria-pressed="filterFavoritesOnly"
          aria-label="즐겨찾기 필터"
        >
          <StarIcon v-if="!filterFavoritesOnly" class="h-3.5 w-3.5" />
          <StarIconSolid v-else class="h-3.5 w-3.5" />
          즐겨찾기
        </button>

        <!-- Platform Filter -->
        <div class="flex items-center gap-1">
          <FunnelIcon class="h-4 w-4 text-gray-400" aria-hidden="true" />
          <div class="flex rounded-lg border border-gray-200 dark:border-gray-700" role="group" aria-label="플랫폼 필터">
            <button
              v-for="pf in platformOptions"
              :key="pf.value ?? 'all'"
              class="px-3 py-1.5 text-xs font-medium first:rounded-l-lg last:rounded-r-lg"
              :class="
                filterPlatform === pf.value
                  ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                  : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
              "
              @click="onPlatformFilter(pf.value)"
            >
              {{ pf.label }}
            </button>
          </div>
        </div>

        <!-- Status Filter -->
        <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
          <button
            v-for="sf in statusOptions"
            :key="sf.value ?? 'all'"
            class="px-3 py-1.5 text-xs font-medium first:rounded-l-lg last:rounded-r-lg"
            :class="
              filterStatus === sf.value
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="onStatusFilter(sf.value)"
          >
            {{ sf.label }}
          </button>
        </div>

        <!-- Date Range -->
        <div class="flex items-center gap-2">
          <CalendarIcon class="h-4 w-4 text-gray-400" />
          <input
            v-model="filterStartDate"
            type="date"
            class="input text-xs"
            @change="onDateChange"
          />
          <span class="text-gray-400">~</span>
          <input
            v-model="filterEndDate"
            type="date"
            class="input text-xs"
            @change="onDateChange"
          />
        </div>

        <!-- Clear Filters -->
        <button
          v-if="hasActiveFilters"
          class="inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
          @click="clearFilters"
        >
          <XMarkIcon class="h-3.5 w-3.5" />
          필터 초기화
        </button>
      </div>

    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" full-page />

    <!-- Empty State -->
    <EmptyState
      v-else-if="!displayedVideos || displayedVideos.content.length === 0"
      :title="hasActiveFilters ? '필터 조건에 맞는 영상이 없습니다' : '아직 영상이 없어요'"
      :description="hasActiveFilters ? '필터를 변경하거나 초기화해 보세요.' : '첫 영상을 업로드하고 YouTube, TikTok, Instagram, Naver Clip에 한 번에 게시해보세요.'"
      :icon="FilmIcon"
      :action-label="hasActiveFilters ? undefined : '첫 영상 업로드하기'"
      :action-to="hasActiveFilters ? undefined : '/upload'"
      secondary-action-label="업로드 가이드 보기"
      :secondary-action-to="hasActiveFilters ? undefined : '/settings'"
    >
      <template v-if="hasActiveFilters" #action>
        <button class="btn-secondary" @click="clearFilters">
          필터 초기화
        </button>
      </template>
    </EmptyState>

    <!-- Content -->
    <template v-else>
      <!-- Pull-to-Refresh Indicator (Mobile Only) -->
      <div
        v-if="isMobile && pullDistance > 0"
        class="flex justify-center py-2 transition-all"
        :style="{ height: pullDistance + 'px' }"
      >
        <ArrowPathRoundedSquareIcon
          class="h-6 w-6 text-primary-600 transition-all"
          :class="{ 'animate-spin': isRefreshing }"
          :style="{
            transform: `rotate(${Math.min(pullDistance * 3, 360)}deg)`,
            opacity: Math.min(pullDistance / 80, 1),
          }"
        />
      </div>

      <!-- Select All (Only shown in selection mode) -->
      <div v-if="selectionMode" class="mb-3 flex items-center gap-2">
        <input
          type="checkbox"
          :checked="isAllSelected"
          :indeterminate="isPartiallySelected"
          class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
          @change="toggleSelectAll"
        />
        <span class="text-sm text-gray-500 dark:text-gray-400">
          전체 선택 ({{ displayedVideos.content.length }}개)
        </span>
        <span class="ml-auto text-sm text-gray-400 dark:text-gray-500">
          총 {{ formatNumber(displayedVideos.totalElements) }}개 영상
        </span>
      </div>

      <!-- Grid View -->
      <div
        v-if="viewMode === 'grid'"
        class="grid grid-cols-1 gap-4 mobile:grid-cols-2 tablet:grid-cols-3 desktop:grid-cols-4"
      >
        <SwipeableCard
          v-for="video in displayedVideos.content"
          :key="video.id"
          :video-id="video.id"
          :ref="(el) => setSwipeableCardRef(video.id, el)"
          @delete="handleSwipeDelete"
          @edit="handleSwipeEdit"
          @close="closeAllSwipeableCards"
        >
          <div
            class="card group relative cursor-pointer overflow-hidden p-0 transition-shadow hover:shadow-lg"
            :class="{ 'ring-2 ring-primary-500 dark:ring-primary-400': selectionMode && selectedIds.has(video.id) }"
          >
            <!-- Checkbox Overlay (Only shown in selection mode) -->
            <div v-if="selectionMode" class="absolute left-3 top-3 z-10">
            <input
              type="checkbox"
              :checked="selectedIds.has(video.id)"
              class="h-4 w-4 rounded border-gray-300 bg-white/80 text-primary-600 focus:ring-primary-500"
              @click.stop
              @change="toggleSelect(video.id)"
            />
          </div>

          <!-- Favorite Button -->
          <div class="absolute z-10" :class="selectionMode ? 'left-3 top-10' : 'left-3 top-3'">
            <FavoriteButton :video-id="video.id" size="sm" />
          </div>

          <!-- Status Badge Overlay -->
          <div class="absolute right-3 top-3 z-10">
            <StatusBadge :status="video.status" />
          </div>

          <!-- Thumbnail -->
          <div
            class="relative aspect-video w-full bg-gray-100 dark:bg-gray-800"
            @click="goToDetail(video.id)"
          >
            <img
              v-if="video.thumbnailUrl"
              :src="video.thumbnailUrl"
              :alt="video.title"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full items-center justify-center">
              <VideoCameraIcon class="h-12 w-12 text-gray-300" />
            </div>
            <!-- Duration Overlay -->
            <span
              v-if="video.duration"
              class="absolute bottom-2 right-2 rounded bg-black/75 px-1.5 py-0.5 text-xs font-medium text-white"
            >
              {{ formatDuration(video.duration) }}
            </span>
          </div>

          <!-- Info -->
          <div class="p-3" @click="goToDetail(video.id)">
            <h3 class="mb-1 line-clamp-2 text-sm font-medium text-gray-900 dark:text-gray-100" :title="video.title">
              {{ video.title }}
            </h3>
            <div class="mb-2 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span>{{ formatDate(video.createdAt) }}</span>
              <span v-if="getTotalViews(video) > 0">
                &middot; 조회수 {{ formatNumber(getTotalViews(video)) }}
              </span>
            </div>
            <!-- Platform Icons -->
            <div class="flex flex-wrap gap-1">
              <PlatformBadge
                v-for="upload in video.uploads"
                :key="upload.id"
                :platform="upload.platform"
              />
            </div>
          </div>

            <!-- Actions Menu (Desktop Only) -->
            <div
              v-if="!isMobile"
              class="absolute right-2 bottom-2 opacity-0 transition-opacity group-hover:opacity-100"
            >
              <button
                class="rounded-full bg-white dark:bg-gray-800 p-1.5 shadow-md hover:bg-gray-50 dark:hover:bg-gray-700"
                @click.stop="openContextMenu(video, $event)"
              >
                <EllipsisVerticalIcon class="h-4 w-4 text-gray-500" />
              </button>
            </div>
          </div>
        </SwipeableCard>
      </div>

      <!-- List View -->
      <div v-else class="card overflow-hidden p-0">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[800px]">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <th v-if="selectionMode" class="w-10 px-4 py-3">
                  <input
                    type="checkbox"
                    :checked="isAllSelected"
                    :indeterminate="isPartiallySelected"
                    class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                    @change="toggleSelectAll"
                  />
                </th>
                <th class="px-4 py-3">영상</th>
                <th class="px-4 py-3">플랫폼</th>
                <th class="px-4 py-3">조회수</th>
                <th class="px-4 py-3">좋아요</th>
                <th class="px-4 py-3">게시일</th>
                <th class="w-10 px-4 py-3"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
              <tr
                v-for="video in displayedVideos.content"
                :key="video.id"
                class="cursor-pointer transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                :class="{ 'bg-primary-50 dark:bg-primary-900/20': selectionMode && selectedIds.has(video.id) }"
              >
                <td v-if="selectionMode" class="px-4 py-3" @click.stop>
                  <div class="flex items-center gap-2">
                    <input
                      type="checkbox"
                      :checked="selectedIds.has(video.id)"
                      class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                      @change="toggleSelect(video.id)"
                    />
                    <FavoriteButton :video-id="video.id" size="sm" />
                  </div>
                </td>
                <td class="px-4 py-3" @click="goToDetail(video.id)">
                  <div class="flex items-center gap-3">
                    <!-- Favorite Button (List view, when not in selection mode) -->
                    <FavoriteButton v-if="!selectionMode" :video-id="video.id" size="sm" />
                    <!-- Small Thumbnail -->
                    <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
                      <img
                        v-if="video.thumbnailUrl"
                        :src="video.thumbnailUrl"
                        :alt="video.title"
                        class="h-full w-full object-cover"
                      />
                      <div v-else class="flex h-full items-center justify-center">
                        <VideoCameraIcon class="h-6 w-6 text-gray-300" />
                      </div>
                    </div>
                    <div class="min-w-0">
                      <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100" :title="video.title">
                        {{ video.title }}
                      </p>
                      <div class="flex items-center gap-2">
                        <StatusBadge :status="video.status" />
                        <span
                          v-if="video.duration"
                          class="text-xs text-gray-400"
                        >
                          {{ formatDuration(video.duration) }}
                        </span>
                      </div>
                    </div>
                  </div>
                </td>
                <td class="px-4 py-3" @click="goToDetail(video.id)">
                  <div class="flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="upload in video.uploads"
                      :key="upload.id"
                      :platform="upload.platform"
                    />
                    <span v-if="video.uploads.length === 0" class="text-xs text-gray-400">-</span>
                  </div>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300" @click="goToDetail(video.id)">
                  {{ formatNumber(getTotalViews(video)) }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-300" @click="goToDetail(video.id)">
                  {{ formatNumber(getTotalLikes(video)) }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400" @click="goToDetail(video.id)">
                  {{ formatDate(video.createdAt) }}
                </td>
                <td class="px-4 py-3" @click.stop>
                  <button
                    class="rounded-full p-1.5 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
                    @click="openContextMenu(video, $event)"
                  >
                    <EllipsisVerticalIcon class="h-5 w-5" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Infinite Scroll Sentinel -->
      <div ref="sentinelRef" class="mt-6 flex justify-center py-4">
        <!-- Loading Spinner -->
        <div
          v-if="isLoadingMore && hasMoreItems"
          class="flex items-center justify-center"
        >
          <div
            class="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 dark:border-gray-700 border-t-primary-600"
          ></div>
        </div>

        <!-- End Message -->
        <p
          v-else-if="!hasMoreItems && paginatedVideos.length > 0"
          class="text-sm text-gray-400 dark:text-gray-500"
        >
          더 이상 영상이 없습니다
        </p>
      </div>
    </template>

    <!-- Scroll to Top Button -->
    <ScrollToTop />

    <!-- Context Menu (Dropdown) -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="fixed inset-0 z-40"
        @click="closeContextMenu"
      />
      <div
        v-if="contextMenu.visible"
        class="fixed z-50 w-48 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 py-1 shadow-lg"
        :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      >
        <button
          class="flex w-full items-center gap-2 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="goToDetail(contextMenu.video!.id)"
        >
          <EyeIcon class="h-4 w-4" />
          상세 보기
        </button>
        <button
          class="flex w-full items-center gap-2 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="handleEdit(contextMenu.video!.id)"
        >
          <PencilSquareIcon class="h-4 w-4" />
          수정
        </button>
        <button
          class="flex w-full items-center gap-2 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="handleRecycle(contextMenu.video!)"
        >
          <ArrowPathRoundedSquareIcon class="h-4 w-4" />
          재활용
        </button>
        <button
          v-if="contextMenu.video?.status === 'FAILED'"
          class="flex w-full items-center gap-2 px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
          @click="handleReupload(contextMenu.video!)"
        >
          <ArrowPathIcon class="h-4 w-4" />
          재업로드
        </button>
        <hr class="my-1 border-gray-100 dark:border-gray-700" />
        <button
          class="flex w-full items-center gap-2 px-4 py-2 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20"
          @click="handleSingleDelete(contextMenu.video!)"
        >
          <TrashIcon class="h-4 w-4" />
          삭제
        </button>
      </div>
    </Teleport>

    <!-- Delete Confirmation Modal -->
    <ConfirmModal
      v-model="showDeleteModal"
      title="영상 삭제"
      :message="deleteMessage"
      confirm-text="삭제"
      :danger="true"
      @confirm="handleConfirmDelete"
    />

    <!-- Recycle Modal -->
    <RecycleModal
      v-if="recycleVideo"
      v-model="showRecycleModal"
      :video="recycleVideo"
      @confirm="handleRecycleConfirm"
    />

    <!-- Bulk Action Bar (Floating at bottom, only shown in selection mode) -->
    <BulkActionBar
      v-if="selectionMode"
      :selected-count="selectedIds.size"
      @delete="handleBulkDelete"
      @publish="handleBulkPublish"
      @change-category="handleBulkChangeCategory"
      @export="handleBulkExport"
      @clear-selection="clearSelection"
      @ai-batch="handleAiBatchStart"
    />

    <!-- AI Batch Confirm Modal -->
    <ConfirmModal
      v-model="showBatchConfirm"
      title="AI 배치 처리 시작"
      :message="batchConfirmMessage"
      confirm-text="시작"
      @confirm="handleStartBatch"
    />

    <!-- Batch Progress Panel -->
    <BatchProgressPanel
      v-if="activeBatchId"
      :batch-id="activeBatchId"
      :operation="batchOperation"
      :visible="!!activeBatchId"
      @close="activeBatchId = null"
      @completed="handleBatchCompleted"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, reactive, type ComponentPublicInstance } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  PlusIcon,
  MagnifyingGlassIcon,
  XMarkIcon,
  Squares2X2Icon,
  ListBulletIcon,
  FunnelIcon,
  CalendarIcon,
  TrashIcon,
  ArrowPathIcon,
  EllipsisVerticalIcon,
  EyeIcon,
  PencilSquareIcon,
  VideoCameraIcon,
  FilmIcon,
  ArrowPathRoundedSquareIcon,
  StarIcon,
  CheckIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarIconSolid } from '@heroicons/vue/24/solid'
import type { Platform } from '@/types/channel'
import type { UploadStatus, Video } from '@/types/video'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import BulkActionBar from '@/components/video/BulkActionBar.vue'
import ExportDropdown from '@/components/analytics/ExportDropdown.vue'
import SwipeableCard from '@/components/video/SwipeableCard.vue'
import RecycleModal from '@/components/video/RecycleModal.vue'
import FavoriteButton from '@/components/video/FavoriteButton.vue'
import { useVideoStore } from '@/stores/video'
import { videoApi } from '@/api/video'
import { useFavoritesStore } from '@/stores/favorites'
import { useNotification } from '@/composables/useNotification'
import { usePullToRefresh } from '@/composables/usePullToRefresh'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import type { ColumnDefinition } from '@/utils/export'
import { formatDateForExport } from '@/utils/export'
import { PLATFORM_CONFIG } from '@/types/channel'
import ScrollToTop from '@/components/common/ScrollToTop.vue'
import BatchProgressPanel from '@/components/ai/BatchProgressPanel.vue'
import { aiApi } from '@/api/ai'
import PageGuide from '@/components/common/PageGuide.vue'
import { BATCH_OPERATIONS } from '@/types/ai'
import type { AiBatchOperation, AiBatchResponse } from '@/types/ai'

const router = useRouter()
const videoStore = useVideoStore()
const favoritesStore = useFavoritesStore()
const { videos, loading } = storeToRefs(videoStore)
const { success, error } = useNotification()

// Mobile detection
const isMobile = useMediaQuery('(max-width: 767px)')

// Pull-to-refresh
const pageContainerRef = ref<HTMLElement | null>(null)
const { pullDistance, isRefreshing } = usePullToRefresh(pageContainerRef, {
  threshold: 80,
  maxDistance: 150,
  onRefresh: async () => {
    await videoStore.fetchVideos(videos.value?.page ?? 0)
  },
})

// Swipeable cards management
type SwipeableCardInstance = { close?: () => void }
const swipeableCardRefs = new Map<number, SwipeableCardInstance>()

function setSwipeableCardRef(videoId: number, el: Element | ComponentPublicInstance | null) {
  if (el) {
    swipeableCardRefs.set(videoId, el as SwipeableCardInstance)
  } else {
    swipeableCardRefs.delete(videoId)
  }
}

function closeAllSwipeableCards(exceptId?: number) {
  swipeableCardRefs.forEach((cardRef, id) => {
    if (id !== exceptId && cardRef?.close) {
      cardRef.close()
    }
  })
}

function handleSwipeDelete(videoId: number) {
  const video = videos.value?.content.find((v) => v.id === videoId)
  if (video) {
    handleSingleDelete(video)
  }
  closeAllSwipeableCards()
}

function handleSwipeEdit(videoId: number) {
  handleEdit(videoId)
  closeAllSwipeableCards()
}

// --- View state ---
const viewMode = ref<'grid' | 'list'>('grid')
const sortField = ref<'createdAt' | 'views' | 'likes'>('createdAt')
const selectionMode = ref(false)

// Toggle selection mode
function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) {
    clearSelection()
  }
}

// --- Filters ---
const searchKeyword = ref('')
const filterPlatform = ref<Platform | undefined>(undefined)
const filterStatus = ref<UploadStatus | undefined>(undefined)
const filterStartDate = ref('')
const filterEndDate = ref('')
const filterFavoritesOnly = ref(false)

const platformOptions: { label: string; value: Platform | undefined }[] = [
  { label: '전체', value: undefined },
  { label: 'YT', value: 'YOUTUBE' },
  { label: 'TT', value: 'TIKTOK' },
  { label: 'IG', value: 'INSTAGRAM' },
  { label: 'NV', value: 'NAVER_CLIP' },
]

const statusOptions: { label: string; value: UploadStatus | undefined }[] = [
  { label: '전체', value: undefined },
  { label: '게시', value: 'PUBLISHED' },
  { label: '예약', value: 'REVIEW' },
  { label: '임시', value: 'DRAFT' },
]

const hasActiveFilters = computed(() => {
  return (
    filterPlatform.value !== undefined ||
    filterStatus.value !== undefined ||
    filterStartDate.value !== '' ||
    filterEndDate.value !== '' ||
    searchKeyword.value !== '' ||
    filterFavoritesOnly.value
  )
})

// Infinite scroll state
const currentPage = ref(0)
const itemsPerPage = 6

// Filtered videos based on favorites
const filteredVideos = computed(() => {
  if (!videos.value) return []

  if (!filterFavoritesOnly.value) return videos.value.content

  // Filter to show only favorited videos
  const favoriteIds = favoritesStore.favorites
  return videos.value.content.filter((v) => favoriteIds.includes(v.id))
})

// Paginated videos for infinite scroll
const paginatedVideos = computed(() => {
  const maxItems = (currentPage.value + 1) * itemsPerPage
  return filteredVideos.value.slice(0, maxItems)
})

// Display wrapper for compatibility with existing code
const displayedVideos = computed(() => {
  if (!videos.value) return null

  return {
    ...videos.value,
    content: paginatedVideos.value,
    totalElements: filteredVideos.value.length,
  }
})

// Check if there are more items to load
const hasMoreItems = computed(() => {
  return paginatedVideos.value.length < filteredVideos.value.length
})

// Load more items
const loadMoreItems = async () => {
  if (hasMoreItems.value) {
    currentPage.value++
  }
}

// Infinite scroll composable
const { sentinelRef, isLoading: isLoadingMore } = useInfiniteScroll(loadMoreItems, {
  threshold: 0.1,
  rootMargin: '100px',
  debounceMs: 200,
})

// --- Selection ---
const selectedIds = ref<Set<number>>(new Set())

const isAllSelected = computed(() => {
  if (!displayedVideos.value || displayedVideos.value.content.length === 0) return false
  return displayedVideos.value.content.every((v) => selectedIds.value.has(v.id))
})

const isPartiallySelected = computed(() => {
  if (!displayedVideos.value || displayedVideos.value.content.length === 0) return false
  const selected = displayedVideos.value.content.filter((v) => selectedIds.value.has(v.id)).length
  return selected > 0 && selected < displayedVideos.value.content.length
})

function toggleSelectAll() {
  if (!displayedVideos.value) return
  if (isAllSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(displayedVideos.value.content.map((v) => v.id))
  }
}

function toggleSelect(id: number) {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
}

// --- Search with debounce ---
let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    applyFilters()
  }, 300)
}

function clearSearch() {
  searchKeyword.value = ''
  applyFilters()
}

// --- Filter handlers ---
function onPlatformFilter(value: Platform | undefined) {
  filterPlatform.value = value
  applyFilters()
}

function onStatusFilter(value: UploadStatus | undefined) {
  filterStatus.value = value
  applyFilters()
}

function onDateChange() {
  applyFilters()
}

function onSortChange() {
  videoStore.setSort(sortField.value, 'DESC')
  currentPage.value = 0
}

function clearFilters() {
  searchKeyword.value = ''
  filterPlatform.value = undefined
  filterStatus.value = undefined
  filterStartDate.value = ''
  filterEndDate.value = ''
  filterFavoritesOnly.value = false
  sortField.value = 'createdAt'
  applyFilters()
}

function applyFilters() {
  videoStore.setFilter({
    platform: filterPlatform.value,
    status: filterStatus.value,
    startDate: filterStartDate.value || undefined,
    endDate: filterEndDate.value || undefined,
    keyword: searchKeyword.value || undefined,
  })
  selectedIds.value = new Set()
  // Reset pagination when filters change
  currentPage.value = 0
}

// --- Context Menu ---
const contextMenu = reactive<{
  visible: boolean
  x: number
  y: number
  video: Video | null
}>({
  visible: false,
  x: 0,
  y: 0,
  video: null,
})

function openContextMenu(video: Video, event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  const menuWidth = 192
  const menuHeight = 200
  let x = event.clientX
  let y = event.clientY
  if (x + menuWidth > window.innerWidth) x = window.innerWidth - menuWidth - 8
  if (y + menuHeight > window.innerHeight) y = window.innerHeight - menuHeight - 8
  contextMenu.visible = true
  contextMenu.x = x
  contextMenu.y = y
  contextMenu.video = video
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.video = null
}

// --- Navigation ---
function goToDetail(id: number) {
  closeContextMenu()
  router.push(`/videos/${id}`)
}

function handleEdit(id: number) {
  closeContextMenu()
  router.push(`/videos/${id}/edit`)
}

// --- Recycle ---
const showRecycleModal = ref(false)
const recycleVideo = ref<Video | null>(null)

function handleRecycle(video: Video) {
  closeContextMenu()
  recycleVideo.value = video
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await videoStore.fetchVideos(videos.value?.page ?? 0)
}

// --- Delete ---
const showDeleteModal = ref(false)
const deleteTargetIds = ref<number[]>([])

const deleteMessage = computed(() => {
  const count = deleteTargetIds.value.length
  return `선택한 ${count}개의 영상을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`
})

function handleSingleDelete(video: Video) {
  closeContextMenu()
  deleteTargetIds.value = [video.id]
  showDeleteModal.value = true
}

async function handleConfirmDelete() {
  for (const id of deleteTargetIds.value) {
    await videoStore.deleteVideo(id)
  }
  deleteTargetIds.value = []
  selectedIds.value = new Set()
  await videoStore.fetchVideos(videos.value?.page ?? 0)
}

// Bulk delete
watch(
  () => showDeleteModal.value,
  (newVal) => {
    if (newVal && deleteTargetIds.value.length === 0 && selectedIds.value.size > 0) {
      deleteTargetIds.value = [...selectedIds.value]
    }
  },
)

// --- Re-upload ---
function handleReupload(video: Video) {
  closeContextMenu()
  router.push(`/videos/${video.id}/publish`)
}

// handleBulkReupload reserved for future API integration

// Reset pagination when favorites filter changes
watch(filterFavoritesOnly, () => {
  currentPage.value = 0
})

// --- Formatters ---
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
})

function formatDate(dateStr: string): string {
  return dateFormatter.format(new Date(dateStr))
}

function formatNumber(num: number): string {
  if (num >= 1_000_000) {
    return (num / 1_000_000).toFixed(1).replace(/\.0$/, '') + 'M'
  }
  if (num >= 1_000) {
    return (num / 1_000).toFixed(1).replace(/\.0$/, '') + 'K'
  }
  return num.toLocaleString('ko-KR')
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

// --- Video stats helpers ---
// Note: These compute aggregate values from platform uploads.
// The actual view/like counts would come from analytics data linked to uploads.
// For now we use a placeholder that returns 0 if not available on the Video model.
function getTotalViews(video: Video): number {
  // Views would typically come from analytics; using 0 as the Video type
  // doesn't carry aggregate view counts directly.
  return (video as Video & { totalViews?: number }).totalViews ?? 0
}

function getTotalLikes(video: Video): number {
  return (video as Video & { totalLikes?: number }).totalLikes ?? 0
}

// --- Bulk Actions (with toast notifications for now) ---
function handleBulkDelete() {
  deleteTargetIds.value = [...selectedIds.value]
  showDeleteModal.value = true
}

async function handleBulkPublish() {
  const count = selectedIds.value.size
  try {
    // Publish all selected videos (individual publish calls)
    await Promise.all(
      [...selectedIds.value].map((id) =>
        videoApi.publish(id, { platforms: [] }),
      ),
    )
    success(`${count}개 영상을 플랫폼에 게시합니다`)
  } catch {
    error('일부 영상 게시에 실패했습니다.')
  }
  clearSelection()
}

async function handleBulkChangeCategory(tags: string[]) {
  const count = selectedIds.value.size
  try {
    await Promise.all(
      [...selectedIds.value].map((id) =>
        videoApi.update(id, { tags }),
      ),
    )
    success(`${count}개 영상의 카테고리가 변경되었습니다: ${tags.join(', ')}`)
    await videoStore.fetchVideos()
  } catch {
    error('일부 영상 카테고리 변경에 실패했습니다.')
  }
  clearSelection()
}

function handleBulkExport() {
  const count = selectedIds.value.size
  // Export is a client-side operation (CSV download) — no API needed
  success(`${count}개 영상을 내보냅니다`)
  clearSelection()
}

// --- Export Data Preparation ---
interface VideoExportData {
  [key: string]: string | number
  title: string
  status: string
  platforms: string
  views: number
  likes: number
  createdAt: string
}

const exportData = computed<VideoExportData[]>(() => {
  if (!displayedVideos.value) return []

  return displayedVideos.value.content.map((video) => ({
    title: video.title,
    status: getStatusLabel(video.status),
    platforms: video.uploads
      .map((u) => PLATFORM_CONFIG[u.platform]?.label ?? u.platform)
      .join(', ') || '-',
    views: getTotalViews(video),
    likes: getTotalLikes(video),
    createdAt: video.createdAt,
  }))
})

const exportColumns = computed<ColumnDefinition<Record<string, unknown>>[]>(() => [
  { key: 'title', header: '제목' },
  { key: 'status', header: '상태' },
  { key: 'platforms', header: '플랫폼' },
  { key: 'views', header: '조회수' },
  { key: 'likes', header: '좋아요' },
  { key: 'createdAt', header: '게시일', formatter: (val) => formatDateForExport(val as string) },
])

const exportFilename = computed(() => {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')
  return `onGo_videos_${year}-${month}-${day}`
})

function getStatusLabel(status: UploadStatus): string {
  const statusLabels: Record<UploadStatus, string> = {
    DRAFT: '임시저장',
    UPLOADING: '업로드 중',
    PROCESSING: '처리 중',
    REVIEW: '검토 중',
    PUBLISHED: '게시됨',
    FAILED: '실패',
    REJECTED: '거부됨',
  }
  return statusLabels[status] ?? status
}

// --- AI Batch ---
const showBatchConfirm = ref(false)
const batchOperation = ref<AiBatchOperation>('GENERATE_META')
const activeBatchId = ref<string | null>(null)

const batchConfirmMessage = computed(() => {
  const op = BATCH_OPERATIONS.find(o => o.key === batchOperation.value)
  const totalCost = (op?.creditCost ?? 0) * selectedIds.value.size
  return `선택한 ${selectedIds.value.size}개 영상에 '${op?.label ?? batchOperation.value}' 작업을 실행합니다.\n예상 크레딧: ${totalCost}`
})

function handleAiBatchStart(operation: AiBatchOperation) {
  batchOperation.value = operation
  showBatchConfirm.value = true
}

async function handleStartBatch() {
  try {
    const response = await aiApi.startBatch({
      videoIds: [...selectedIds.value],
      operation: batchOperation.value,
    })
    activeBatchId.value = response.batchId
    showBatchConfirm.value = false
    clearSelection()
    success('AI 배치 처리가 시작되었습니다')
  } catch {
    error('AI 배치 처리 시작에 실패했습니다')
  }
}

function handleBatchCompleted(_batch: AiBatchResponse) {
  success('AI 배치 처리가 완료되었습니다')
  videoStore.fetchVideos()
}

// --- Lifecycle ---
onMounted(() => {
  // Check for favorites query parameter
  const route = router.currentRoute.value
  if (route.query.favorites === 'true') {
    filterFavoritesOnly.value = true
  }

  videoStore.fetchVideos()
})

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout)
})
</script>
