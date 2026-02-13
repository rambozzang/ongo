<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRecurringScheduleStore } from '@/stores/recurringSchedule'
import type {
  RecurringRule,
  RecurrencePattern,
  DayOfWeek,
} from '@/types/recurringSchedule'
import { XMarkIcon } from '@heroicons/vue/24/outline'

const props = defineProps<{
  rule: RecurringRule | null
}>()

const emit = defineEmits<{
  close: []
}>()

const store = useRecurringScheduleStore()

const isEditing = computed(() => props.rule !== null)

// ── Korean labels ────────────────────────────────────────────────────
const DAY_LABELS_KO: { value: DayOfWeek; label: string }[] = [
  { value: 1, label: '월' },
  { value: 2, label: '화' },
  { value: 3, label: '수' },
  { value: 4, label: '목' },
  { value: 5, label: '금' },
  { value: 6, label: '토' },
  { value: 0, label: '일' },
]

const PATTERN_OPTIONS: { value: RecurrencePattern; label: string }[] = [
  { value: 'daily', label: '매일' },
  { value: 'weekly', label: '매주' },
  { value: 'biweekly', label: '격주' },
  { value: 'monthly', label: '매월' },
]

const PLATFORM_OPTIONS: { value: string; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const WEEK_ORDINALS: { value: number; label: string }[] = [
  { value: 1, label: '첫째' },
  { value: 2, label: '둘째' },
  { value: 3, label: '셋째' },
  { value: 4, label: '넷째' },
]

// ── Form state ───────────────────────────────────────────────────────
type MonthlyMode = 'dayOfMonth' | 'nthWeekday'

const form = reactive({
  name: props.rule?.name ?? '',
  pattern: (props.rule?.pattern ?? 'weekly') as RecurrencePattern,
  daysOfWeek: [...(props.rule?.daysOfWeek ?? [])] as DayOfWeek[],
  dayOfMonth: props.rule?.dayOfMonth ?? 1,
  nthWeekday: {
    week: props.rule?.nthWeekday?.week ?? 1,
    day: (props.rule?.nthWeekday?.day ?? 2) as DayOfWeek,
  },
  monthlyMode: (props.rule?.nthWeekday ? 'nthWeekday' : 'dayOfMonth') as MonthlyMode,
  timeSlot: props.rule?.timeSlot ?? '18:00',
  platforms: [...(props.rule?.platforms ?? [])] as string[],
  isActive: props.rule?.isActive ?? true,
})

// ── Computed preview ─────────────────────────────────────────────────
const previewDates = computed<string[]>(() => {
  const previewRule: RecurringRule = {
    id: 'preview',
    name: form.name,
    pattern: form.pattern,
    daysOfWeek: form.daysOfWeek,
    dayOfMonth: form.monthlyMode === 'dayOfMonth' ? form.dayOfMonth : undefined,
    nthWeekday: form.monthlyMode === 'nthWeekday' ? { ...form.nthWeekday } : undefined,
    timeSlot: form.timeSlot,
    platforms: form.platforms,
    isActive: true,
    createdAt: '',
    updatedAt: '',
  }

  const occurrences = store.computeOccurrences(previewRule, 5)
  const dayNames: Record<number, string> = {
    0: '일',
    1: '월',
    2: '화',
    3: '수',
    4: '목',
    5: '금',
    6: '토',
  }

  return occurrences.map((d) => {
    const y = d.getFullYear()
    const m = (d.getMonth() + 1).toString().padStart(2, '0')
    const day = d.getDate().toString().padStart(2, '0')
    const dayName = dayNames[d.getDay()]
    const hours = d.getHours().toString().padStart(2, '0')
    const mins = d.getMinutes().toString().padStart(2, '0')
    return `${y}-${m}-${day} (${dayName}) ${hours}:${mins}`
  })
})

// ── Validation ───────────────────────────────────────────────────────
const isValid = computed(() => {
  if (!form.name.trim()) return false
  if (form.platforms.length === 0) return false
  if (
    (form.pattern === 'weekly' || form.pattern === 'biweekly') &&
    form.daysOfWeek.length === 0
  ) {
    return false
  }
  return true
})

// ── Day toggle ───────────────────────────────────────────────────────
function toggleDay(day: DayOfWeek): void {
  const idx = form.daysOfWeek.indexOf(day)
  if (idx >= 0) {
    form.daysOfWeek.splice(idx, 1)
  } else {
    form.daysOfWeek.push(day)
  }
}

function isDaySelected(day: DayOfWeek): boolean {
  return form.daysOfWeek.includes(day)
}

// ── Platform toggle ──────────────────────────────────────────────────
function togglePlatform(platform: string): void {
  const idx = form.platforms.indexOf(platform)
  if (idx >= 0) {
    form.platforms.splice(idx, 1)
  } else {
    form.platforms.push(platform)
  }
}

function isPlatformSelected(platform: string): boolean {
  return form.platforms.includes(platform)
}

// ── Submit ───────────────────────────────────────────────────────────
function handleSubmit(): void {
  if (!isValid.value) return

  const ruleData = {
    name: form.name.trim(),
    pattern: form.pattern,
    daysOfWeek: form.pattern === 'weekly' || form.pattern === 'biweekly' ? form.daysOfWeek : [],
    dayOfMonth:
      form.pattern === 'monthly' && form.monthlyMode === 'dayOfMonth'
        ? form.dayOfMonth
        : undefined,
    nthWeekday:
      form.pattern === 'monthly' && form.monthlyMode === 'nthWeekday'
        ? { ...form.nthWeekday }
        : undefined,
    timeSlot: form.timeSlot,
    platforms: form.platforms,
    isActive: form.isActive,
  }

  if (isEditing.value && props.rule) {
    store.updateRule(props.rule.id, ruleData)
  } else {
    store.addRule(ruleData)
  }

  emit('close')
}

// Generate day-of-month options (1-31)
const dayOfMonthOptions = Array.from({ length: 31 }, (_, i) => i + 1)
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="recurring-rule-form-modal-title"
      @click.self="emit('close')"
    >
      <div
        class="mx-4 flex max-h-[90vh] w-full max-w-lg flex-col rounded-xl bg-white shadow-xl dark:bg-gray-800"
        @keydown.escape="emit('close')"
      >
        <!-- Header -->
        <div class="flex items-center justify-between border-b border-gray-200 px-6 py-4 dark:border-gray-700">
          <h2 id="recurring-rule-form-modal-title" class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ isEditing ? '반복 규칙 수정' : '새 반복 규칙 추가' }}
          </h2>
          <button
            class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
            aria-label="닫기"
            @click="emit('close')"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>
        </div>

        <!-- Body (scrollable) -->
        <div class="flex-1 space-y-5 overflow-y-auto px-6 py-5">
          <!-- 규칙 이름 -->
          <div>
            <label
              for="rule-name"
              class="block text-sm font-medium text-gray-700 dark:text-gray-300"
            >
              규칙 이름
            </label>
            <input
              id="rule-name"
              v-model="form.name"
              type="text"
              placeholder="예: 주간 브이로그 업로드"
              class="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 dark:placeholder-gray-500"
            />
          </div>

          <!-- 반복 패턴 -->
          <div>
            <label
              for="rule-pattern"
              class="block text-sm font-medium text-gray-700 dark:text-gray-300"
            >
              반복 패턴
            </label>
            <select
              id="rule-pattern"
              v-model="form.pattern"
              class="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
            >
              <option
                v-for="opt in PATTERN_OPTIONS"
                :key="opt.value"
                :value="opt.value"
              >
                {{ opt.label }}
              </option>
            </select>
          </div>

          <!-- 요일 선택 (weekly / biweekly) -->
          <div v-if="form.pattern === 'weekly' || form.pattern === 'biweekly'">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              요일 선택
            </label>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="dayOpt in DAY_LABELS_KO"
                :key="dayOpt.value"
                type="button"
                :class="[
                  'flex h-9 w-9 items-center justify-center rounded-full text-sm font-medium transition-colors',
                  isDaySelected(dayOpt.value)
                    ? 'bg-primary-500 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600',
                ]"
                @click="toggleDay(dayOpt.value)"
              >
                {{ dayOpt.label }}
              </button>
            </div>
            <p
              v-if="form.daysOfWeek.length === 0"
              class="mt-1 text-xs text-red-500"
            >
              최소 1개의 요일을 선택해 주세요
            </p>
          </div>

          <!-- 월간 설정 (monthly) -->
          <div v-if="form.pattern === 'monthly'" class="space-y-3">
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              월간 반복 기준
            </label>

            <!-- Monthly mode radio -->
            <div class="flex gap-4">
              <label class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                <input
                  v-model="form.monthlyMode"
                  type="radio"
                  value="dayOfMonth"
                  class="text-primary-500 focus:ring-primary-500"
                />
                특정 날짜
              </label>
              <label class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                <input
                  v-model="form.monthlyMode"
                  type="radio"
                  value="nthWeekday"
                  class="text-primary-500 focus:ring-primary-500"
                />
                N번째 요일
              </label>
            </div>

            <!-- Day of month selector -->
            <div v-if="form.monthlyMode === 'dayOfMonth'">
              <select
                v-model.number="form.dayOfMonth"
                class="block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              >
                <option v-for="d in dayOfMonthOptions" :key="d" :value="d">
                  매월 {{ d }}일
                </option>
              </select>
            </div>

            <!-- Nth weekday selector -->
            <div v-if="form.monthlyMode === 'nthWeekday'" class="flex gap-2">
              <select
                v-model.number="form.nthWeekday.week"
                class="block w-1/2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              >
                <option
                  v-for="w in WEEK_ORDINALS"
                  :key="w.value"
                  :value="w.value"
                >
                  {{ w.label }}
                </option>
              </select>
              <select
                v-model.number="form.nthWeekday.day"
                class="block w-1/2 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
              >
                <option
                  v-for="dayOpt in DAY_LABELS_KO"
                  :key="dayOpt.value"
                  :value="dayOpt.value"
                >
                  {{ dayOpt.label }}요일
                </option>
              </select>
            </div>
          </div>

          <!-- 시간 -->
          <div>
            <label
              for="rule-time"
              class="block text-sm font-medium text-gray-700 dark:text-gray-300"
            >
              시간
            </label>
            <input
              id="rule-time"
              v-model="form.timeSlot"
              type="time"
              class="mt-1 block w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-900 transition-colors focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100"
            />
          </div>

          <!-- 플랫폼 -->
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
              플랫폼
            </label>
            <div class="mt-2 flex flex-wrap gap-2">
              <button
                v-for="pOpt in PLATFORM_OPTIONS"
                :key="pOpt.value"
                type="button"
                :class="[
                  'rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors',
                  isPlatformSelected(pOpt.value)
                    ? 'border-primary-500 bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                    : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300 dark:hover:bg-gray-600',
                ]"
                @click="togglePlatform(pOpt.value)"
              >
                {{ pOpt.label }}
              </button>
            </div>
            <p
              v-if="form.platforms.length === 0"
              class="mt-1 text-xs text-red-500"
            >
              최소 1개의 플랫폼을 선택해 주세요
            </p>
          </div>

          <!-- Preview section -->
          <div
            v-if="previewDates.length > 0"
            class="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/50"
          >
            <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
              다음 5회 예약 일정
            </h4>
            <ul class="mt-2 space-y-1">
              <li
                v-for="(date, idx) in previewDates"
                :key="idx"
                class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400"
              >
                <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-medium text-primary-700 dark:bg-primary-900/40 dark:text-primary-300">
                  {{ idx + 1 }}
                </span>
                {{ date }}
              </li>
            </ul>
          </div>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-end gap-2 border-t border-gray-200 px-6 py-4 dark:border-gray-700">
          <button
            class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-700"
            @click="emit('close')"
          >
            취소
          </button>
          <button
            :disabled="!isValid"
            :class="[
              'rounded-lg px-4 py-2 text-sm font-medium text-white transition-colors',
              isValid
                ? 'bg-primary-500 hover:bg-primary-600'
                : 'cursor-not-allowed bg-gray-300 dark:bg-gray-600',
            ]"
            @click="handleSubmit"
          >
            {{ isEditing ? '저장' : '추가' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
