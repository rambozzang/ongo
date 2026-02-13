<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRecurringScheduleStore } from '@/stores/recurringSchedule'
import type { ContentQueueSlot } from '@/types/recurringSchedule'
import {
  CalendarDaysIcon,
  ExclamationTriangleIcon,
  FilmIcon,
  ArrowPathIcon,
  XCircleIcon,
} from '@heroicons/vue/24/outline'
import VideoAssignModal from './VideoAssignModal.vue'

const store = useRecurringScheduleStore()

onMounted(() => {
  store.initialize()
})

// ── Filters ──────────────────────────────────────────────────────────
const filterRuleId = ref<string>('')
const showEmptyOnly = ref(false)

const filteredSlots = computed<ContentQueueSlot[]>(() => {
  let slots = store.upcomingSlots

  // Limit to next 2 weeks
  const twoWeeksLater = new Date()
  twoWeeksLater.setDate(twoWeeksLater.getDate() + 14)
  slots = slots.filter((s) => new Date(s.scheduledDate) <= twoWeeksLater)

  if (filterRuleId.value) {
    slots = slots.filter((s) => s.ruleId === filterRuleId.value)
  }

  if (showEmptyOnly.value) {
    slots = slots.filter((s) => s.status === 'empty')
  }

  return slots
})

// ── Video assignment modal ───────────────────────────────────────────
const showAssignModal = ref(false)
const assigningSlotId = ref<string | null>(null)

function openAssignModal(slotId: string): void {
  assigningSlotId.value = slotId
  showAssignModal.value = true
}

function closeAssignModal(): void {
  showAssignModal.value = false
  assigningSlotId.value = null
}

function handleVideoAssigned(video: { id: number; title: string; thumbnail?: string }): void {
  if (assigningSlotId.value) {
    store.assignVideo(assigningSlotId.value, video.id, video.title, video.thumbnail)
  }
  closeAssignModal()
}

function handleUnassign(slotId: string): void {
  store.unassignVideo(slotId)
}

// ── Helpers ──────────────────────────────────────────────────────────
const DAY_LABELS_KO: Record<number, string> = {
  0: '일',
  1: '월',
  2: '화',
  3: '수',
  4: '목',
  5: '금',
  6: '토',
}

