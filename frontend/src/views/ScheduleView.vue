<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">예약 관리</h1>

      <div class="flex items-center gap-3">
        <!-- Sort dropdown (only in list view) -->
        <select
          v-if="calendarView === 'list'"
          v-model="sortMode"
          class="input py-1.5 text-sm"
        >
          <option value="date">날짜순</option>
          <option value="platform">플랫폼순</option>
          <option value="status">상태순</option>
          <option value="manual">수동 정렬</option>
        </select>

        <!-- Filter toggle -->
        <button
          class="btn-secondary flex items-center gap-1.5"
          @click="showFilters = !showFilters"
        >
          <FunnelIcon class="h-4 w-4" />
          <span class="hidden tablet:inline">필터</span>
          <span
            v-if="activeFilterCount > 0"
            class="flex h-5 w-5 items-center justify-center rounded-full bg-primary-600 text-xs text-white"
          >
            {{ activeFilterCount }}
          </span>
        </button>

        <!-- View toggle -->
        <div class="flex rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <button
            v-for="view in viewOptions"
            :key="view.value"
            class="px-3 py-1.5 text-sm font-medium transition-colors first:rounded-l-lg last:rounded-r-lg"
            :class="
              calendarView === view.value
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
            "
            @click="setCalendarView(view.value)"
          >
            {{ view.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- Filters panel -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="-translate-y-2 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="-translate-y-2 opacity-0"
    >
      <div v-if="showFilters" class="card mb-4">
        <div class="flex flex-wrap items-center gap-4">
          <!-- Platform filter -->
          <div>
            <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">플랫폼</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="p in platformOptions"
                :key="p.value"
                class="rounded-full px-2.5 py-1 text-xs font-medium transition-colors"
                :class="
                  filterPlatform === p.value
                    ? 'text-white'
                    : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
                "
                :style="filterPlatform === p.value ? { backgroundColor: p.color } : {}"
                @click="filterPlatform = filterPlatform === p.value ? null : p.value"
              >
                {{ p.label }}
              </button>
            </div>
          </div>

          <!-- Status filter -->
          <div>
            <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">상태</label>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="s in statusOptions"
                :key="s.value"
                class="rounded-full px-2.5 py-1 text-xs font-medium transition-colors"
                :class="[
                  filterStatus === s.value ? s.activeClass : 'bg-gray-100 text-gray-600 hover:bg-gray-200',
                ]"
                @click="filterStatus = filterStatus === s.value ? null : s.value"
              >
                {{ s.label }}
              </button>
            </div>
          </div>

          <!-- Clear filters -->
          <button
            v-if="activeFilterCount > 0"
            class="ml-auto text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
            @click="clearFilters"
          >
            필터 초기화
          </button>
        </div>
      </div>
    </Transition>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" full-page />

    <!-- Empty State (when no schedules at all) -->
    <EmptyState
      v-else-if="schedules.length === 0"
      title="예약된 영상이 없어요"
      description="영상 업로드 시 예약 게시를 설정하면 이곳에서 한눈에 관리할 수 있어요."
      :icon="CalendarDaysIcon"
      action-label="영상 업로드하기"
      action-to="/upload"
    />

    <!-- Calendar navigation + content -->
    <template v-else>
      <!-- Navigation bar -->
      <div class="card mb-4 flex items-center justify-between">
        <button
          class="rounded-lg p-2 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
          @click="navigatePrev"
        >
          <ChevronLeftIcon class="h-5 w-5" />
        </button>

        <div class="flex items-center gap-3">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ navigationTitle }}</h2>
          <button
            class="rounded-md bg-gray-100 dark:bg-gray-800 px-2.5 py-1 text-xs font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700"
            @click="goToToday"
          >
            오늘
          </button>
        </div>

        <button
          class="rounded-lg p-2 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
          @click="navigateNext"
        >
          <ChevronRightIcon class="h-5 w-5" />
        </button>
      </div>

      <!-- Month view -->
      <div v-if="calendarView === 'month'" class="card overflow-hidden p-0">
        <!-- Day headers -->
        <div class="grid grid-cols-7 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
          <div
            v-for="day in dayLabels"
            :key="day"
            class="px-2 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400"
          >
            {{ day }}
          </div>
        </div>

        <!-- Calendar grid: 6 rows x 7 cols -->
        <div class="grid grid-cols-7">
          <div
            v-for="(cell, index) in monthCells"
            :key="index"
            class="relative min-h-[100px] cursor-pointer border-b border-r border-gray-100 dark:border-gray-700 p-1.5 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700 tablet:min-h-[120px]"
            :class="{
              'bg-blue-50/40': cell.isToday,
              'opacity-40': !cell.isCurrentMonth,
            }"
            @click="onCellClick(cell)"
          >
            <!-- Date number -->
            <div class="mb-1 flex items-center justify-between">
              <span
                class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium"
                :class="cell.isToday ? 'bg-primary-600 text-white' : 'text-gray-700 dark:text-gray-300'"
              >
                {{ cell.day }}
              </span>
              <button
                v-if="cell.isCurrentMonth"
                class="hidden rounded p-0.5 text-gray-300 dark:text-gray-600 hover:bg-gray-200 dark:hover:bg-gray-700 hover:text-gray-500 dark:hover:text-gray-400 group-hover:block"
                @click.stop="openQuickAdd(cell.date)"
              >
                <PlusIcon class="h-3.5 w-3.5" />
              </button>
            </div>

            <!-- Scheduled items -->
            <div class="space-y-0.5">
              <div
                v-for="schedule in getSchedulesForDate(cell.dateStr)"
                :key="schedule.id"
                class="group/item flex items-center gap-1 rounded px-1 py-0.5 text-xs transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
                @click.stop="openScheduleDetail(schedule)"
              >
                <!-- Platform color dots -->
                <div class="flex shrink-0 gap-0.5">
                  <span
                    v-for="sp in schedule.platforms.slice(0, 2)"
                    :key="sp.platform"
                    class="h-1.5 w-1.5 rounded-full"
                    :style="{ backgroundColor: getPlatformColor(sp.platform) }"
                  />
                  <span
                    v-if="schedule.platforms.length > 2"
                    class="text-[10px] leading-none text-gray-400"
                  >
                    +{{ schedule.platforms.length - 2 }}
                  </span>
                </div>

                <!-- Thumbnail (hidden on mobile) -->
                <img
                  v-if="schedule.thumbnailUrl"
                  :src="schedule.thumbnailUrl"
                  :alt="schedule.videoTitle"
                  class="hidden h-4 w-6 shrink-0 rounded-sm object-cover tablet:block"
                />

                <!-- Title -->
                <span class="truncate text-gray-700 dark:text-gray-300">
                  {{ schedule.videoTitle }}
                </span>
              </div>

              <!-- Overflow count -->
              <div
                v-if="getSchedulesForDate(cell.dateStr).length > 3"
                class="px-1 text-[10px] font-medium text-gray-400 dark:text-gray-500"
              >
                +{{ getSchedulesForDate(cell.dateStr).length - 3 }} 더보기
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Week view -->
      <div v-else-if="calendarView === 'week'" class="card overflow-x-auto p-0">
        <!-- Day headers -->
        <div class="grid min-w-[700px] grid-cols-[60px_repeat(7,1fr)] border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
          <div class="border-r border-gray-200 dark:border-gray-700 px-2 py-2.5" />
          <div
            v-for="day in weekDays"
            :key="day.dateStr"
            class="px-2 py-2.5 text-center"
            :class="{ 'bg-blue-50/60': day.isToday }"
          >
            <div class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ day.dayLabel }}</div>
            <div
              class="mt-0.5 inline-flex h-7 w-7 items-center justify-center rounded-full text-sm font-semibold"
              :class="day.isToday ? 'bg-primary-600 text-white' : 'text-gray-900 dark:text-gray-100'"
            >
              {{ day.day }}
            </div>
          </div>
        </div>

        <!-- Time rows -->
        <div class="min-w-[700px]">
          <div
            v-for="hour in hours"
            :key="hour"
            class="grid grid-cols-[60px_repeat(7,1fr)] border-b border-gray-100 dark:border-gray-700"
          >
            <!-- Time label -->
            <div class="border-r border-gray-200 dark:border-gray-700 px-2 py-3 text-right text-xs text-gray-400 dark:text-gray-500">
              {{ formatHour(hour) }}
            </div>

            <!-- Day columns -->
            <div
              v-for="day in weekDays"
              :key="day.dateStr"
              class="relative min-h-[48px] cursor-pointer border-r border-gray-100 dark:border-gray-700 px-1 py-0.5 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
              :class="{ 'bg-blue-50/30': day.isToday }"
              @click="openQuickAdd(day.date, hour)"
            >
              <div
                v-for="schedule in getSchedulesForHour(day.dateStr, hour)"
                :key="schedule.id"
                class="mb-0.5 flex items-center gap-1 rounded px-1.5 py-1 text-xs transition-shadow hover:shadow-sm"
                :style="{
                  backgroundColor: getScheduleBgColor(schedule),
                  borderLeft: `3px solid ${getScheduleBorderColor(schedule)}`,
                }"
                @click.stop="openScheduleDetail(schedule)"
              >
                <span class="truncate font-medium text-gray-800 dark:text-gray-200">
                  {{ schedule.videoTitle }}
                </span>
                <span class="shrink-0 text-gray-500 dark:text-gray-400">
                  {{ formatTime(schedule.scheduledAt) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- List view -->
      <div v-else class="card overflow-x-auto p-0">
        <!-- Empty state -->
        <div v-if="filteredSchedules.length === 0" class="px-6 py-12 text-center">
          <CalendarIcon class="mx-auto mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">예약된 일정이 없습니다.</p>
        </div>

        <table v-else class="w-full min-w-[640px]">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900">
              <th v-if="sortMode === 'manual'" class="w-8" />
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                일시
              </th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                영상
              </th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                플랫폼
              </th>
              <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                상태
              </th>
              <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
                작업
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <DraggableScheduleItem
              v-for="(schedule, index) in sortedListSchedules"
              :key="schedule.id"
              :item="schedule"
              :index="index"
              :is-draggable="sortMode === 'manual'"
              :is-dragging="dragIndex === index"
              :is-drop-target="dropIndex === index"
              :show-drop-indicator="isDragging && dropIndex === index && dragIndex !== index"
              :drop-position="dragIndex !== null && dragIndex < index ? 'bottom' : 'top'"
              @dragstart="dragHandlers.handleDragStart"
              @dragover="dragHandlers.handleDragOver"
              @dragend="dragHandlers.handleDragEnd"
              @drop="dragHandlers.handleDrop"
            >
              <tr class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700">
                <!-- Date/time -->
                <td class="whitespace-nowrap px-4 py-3">
                  <div class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ formatDate(schedule.scheduledAt) }}
                  </div>
                  <div class="text-xs text-gray-500 dark:text-gray-400">
                    {{ formatTime(schedule.scheduledAt) }}
                  </div>
                </td>

                <!-- Video title + thumbnail -->
                <td class="px-4 py-3">
                  <div class="flex items-center gap-3">
                    <img
                      v-if="schedule.thumbnailUrl"
                      :src="schedule.thumbnailUrl"
                      :alt="schedule.videoTitle"
                      class="h-9 w-14 shrink-0 rounded object-cover"
                    />
                    <div
                      v-else
                      class="flex h-9 w-14 shrink-0 items-center justify-center rounded bg-gray-100 dark:bg-gray-800"
                    >
                      <CalendarIcon class="h-4 w-4 text-gray-400" />
                    </div>
                    <span class="max-w-[200px] truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ schedule.videoTitle }}
                    </span>
                  </div>
                </td>

                <!-- Platforms -->
                <td class="px-4 py-3">
                  <div class="flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="sp in schedule.platforms"
                      :key="sp.platform"
                      :platform="sp.platform"
                    />
                  </div>
                </td>

                <!-- Status -->
                <td class="px-4 py-3">
                  <span :class="getStatusBadgeClass(schedule.status)">
                    {{ getStatusLabel(schedule.status) }}
                  </span>
                </td>

                <!-- Actions -->
                <td class="whitespace-nowrap px-4 py-3 text-right">
                  <div class="flex items-center justify-end gap-1">
                    <button
                      v-if="schedule.status === 'SCHEDULED'"
                      class="rounded p-1.5 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
                      title="수정"
                      @click="openScheduleDetail(schedule)"
                    >
                      <PencilSquareIcon class="h-4 w-4" />
                    </button>
                    <button
                      v-if="schedule.status === 'SCHEDULED'"
                      class="rounded p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-500"
                      title="취소"
                      @click="confirmCancel(schedule)"
                    >
                      <XMarkIcon class="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            </DraggableScheduleItem>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Quick add modal -->
    <Teleport to="body">
      <div v-if="showQuickAdd" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="showQuickAdd = false" />
        <div class="relative w-full max-w-md rounded-xl bg-white dark:bg-gray-800 p-6 shadow-xl">
          <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">예약 추가</h3>

          <div class="space-y-4">
            <!-- Video ID -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">영상 ID</label>
              <input
                v-model.number="quickAddForm.videoId"
                type="number"
                class="input w-full"
                placeholder="영상 ID를 입력하세요"
              />
            </div>

            <!-- Scheduled date/time -->
            <div>
              <div class="mb-1 flex items-center justify-between">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">예약 일시</label>
                <button
                  v-if="recommendedSlot"
                  class="flex items-center gap-1 rounded-full bg-green-100 dark:bg-green-900/30 px-2 py-0.5 text-xs font-medium text-green-700 dark:text-green-400 transition-colors hover:bg-green-200 dark:hover:bg-green-900/50"
                  @click="applyRecommendedTime"
                >
                  <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  추천: {{ recommendedSlot.dayLabel }} {{ recommendedSlot.timeLabel }}
                </button>
              </div>
              <input
                v-model="quickAddForm.scheduledAt"
                type="datetime-local"
                class="input w-full"
              />
            </div>

            <!-- Platforms -->
            <div>
              <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">플랫폼 선택</label>
              <div class="flex flex-wrap gap-2">
                <label
                  v-for="p in allPlatforms"
                  :key="p"
                  class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 transition-colors"
                  :class="
                    quickAddForm.platforms.includes(p)
                      ? 'border-primary-300 bg-primary-50 dark:bg-primary-900/20 dark:border-primary-800'
                      : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
                  "
                >
                  <input
                    v-model="quickAddForm.platforms"
                    type="checkbox"
                    :value="p"
                    class="sr-only"
                  />
                  <span
                    class="h-2.5 w-2.5 rounded-full"
                    :style="{ backgroundColor: getPlatformColor(p) }"
                  />
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                    {{ PLATFORM_CONFIG[p].label }}
                  </span>
                </label>
              </div>
            </div>
          </div>

          <div class="mt-6 flex justify-end gap-3">
            <button class="btn-secondary" @click="showQuickAdd = false">취소</button>
            <button
              class="btn-primary"
              :disabled="!isQuickAddValid"
              @click="submitQuickAdd"
            >
              예약 추가
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Schedule detail modal -->
    <Teleport to="body">
      <div v-if="selectedSchedule" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="fixed inset-0 bg-black/50" @click="selectedSchedule = null" />
        <div class="relative w-full max-w-lg rounded-xl bg-white dark:bg-gray-800 p-6 shadow-xl">
          <div class="mb-4 flex items-start justify-between">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">예약 상세</h3>
            <button
              class="rounded-lg p-1 text-gray-400 dark:text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 hover:text-gray-600 dark:hover:text-gray-300"
              @click="selectedSchedule = null"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <div class="space-y-4">
            <!-- Thumbnail + title -->
            <div class="flex items-center gap-3">
              <img
                v-if="selectedSchedule.thumbnailUrl"
                :src="selectedSchedule.thumbnailUrl"
                :alt="selectedSchedule.videoTitle"
                class="h-14 w-20 shrink-0 rounded-lg object-cover"
              />
              <div
                v-else
                class="flex h-14 w-20 shrink-0 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-800"
              >
                <CalendarIcon class="h-6 w-6 text-gray-400" />
              </div>
              <div>
                <p class="font-medium text-gray-900 dark:text-gray-100">{{ selectedSchedule.videoTitle }}</p>
                <span :class="getStatusBadgeClass(selectedSchedule.status)">
                  {{ getStatusLabel(selectedSchedule.status) }}
                </span>
              </div>
            </div>

            <!-- Schedule info -->
            <div class="rounded-lg bg-gray-50 dark:bg-gray-900 p-4">
              <div class="grid gap-3 text-sm">
                <div class="flex justify-between">
                  <span class="text-gray-500 dark:text-gray-400">예약 일시</span>
                  <span class="font-medium text-gray-900 dark:text-gray-100">
                    {{ formatDate(selectedSchedule.scheduledAt) }}
                    {{ formatTime(selectedSchedule.scheduledAt) }}
                  </span>
                </div>
                <div class="flex justify-between">
                  <span class="text-gray-500 dark:text-gray-400">생성일</span>
                  <span class="text-gray-700 dark:text-gray-300">{{ formatDate(selectedSchedule.createdAt) }}</span>
                </div>
              </div>
            </div>

            <!-- Per-platform status -->
            <div>
              <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">플랫폼별 상태</h4>
              <div class="space-y-2">
                <div
                  v-for="sp in selectedSchedule.platforms"
                  :key="sp.platform"
                  class="flex items-center justify-between rounded-lg border border-gray-100 dark:border-gray-700 px-3 py-2"
                >
                  <div class="flex items-center gap-2">
                    <span
                      class="h-2.5 w-2.5 rounded-full"
                      :style="{ backgroundColor: getPlatformColor(sp.platform) }"
                    />
                    <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                      {{ PLATFORM_CONFIG[sp.platform].label }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatTime(sp.scheduledAt) }}</span>
                    <span :class="getStatusBadgeClass(sp.status)">
                      {{ getStatusLabel(sp.status) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div
            v-if="selectedSchedule.status === 'SCHEDULED'"
            class="mt-6 flex justify-end gap-3"
          >
            <button class="btn-danger" @click="confirmCancel(selectedSchedule)">
              예약 취소
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Cancel confirm modal -->
    <ConfirmModal
      v-model="showCancelModal"
      title="예약 취소"
      message="이 예약을 취소하시겠습니까? 취소된 예약은 복원할 수 없습니다."
      confirm-text="취소하기"
      danger
      @confirm="handleCancel"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  PlusIcon,
  CalendarIcon,
  CalendarDaysIcon,
  FunnelIcon,
  PencilSquareIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import DraggableScheduleItem from '@/components/schedule/DraggableScheduleItem.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useNotification } from '@/composables/useNotification'
import { useDragAndDrop } from '@/composables/useDragAndDrop'
import { analyticsApi } from '@/api/analytics'
import type { Schedule, ScheduleStatus, CalendarView } from '@/types/schedule'
import type { Platform } from '@/types/channel'
import type { OptimalTimeSlot } from '@/types/analytics'
import { PLATFORM_CONFIG } from '@/types/channel'

// ─── Store ───────────────────────────────────────────────
const scheduleStore = useScheduleStore()
const { schedules, loading, calendarView } = storeToRefs(scheduleStore)
const { fetchSchedules, createSchedule, cancelSchedule, setCalendarView } = scheduleStore

// ─── Notification ────────────────────────────────────────
const { success } = useNotification()

// ─── View options ────────────────────────────────────────
const viewOptions: { value: CalendarView; label: string }[] = [
  { value: 'month', label: '월간' },
  { value: 'week', label: '주간' },
  { value: 'list', label: '리스트' },
]

const dayLabels = ['일', '월', '화', '수', '목', '금', '토']

const hours = Array.from({ length: 24 }, (_, i) => i)

const allPlatforms: Platform[] = ['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP']

// ─── Current date navigation ─────────────────────────────
const currentDate = ref(new Date())

const today = new Date()
today.setHours(0, 0, 0, 0)

// ─── Filters ─────────────────────────────────────────────
const showFilters = ref(false)
const filterPlatform = ref<Platform | null>(null)
const filterStatus = ref<ScheduleStatus | null>(null)

const platformOptions = [
  { value: 'YOUTUBE' as Platform, label: 'YouTube', color: '#FF0000' },
  { value: 'TIKTOK' as Platform, label: 'TikTok', color: '#000000' },
  { value: 'INSTAGRAM' as Platform, label: 'Instagram', color: '#E1306C' },
  { value: 'NAVER_CLIP' as Platform, label: 'Naver Clip', color: '#03C75A' },
]

const statusOptions = [
  { value: 'SCHEDULED' as ScheduleStatus, label: '예약됨', activeClass: 'badge-blue' },
  { value: 'PUBLISHED' as ScheduleStatus, label: '게시완료', activeClass: 'badge-success' },
  { value: 'FAILED' as ScheduleStatus, label: '실패', activeClass: 'badge-danger' },
  { value: 'CANCELLED' as ScheduleStatus, label: '취소됨', activeClass: 'badge-gray' },
]

const activeFilterCount = computed(() => {
  let count = 0
  if (filterPlatform.value) count++
  if (filterStatus.value) count++
  return count
})

function clearFilters() {
  filterPlatform.value = null
  filterStatus.value = null
}

// ─── Filtered schedules ──────────────────────────────────
const filteredSchedules = computed(() => {
  return schedules.value.filter((s) => {
    if (filterPlatform.value) {
      const hasPlatform = s.platforms.some((p) => p.platform === filterPlatform.value)
      if (!hasPlatform) return false
    }
    if (filterStatus.value && s.status !== filterStatus.value) {
      return false
    }
    return true
  })
})

// ─── Sort mode ───────────────────────────────────────────
type SortMode = 'date' | 'platform' | 'status' | 'manual'
const sortMode = ref<SortMode>('date')
const manualOrder = ref<Schedule[]>([])

const sortedListSchedules = computed(() => {
  const items = [...filteredSchedules.value]

  if (sortMode.value === 'manual' && manualOrder.value.length > 0) {
    // Return items in manual order, preserving filtered items
    return manualOrder.value.filter(schedule =>
      items.some(item => item.id === schedule.id)
    )
  }

  if (sortMode.value === 'date') {
    return items.sort(
      (a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime(),
    )
  }

  if (sortMode.value === 'platform') {
    return items.sort((a, b) => {
      const aPlatform = a.platforms[0]?.platform ?? ''
      const bPlatform = b.platforms[0]?.platform ?? ''
      return aPlatform.localeCompare(bPlatform)
    })
  }

  if (sortMode.value === 'status') {
    const statusOrder: Record<ScheduleStatus, number> = {
      SCHEDULED: 1,
      PUBLISHED: 2,
      FAILED: 3,
      CANCELLED: 4,
    }
    return items.sort((a, b) => statusOrder[a.status] - statusOrder[b.status])
  }

  return items
})

// ─── Drag and drop ───────────────────────────────────────
const { dragHandlers, isDragging, dragIndex, dropIndex } = useDragAndDrop(
  computed(() => sortedListSchedules.value),
  (reorderedItems: Schedule[]) => {
    manualOrder.value = reorderedItems
    success('일정이 변경되었습니다')
  }
)

// Reset manual order when sort mode changes
watch(sortMode, (newMode) => {
  if (newMode === 'manual' && manualOrder.value.length === 0) {
    // Initialize manual order with current sorted items
    manualOrder.value = [...sortedListSchedules.value]
  }
})

// ─── Navigation ──────────────────────────────────────────
const navigationTitle = computed(() => {
  const d = currentDate.value
  if (calendarView.value === 'month') {
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월`
  }
  if (calendarView.value === 'week') {
    const weekStart = getWeekStart(d)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekEnd.getDate() + 6)
    const startMonth = weekStart.getMonth() + 1
    const endMonth = weekEnd.getMonth() + 1
    if (startMonth === endMonth) {
      return `${weekStart.getFullYear()}년 ${startMonth}월 ${weekStart.getDate()}일 - ${weekEnd.getDate()}일`
    }
    return `${startMonth}월 ${weekStart.getDate()}일 - ${endMonth}월 ${weekEnd.getDate()}일`
  }
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월`
})

function navigatePrev() {
  const d = new Date(currentDate.value)
  if (calendarView.value === 'month') {
    d.setMonth(d.getMonth() - 1)
  } else if (calendarView.value === 'week') {
    d.setDate(d.getDate() - 7)
  } else {
    d.setMonth(d.getMonth() - 1)
  }
  currentDate.value = d
}

function navigateNext() {
  const d = new Date(currentDate.value)
  if (calendarView.value === 'month') {
    d.setMonth(d.getMonth() + 1)
  } else if (calendarView.value === 'week') {
    d.setDate(d.getDate() + 7)
  } else {
    d.setMonth(d.getMonth() + 1)
  }
  currentDate.value = d
}

function goToToday() {
  currentDate.value = new Date()
}

// ─── Month view calculations ─────────────────────────────
interface MonthCell {
  date: Date
  dateStr: string
  day: number
  isCurrentMonth: boolean
  isToday: boolean
}

const monthCells = computed<MonthCell[]>(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()

  // First day of the month
  const firstDay = new Date(year, month, 1)
  // Day of week for first day (0=Sun)
  const startDow = firstDay.getDay()

  // Last day of the month
  const lastDay = new Date(year, month + 1, 0)
  const totalDays = lastDay.getDate()

  const cells: MonthCell[] = []

  // Fill leading days from previous month
  const prevMonthLast = new Date(year, month, 0)
  for (let i = startDow - 1; i >= 0; i--) {
    const d = new Date(year, month - 1, prevMonthLast.getDate() - i)
    cells.push(createCell(d, false))
  }

  // Current month days
  for (let day = 1; day <= totalDays; day++) {
    const d = new Date(year, month, day)
    cells.push(createCell(d, true))
  }

  // Fill trailing days to complete 6 rows (42 cells)
  const remaining = 42 - cells.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month + 1, i)
    cells.push(createCell(d, false))
  }

  return cells
})

function createCell(date: Date, isCurrentMonth: boolean): MonthCell {
  return {
    date,
    dateStr: toDateStr(date),
    day: date.getDate(),
    isCurrentMonth,
    isToday: toDateStr(date) === toDateStr(today),
  }
}

// ─── Week view calculations ──────────────────────────────
interface WeekDay {
  date: Date
  dateStr: string
  day: number
  dayLabel: string
  isToday: boolean
}

const weekDays = computed<WeekDay[]>(() => {
  const start = getWeekStart(currentDate.value)
  const days: WeekDay[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    days.push({
      date: d,
      dateStr: toDateStr(d),
      day: d.getDate(),
      dayLabel: dayLabels[d.getDay()],
      isToday: toDateStr(d) === toDateStr(today),
    })
  }
  return days
})

function getWeekStart(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  d.setDate(d.getDate() - day)
  d.setHours(0, 0, 0, 0)
  return d
}

// ─── Schedule lookups ────────────────────────────────────
function getSchedulesForDate(dateStr: string): Schedule[] {
  return filteredSchedules.value
    .filter((s) => toDateStr(new Date(s.scheduledAt)) === dateStr)
    .slice(0, 3)
}

function getSchedulesForHour(dateStr: string, hour: number): Schedule[] {
  return filteredSchedules.value.filter((s) => {
    const d = new Date(s.scheduledAt)
    return toDateStr(d) === dateStr && d.getHours() === hour
  })
}

// ─── Quick add ───────────────────────────────────────────
const showQuickAdd = ref(false)
const quickAddForm = ref({
  videoId: null as number | null,
  scheduledAt: '',
  platforms: [] as Platform[],
})

// ─── Recommended time ─────────────────────────────────────
const recommendedSlot = ref<OptimalTimeSlot | null>(null)

async function fetchRecommendedTime() {
  try {
    const result = await analyticsApi.getOptimalTimes()
    recommendedSlot.value = result.slots.length > 0 ? result.slots[0] : null
  } catch {
    recommendedSlot.value = null
  }
}

function applyRecommendedTime() {
  if (!recommendedSlot.value) return
  const now = new Date()
  const currentDay = now.getDay()
  let daysUntil = recommendedSlot.value.dayOfWeek - currentDay
  if (daysUntil < 0) daysUntil += 7
  if (daysUntil === 0 && recommendedSlot.value.hour <= now.getHours()) daysUntil = 7

  const target = new Date(now)
  target.setDate(target.getDate() + daysUntil)
  target.setHours(recommendedSlot.value.hour, 0, 0, 0)
  quickAddForm.value.scheduledAt = toDateTimeLocal(target)
}

const isQuickAddValid = computed(() => {
  return (
    quickAddForm.value.videoId != null &&
    quickAddForm.value.videoId > 0 &&
    quickAddForm.value.scheduledAt !== '' &&
    quickAddForm.value.platforms.length > 0
  )
})

function onCellClick(cell: MonthCell) {
  if (cell.isCurrentMonth) {
    openQuickAdd(cell.date)
  }
}

function openQuickAdd(date: Date, hour?: number) {
  const d = new Date(date)
  if (hour !== undefined) {
    d.setHours(hour, 0, 0, 0)
  } else {
    d.setHours(9, 0, 0, 0)
  }
  quickAddForm.value = {
    videoId: null,
    scheduledAt: toDateTimeLocal(d),
    platforms: [],
  }
  showQuickAdd.value = true
}

async function submitQuickAdd() {
  if (!isQuickAddValid.value || quickAddForm.value.videoId == null) return

  try {
    await createSchedule({
      videoId: quickAddForm.value.videoId,
      scheduledAt: new Date(quickAddForm.value.scheduledAt).toISOString(),
      platforms: quickAddForm.value.platforms.map((p) => ({ platform: p })),
    })
    showQuickAdd.value = false
    await loadSchedules()
  } catch {
    // Error handling is managed by the store / global error handler
  }
}

// ─── Schedule detail ─────────────────────────────────────
const selectedSchedule = ref<Schedule | null>(null)

function openScheduleDetail(schedule: Schedule) {
  selectedSchedule.value = schedule
}

// ─── Cancel schedule ─────────────────────────────────────
const showCancelModal = ref(false)
const cancelTargetId = ref<number | null>(null)

function confirmCancel(schedule: Schedule) {
  cancelTargetId.value = schedule.id
  selectedSchedule.value = null
  showCancelModal.value = true
}

async function handleCancel() {
  if (cancelTargetId.value == null) return
  try {
    await cancelSchedule(cancelTargetId.value)
    cancelTargetId.value = null
    await loadSchedules()
  } catch {
    // Error handling is managed by the store / global error handler
  }
}

// ─── Helpers ─────────────────────────────────────────────
function toDateStr(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function toDateTimeLocal(date: Date): string {
  const y = date.getFullYear()
  const mo = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${d}T${h}:${mi}`
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

function formatTime(dateStr: string): string {
  const d = new Date(dateStr)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function formatHour(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

function getPlatformColor(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.color ?? '#6B7280'
}

function getScheduleBgColor(schedule: Schedule): string {
  if (schedule.platforms.length === 0) return '#F3F4F6'
  const primary = schedule.platforms[0].platform
  return getPlatformColor(primary) + '15'
}

function getScheduleBorderColor(schedule: Schedule): string {
  if (schedule.platforms.length === 0) return '#D1D5DB'
  return getPlatformColor(schedule.platforms[0].platform)
}

function getStatusLabel(status: ScheduleStatus): string {
  const map: Record<ScheduleStatus, string> = {
    SCHEDULED: '예약됨',
    PUBLISHED: '게시완료',
    FAILED: '실패',
    CANCELLED: '취소됨',
  }
  return map[status]
}

function getStatusBadgeClass(status: ScheduleStatus): string {
  const base = 'badge'
  const map: Record<ScheduleStatus, string> = {
    SCHEDULED: 'badge-blue',
    PUBLISHED: 'badge-success',
    FAILED: 'badge-danger',
    CANCELLED: 'badge-gray',
  }
  return `${base} ${map[status]}`
}

// ─── Data loading ────────────────────────────────────────
function getDateRange(): { startDate: string; endDate: string } {
  const d = currentDate.value

  if (calendarView.value === 'month') {
    const firstDay = new Date(d.getFullYear(), d.getMonth(), 1)
    // Include leading days from prev month (up to 6 days before)
    const start = new Date(firstDay)
    start.setDate(start.getDate() - firstDay.getDay())

    const lastDay = new Date(d.getFullYear(), d.getMonth() + 1, 0)
    // Include trailing days to fill 6 rows
    const end = new Date(lastDay)
    end.setDate(end.getDate() + (6 - lastDay.getDay()))

    return { startDate: toDateStr(start), endDate: toDateStr(end) }
  }

  if (calendarView.value === 'week') {
    const weekStart = getWeekStart(d)
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekEnd.getDate() + 6)
    return { startDate: toDateStr(weekStart), endDate: toDateStr(weekEnd) }
  }

  // List view: current month
  const start = new Date(d.getFullYear(), d.getMonth(), 1)
  const end = new Date(d.getFullYear(), d.getMonth() + 1, 0)
  return { startDate: toDateStr(start), endDate: toDateStr(end) }
}

async function loadSchedules() {
  const { startDate, endDate } = getDateRange()
  await fetchSchedules(startDate, endDate)
}

// Reload when navigation or view changes
watch([currentDate, calendarView], () => {
  loadSchedules()
})

onMounted(() => {
  loadSchedules()
  fetchRecommendedTime()
})
</script>
