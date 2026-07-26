<template>
  <div class="card overflow-x-auto p-0">
    <!-- 필터 결과 없음 -->
    <div v-if="sortedSchedules.length === 0" class="px-6 py-12 text-center">
      <CalendarIcon class="mx-auto mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('scheduleView.noFiltered') }}</p>
    </div>

    <template v-else>
      <!-- 모바일: 카드 -->
      <div class="space-y-3 px-4 py-4 tablet:hidden">
        <div v-for="schedule in sortedSchedules" :key="schedule.id" class="card p-4">
          <div class="flex items-start justify-between">
            <div class="min-w-0 flex-1">
              <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ schedule.videoTitle }}
              </p>
              <div class="mt-1 flex items-center gap-2">
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ formatScheduleDate(schedule.scheduledAt) }}
                  {{ formatScheduleTime(schedule.scheduledAt) }}
                </span>
                <span :class="getStatusBadgeClass(schedule.status)">
                  {{ getStatusLabel(schedule.status) }}
                </span>
              </div>
              <div class="mt-2 flex flex-wrap gap-1">
                <PlatformBadge
                  v-for="sp in schedule.platforms"
                  :key="sp.platform"
                  :platform="sp.platform"
                />
              </div>
            </div>
            <div v-if="schedule.status === 'SCHEDULED'" class="flex items-center gap-1 pl-2">
              <button
                :aria-label="$t('scheduleView.edit')"
                class="rounded p-1.5 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700"
                @click="$emit('select', schedule)"
              >
                <PencilSquareIcon class="h-4 w-4" />
              </button>
              <button
                :aria-label="$t('action.cancel')"
                class="rounded p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20"
                @click="$emit('cancel', schedule)"
              >
                <XMarkIcon class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 태블릿+: 테이블 -->
      <table class="hidden w-full tablet:table">
        <thead>
          <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
            <th v-if="sortMode === 'manual'" class="w-8" />
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.table.datetime') }}
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.table.video') }}
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.table.platform') }}
            </th>
            <th class="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.table.status') }}
            </th>
            <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.table.actions') }}
            </th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <DraggableScheduleItem
            v-for="(schedule, index) in sortedSchedules"
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
              <!-- 일시 -->
              <td class="whitespace-nowrap px-4 py-3">
                <div class="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ formatScheduleDate(schedule.scheduledAt) }}
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400">
                  {{ formatScheduleTime(schedule.scheduledAt) }}
                </div>
              </td>

              <!-- 영상 -->
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

              <!-- 플랫폼 -->
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  <PlatformBadge
                    v-for="sp in schedule.platforms"
                    :key="sp.platform"
                    :platform="sp.platform"
                  />
                </div>
              </td>

              <!-- 상태 -->
              <td class="px-4 py-3">
                <span :class="getStatusBadgeClass(schedule.status)">
                  {{ getStatusLabel(schedule.status) }}
                </span>
              </td>

              <!-- 작업 -->
              <td class="whitespace-nowrap px-4 py-3 text-right">
                <div class="flex items-center justify-end gap-1">
                  <button
                    v-if="schedule.status === 'SCHEDULED'"
                    class="rounded p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-gray-300"
                    :title="$t('scheduleView.edit')"
                    @click="$emit('select', schedule)"
                  >
                    <PencilSquareIcon class="h-4 w-4" />
                  </button>
                  <button
                    v-if="schedule.status === 'SCHEDULED'"
                    class="rounded p-1.5 text-gray-400 hover:bg-red-50 hover:text-red-500 dark:text-gray-500 dark:hover:bg-red-900/20"
                    :title="$t('action.cancel')"
                    @click="$emit('cancel', schedule)"
                  >
                    <XMarkIcon class="h-4 w-4" />
                  </button>
                </div>
              </td>
            </tr>
          </DraggableScheduleItem>
        </tbody>
      </table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarIcon, PencilSquareIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import DraggableScheduleItem from '@/components/schedule/DraggableScheduleItem.vue'
import { useScheduleLabels } from '@/composables/useScheduleLabels'
import { useDragAndDrop } from '@/composables/useDragAndDrop'
import { useNotification } from '@/composables/useNotification'
import { formatScheduleDate, formatScheduleTime, getStatusBadgeClass } from '@/utils/schedule'
import type { Schedule, ScheduleSortMode, ScheduleStatus } from '@/types/schedule'

const props = defineProps<{
  schedules: Schedule[]
  sortMode: ScheduleSortMode
}>()

defineEmits<{
  select: [schedule: Schedule]
  cancel: [schedule: Schedule]
}>()

const { t } = useI18n({ useScope: 'global' })
const { getStatusLabel } = useScheduleLabels()
const { success } = useNotification()

const STATUS_ORDER: Record<ScheduleStatus, number> = {
  SCHEDULED: 1,
  PUBLISHED: 2,
  FAILED: 3,
  CANCELLED: 4,
}

/** 수동 정렬 순서 (id 배열) */
const manualOrder = ref<number[]>([])

const sortedSchedules = computed<Schedule[]>(() => {
  const items = [...props.schedules]

  if (props.sortMode === 'manual' && manualOrder.value.length > 0) {
    const rank = new Map(manualOrder.value.map((id, index) => [id, index]))
    return items.sort(
      (a, b) => (rank.get(a.id) ?? Number.MAX_SAFE_INTEGER) - (rank.get(b.id) ?? Number.MAX_SAFE_INTEGER),
    )
  }

  if (props.sortMode === 'platform') {
    return items.sort((a, b) =>
      (a.platforms[0]?.platform ?? '').localeCompare(b.platforms[0]?.platform ?? ''),
    )
  }

  if (props.sortMode === 'status') {
    return items.sort((a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status])
  }

  // 기본: 날짜순
  return items.sort(
    (a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime(),
  )
})

const { dragHandlers, isDragging, dragIndex, dropIndex } = useDragAndDrop(
  sortedSchedules,
  (reordered: Schedule[]) => {
    manualOrder.value = reordered.map((s) => s.id)
    success(t('scheduleView.reorderSuccess'))
  },
)

// 수동 정렬 진입 시 현재 순서로 초기화
watch(
  () => props.sortMode,
  (mode) => {
    if (mode === 'manual' && manualOrder.value.length === 0) {
      manualOrder.value = sortedSchedules.value.map((s) => s.id)
    }
  },
)
</script>