function formatSlotDate(isoString: string): string {
  const d = new Date(isoString)
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const dayName = DAY_LABELS_KO[d.getDay()]
  const hours = d.getHours().toString().padStart(2, '0')
  const minutes = d.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} (${dayName}) ${hours}:${minutes}`
}

function isWithin24Hours(isoString: string): boolean {
  const slotDate = new Date(isoString)
  const now = new Date()
  const diffMs = slotDate.getTime() - now.getTime()
  return diffMs > 0 && diffMs < 24 * 60 * 60 * 1000
}

function getRuleName(ruleId: string): string {
  const rule = store.rules.find((r) => r.id === ruleId)
  return rule?.name ?? '알 수 없는 규칙'
}

function getDateGroupLabel(isoString: string): string {
  const d = new Date(isoString)
  const today = new Date()
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)

  const isSameDay = (a: Date, b: Date): boolean =>
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()

  if (isSameDay(d, today)) return '오늘'
  if (isSameDay(d, tomorrow)) return '내일'

  const month = d.getMonth() + 1
  const day = d.getDate()
  const dayName = DAY_LABELS_KO[d.getDay()]
  return `${month}월 ${day}일 (${dayName})`
}

// Group slots by date for timeline view
const groupedSlots = computed(() => {
  const groups: { label: string; date: string; slots: ContentQueueSlot[] }[] = []
  const groupMap = new Map<string, ContentQueueSlot[]>()

  for (const slot of filteredSlots.value) {
    const d = new Date(slot.scheduledDate)
    const dateKey = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
    if (!groupMap.has(dateKey)) {
      groupMap.set(dateKey, [])
    }
    groupMap.get(dateKey)!.push(slot)
  }

  for (const [dateKey, slots] of groupMap) {
    const label = getDateGroupLabel(slots[0].scheduledDate)
    groups.push({ label, date: dateKey, slots })
  }

  return groups
})

const statusConfig: Record<string, { color: string; label: string }> = {
  empty: { color: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400', label: '미배정' },
  assigned: { color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300', label: '배정됨' },
  published: { color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300', label: '게시됨' },
  failed: { color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300', label: '실패' },
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          콘텐츠 대기열
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          향후 2주간 예약된 슬롯을 관리합니다
        </p>
      </div>
    </div>

    <!-- Filters -->
    <div class="flex flex-wrap items-center gap-3">
      <div class="flex items-center gap-2">
        <label
          for="filter-rule"
          class="text-sm text-gray-600 dark:text-gray-400"
        >
          규칙 필터:
        </label>
        <select
          id="filter-rule"
          v-model="filterRuleId"
          class="rounded-lg border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
        >
          <option value="">전체</option>
          <option
            v-for="rule in store.rules"
            :key="rule.id"
            :value="rule.id"
          >
            {{ rule.name }}
          </option>
        </select>
      </div>

      <label class="flex cursor-pointer items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <button
          type="button"
          role="switch"
          :aria-checked="showEmptyOnly"
          :class="[
            'relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors',
            showEmptyOnly ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600',
          ]"
          @click="showEmptyOnly = !showEmptyOnly"
        >
          <span
            :class="[
              'pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition-transform',
              showEmptyOnly ? 'translate-x-4' : 'translate-x-0',
            ]"
          />
        </button>
        빈 슬롯만 보기
      </label>
    </div>

    <!-- Empty state -->
    <div
      v-if="filteredSlots.length === 0"
      class="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-300 bg-white py-12 dark:border-gray-600 dark:bg-gray-800"
    >
      <CalendarDaysIcon class="h-10 w-10 text-gray-400 dark:text-gray-500" />
      <p class="mt-3 text-sm font-medium text-gray-600 dark:text-gray-300">
        {{ showEmptyOnly ? '빈 슬롯이 없습니다' : '예약된 슬롯이 없습니다' }}
      </p>
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        반복 규칙을 추가하면 슬롯이 자동 생성됩니다
      </p>
    </div>

    <!-- Timeline grouped by date -->
    <div v-else class="space-y-6">
      <div v-for="group in groupedSlots" :key="group.date">
        <!-- Date group header -->
        <h3 class="mb-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
          {{ group.label }}
        </h3>

        <div class="space-y-2">
          <div
            v-for="slot in group.slots"
            :key="slot.id"
            :class="[
              'rounded-xl border p-4 transition-all',
              slot.status === 'empty'
                ? 'border-dashed border-gray-300 bg-white dark:border-gray-600 dark:bg-gray-800'
                : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800',
            ]"
          >
            <div class="flex items-center justify-between gap-3">
              <!-- Left: Slot info -->
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <!-- Time -->
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ formatSlotDate(slot.scheduledDate) }}
                  </span>

                  <!-- Rule name badge -->
                  <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400">
                    {{ getRuleName(slot.ruleId) }}
                  </span>

                  <!-- Status badge -->
                  <span
                    :class="[
                      'rounded-full px-2 py-0.5 text-xs font-medium',
                      statusConfig[slot.status].color,
                    ]"
                  >
                    {{ statusConfig[slot.status].label }}
                  </span>

                  <!-- 24hr warning -->
                  <span
                    v-if="slot.status === 'empty' && isWithin24Hours(slot.scheduledDate)"
                    class="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400"
                  >
                    <ExclamationTriangleIcon class="h-3.5 w-3.5" />
                    24시간 이내
                  </span>
                </div>

                <!-- Assigned video info -->
                <div
                  v-if="slot.status === 'assigned' && slot.videoTitle"
                  class="mt-2 flex items-center gap-3"
                >
                  <div
                    v-if="slot.videoThumbnail"
                    class="h-10 w-16 shrink-0 overflow-hidden rounded bg-gray-200 dark:bg-gray-700"
                  >
                    <img
                      :src="slot.videoThumbnail"
                      :alt="slot.videoTitle"
                      class="h-full w-full object-cover"
                    />
                  </div>
                  <div v-else class="flex h-10 w-16 shrink-0 items-center justify-center rounded bg-gray-200 dark:bg-gray-700">
                    <FilmIcon class="h-5 w-5 text-gray-400" />
                  </div>
                  <span class="truncate text-sm text-gray-700 dark:text-gray-300">
                    {{ slot.videoTitle }}
                  </span>
                </div>
              </div>

              <!-- Right: Actions -->
              <div class="flex shrink-0 items-center gap-2">
                <template v-if="slot.status === 'empty'">
                  <button
                    class="inline-flex items-center gap-1.5 rounded-lg border border-dashed border-primary-400 px-3 py-1.5 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:border-primary-500 dark:text-primary-400 dark:hover:bg-primary-900/20"
                    @click="openAssignModal(slot.id)"
                  >
                    <FilmIcon class="h-3.5 w-3.5" />
                    영상 배정
                  </button>
                </template>

                <template v-if="slot.status === 'assigned'">
                  <button
                    class="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-2.5 py-1.5 text-xs font-medium text-gray-600 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-400 dark:hover:bg-gray-700"
                    @click="openAssignModal(slot.id)"
                  >
                    <ArrowPathIcon class="h-3.5 w-3.5" />
                    변경
                  </button>
                  <button
                    class="inline-flex items-center gap-1 rounded-lg border border-gray-300 px-2.5 py-1.5 text-xs font-medium text-red-500 transition-colors hover:bg-red-50 dark:border-gray-600 dark:hover:bg-red-900/20"
                    @click="handleUnassign(slot.id)"
                  >
                    <XCircleIcon class="h-3.5 w-3.5" />
                    제거
                  </button>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Video assign modal -->
    <VideoAssignModal
      v-if="showAssignModal"
      @close="closeAssignModal"
      @select="handleVideoAssigned"
    />
  </div>
</template>
