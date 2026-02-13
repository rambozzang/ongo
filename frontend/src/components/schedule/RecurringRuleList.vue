<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRecurringScheduleStore } from '@/stores/recurringSchedule'
import type { RecurringRule } from '@/types/recurringSchedule'
import {
  PlusIcon,
  PencilSquareIcon,
  TrashIcon,
  CalendarDaysIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import RecurringRuleFormModal from './RecurringRuleFormModal.vue'

const store = useRecurringScheduleStore()

onMounted(() => {
  store.initialize()
})

// ── Modal state ──────────────────────────────────────────────────────
const showFormModal = ref(false)
const editingRule = ref<RecurringRule | null>(null)

function openCreateModal(): void {
  editingRule.value = null
  showFormModal.value = true
}

function openEditModal(rule: RecurringRule): void {
  editingRule.value = rule
  showFormModal.value = true
}

function closeModal(): void {
  showFormModal.value = false
  editingRule.value = null
}

// ── Delete confirmation ──────────────────────────────────────────────
const deletingRuleId = ref<string | null>(null)

function confirmDelete(id: string): void {
  deletingRuleId.value = id
}

function executeDelete(): void {
  if (deletingRuleId.value) {
    store.deleteRule(deletingRuleId.value)
    deletingRuleId.value = null
  }
}

function cancelDelete(): void {
  deletingRuleId.value = null
}

// ── Korean day labels ────────────────────────────────────────────────
const DAY_LABELS_KO: Record<number, string> = {
  0: '일',
  1: '월',
  2: '화',
  3: '수',
  4: '목',
  5: '금',
  6: '토',
}

const PATTERN_LABELS_KO: Record<string, string> = {
  daily: '매일',
  weekly: '매주',
  biweekly: '격주',
  monthly: '매월',
}

function getPatternDescription(rule: RecurringRule): string {
  const patternLabel = PATTERN_LABELS_KO[rule.pattern] || rule.pattern

  if (rule.pattern === 'daily') {
    return `${patternLabel} ${rule.timeSlot}`
  }

  if (rule.pattern === 'weekly' || rule.pattern === 'biweekly') {
    const days = [...rule.daysOfWeek]
      .sort((a, b) => a - b)
      .map((d) => DAY_LABELS_KO[d])
      .join(', ')
    return `${patternLabel} ${days} ${rule.timeSlot}`
  }

  if (rule.pattern === 'monthly') {
    if (rule.nthWeekday) {
      const weekOrdinals = ['', '첫째', '둘째', '셋째', '넷째', '다섯째']
      const weekLabel = weekOrdinals[rule.nthWeekday.week] || `${rule.nthWeekday.week}번째`
      const dayLabel = DAY_LABELS_KO[rule.nthWeekday.day]
      return `${patternLabel} ${weekLabel} ${dayLabel}요일 ${rule.timeSlot}`
    }
    if (rule.dayOfMonth !== undefined) {
      return `${patternLabel} ${rule.dayOfMonth}일 ${rule.timeSlot}`
    }
  }

  return `${patternLabel} ${rule.timeSlot}`
}

function getNextScheduledDate(rule: RecurringRule): string | null {
  const occurrences = store.computeOccurrences(rule, 1)
  if (occurrences.length === 0) return null

  const next = occurrences[0]
  const month = next.getMonth() + 1
  const date = next.getDate()
  const dayName = DAY_LABELS_KO[next.getDay()]
  const hours = next.getHours().toString().padStart(2, '0')
  const minutes = next.getMinutes().toString().padStart(2, '0')
  return `${month}/${date} (${dayName}) ${hours}:${minutes}`
}

const sortedRules = computed(() =>
  [...store.rules].sort((a, b) => {
    // Active rules first, then by updatedAt descending
    if (a.isActive !== b.isActive) return a.isActive ? -1 : 1
    return b.updatedAt.localeCompare(a.updatedAt)
  }),
)
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          반복 예약 규칙
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          정기적인 콘텐츠 게시 일정을 관리합니다
        </p>
      </div>
      <button
        class="inline-flex items-center gap-2 rounded-lg bg-primary-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-600"
        @click="openCreateModal"
      >
        <PlusIcon class="h-4 w-4" />
        새 반복 규칙 추가
      </button>
    </div>

    <!-- Stats summary -->
    <div
      v-if="store.rules.length > 0"
      class="grid grid-cols-2 gap-3 sm:grid-cols-4"
    >
      <div class="rounded-lg bg-white p-3 shadow-sm dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">전체 규칙</p>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ store.stats.totalRules }}
        </p>
      </div>
      <div class="rounded-lg bg-white p-3 shadow-sm dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">활성 규칙</p>
        <p class="text-xl font-bold text-green-600 dark:text-green-400">
          {{ store.stats.activeRules }}
        </p>
      </div>
      <div class="rounded-lg bg-white p-3 shadow-sm dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">예정된 슬롯</p>
        <p class="text-xl font-bold text-blue-600 dark:text-blue-400">
          {{ store.stats.upcomingSlots }}
        </p>
      </div>
      <div class="rounded-lg bg-white p-3 shadow-sm dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">빈 슬롯</p>
        <p class="text-xl font-bold text-amber-600 dark:text-amber-400">
          {{ store.stats.emptySlots }}
        </p>
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-if="store.rules.length === 0"
      class="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-gray-300 bg-white py-16 dark:border-gray-600 dark:bg-gray-800"
    >
      <CalendarDaysIcon class="h-12 w-12 text-gray-400 dark:text-gray-500" />
      <p class="mt-4 text-base font-medium text-gray-600 dark:text-gray-300">
        반복 예약 규칙이 없습니다
      </p>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        정기적인 게시 일정을 설정해 보세요
      </p>
      <button
        class="mt-6 inline-flex items-center gap-2 rounded-lg bg-primary-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-600"
        @click="openCreateModal"
      >
        <PlusIcon class="h-4 w-4" />
        첫 번째 규칙 만들기
      </button>
    </div>

    <!-- Rules list -->
    <div v-else class="space-y-3">
      <div
        v-for="rule in sortedRules"
        :key="rule.id"
        :class="[
          'rounded-xl border bg-white p-4 shadow-sm transition-all dark:bg-gray-800',
          rule.isActive
            ? 'border-gray-200 dark:border-gray-700'
            : 'border-gray-200 opacity-60 dark:border-gray-700',
        ]"
      >
        <div class="flex items-start justify-between gap-4">
          <!-- Left: Info -->
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
                {{ rule.name }}
              </h3>
              <span
                v-if="!rule.isActive"
                class="shrink-0 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-500 dark:bg-gray-700 dark:text-gray-400"
              >
                비활성
              </span>
            </div>

            <!-- Pattern description -->
            <div class="mt-1 flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-300">
              <ClockIcon class="h-4 w-4 shrink-0 text-gray-400 dark:text-gray-500" />
              <span>{{ getPatternDescription(rule) }}</span>
            </div>

            <!-- Platform badges -->
            <div class="mt-2 flex flex-wrap gap-1.5">
              <PlatformBadge
                v-for="platform in rule.platforms"
                :key="platform"
                :platform="platform as any"
              />
            </div>

            <!-- Next scheduled -->
            <p
              v-if="rule.isActive && getNextScheduledDate(rule)"
              class="mt-2 text-xs text-gray-500 dark:text-gray-400"
            >
              다음 예약: {{ getNextScheduledDate(rule) }}
            </p>
          </div>

          <!-- Right: Actions -->
          <div class="flex shrink-0 items-center gap-2">
            <!-- Toggle switch -->
            <button
              :aria-label="rule.isActive ? '비활성화' : '활성화'"
              :class="[
                'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors',
                rule.isActive ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600',
              ]"
              role="switch"
              :aria-checked="rule.isActive"
              @click="store.toggleRule(rule.id)"
            >
              <span
                :class="[
                  'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition-transform',
                  rule.isActive ? 'translate-x-5' : 'translate-x-0',
                ]"
              />
            </button>

            <!-- Edit -->
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="수정"
              @click="openEditModal(rule)"
            >
              <PencilSquareIcon class="h-4 w-4" />
            </button>

            <!-- Delete -->
            <button
              class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
              aria-label="삭제"
              @click="confirmDelete(rule.id)"
            >
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete confirmation overlay -->
    <Teleport to="body">
      <div
        v-if="deletingRuleId"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
        role="dialog"
        aria-modal="true"
        aria-label="규칙 삭제 확인"
        @click.self="cancelDelete"
      >
        <div class="mx-4 w-full max-w-sm rounded-xl bg-white p-6 shadow-xl dark:bg-gray-800">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
            규칙 삭제
          </h3>
          <p class="mt-2 text-sm text-gray-600 dark:text-gray-300">
            이 반복 예약 규칙을 삭제하시겠습니까? 관련된 예약 슬롯도 함께 삭제됩니다.
          </p>
          <div class="mt-5 flex justify-end gap-2">
            <button
              class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
              @click="cancelDelete"
            >
              취소
            </button>
            <button
              class="rounded-lg bg-red-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-red-600"
              @click="executeDelete"
            >
              삭제
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Form modal -->
    <RecurringRuleFormModal
      v-if="showFormModal"
      :rule="editingRule"
      @close="closeModal"
    />
  </div>
</template>
