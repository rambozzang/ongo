<template>
  <div class="space-y-4">
    <!-- 반복 타입 선택 -->
    <div>
      <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">{{ t('schedule.recurrence.title') }}</label>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="option in recurrenceOptions"
          :key="option.value"
          type="button"
          class="rounded-lg border px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            modelValue.type === option.value
              ? 'border-primary-300 dark:border-primary-700 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400'
              : 'border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600'
          "
          @click="updateType(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <!-- 반복 상세 설정 (NONE이 아닐 때만) -->
    <template v-if="modelValue.type !== 'NONE'">
      <!-- 간격 설정 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ t('schedule.recurrence.interval') }}
        </label>
        <div class="flex items-center gap-2">
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('schedule.recurrence.every') }}</span>
          <input
            :value="modelValue.interval"
            type="number"
            min="1"
            max="99"
            class="input w-20 text-center"
            @input="updateInterval(($event.target as HTMLInputElement).value)"
          />
          <span class="text-sm text-gray-500 dark:text-gray-400">{{ intervalUnit }}</span>
        </div>
      </div>

      <!-- 주간 반복 시 요일 선택 -->
      <div v-if="modelValue.type === 'WEEKLY'">
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ t('schedule.recurrence.dayOfWeek') }}
        </label>
        <div class="flex gap-1.5">
          <button
            v-for="day in dayOptions"
            :key="day.value"
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full text-sm font-medium transition-colors"
            :class="
              isDaySelected(day.value)
                ? 'bg-primary-600 text-white'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-700'
            "
            @click="toggleDay(day.value)"
          >
            {{ day.label }}
          </button>
        </div>
      </div>

      <!-- 종료 조건 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ t('schedule.recurrence.endCondition') }}
        </label>
        <div class="space-y-2">
          <label
            v-for="endOption in endOptions"
            :key="endOption.value"
            class="flex cursor-pointer items-center gap-2.5 rounded-lg border px-3 py-2.5 transition-colors"
            :class="
              endCondition === endOption.value
                ? 'border-primary-300 dark:border-primary-700 bg-primary-50/50 dark:bg-primary-900/10'
                : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
            "
          >
            <input
              v-model="endCondition"
              type="radio"
              :value="endOption.value"
              class="h-4 w-4 text-primary-600 focus:ring-primary-500"
              @change="handleEndConditionChange"
            />
            <span class="text-sm text-gray-700 dark:text-gray-300">{{ endOption.label }}</span>

            <!-- 날짜 입력 -->
            <input
              v-if="endOption.value === 'date' && endCondition === 'date'"
              :value="modelValue.endDate ?? ''"
              type="date"
              class="input ml-auto w-40 text-sm"
              @input="updateEndDate(($event.target as HTMLInputElement).value)"
            />

            <!-- 횟수 입력 -->
            <div v-if="endOption.value === 'count' && endCondition === 'count'" class="ml-auto flex items-center gap-1.5">
              <input
                :value="modelValue.maxOccurrences ?? 10"
                type="number"
                min="2"
                max="365"
                class="input w-20 text-center text-sm"
                @input="updateMaxOccurrences(($event.target as HTMLInputElement).value)"
              />
              <span class="text-sm text-gray-500 dark:text-gray-400">{{ t('schedule.recurrence.times') }}</span>
            </div>
          </label>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RecurrenceConfig, RecurrenceType } from '@/types/schedule'

const { t } = useI18n()

const props = defineProps<{
  modelValue: RecurrenceConfig
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RecurrenceConfig]
}>()

const recurrenceOptions = computed<{ value: RecurrenceType; label: string }[]>(() => [
  { value: 'NONE', label: t('schedule.recurrence.none') },
  { value: 'DAILY', label: t('schedule.recurrence.daily') },
  { value: 'WEEKLY', label: t('schedule.recurrence.weekly') },
  { value: 'MONTHLY', label: t('schedule.recurrence.monthly') },
])

const dayOptions = computed(() => [
  { value: 0, label: t('schedule.recurrence.days.sun') },
  { value: 1, label: t('schedule.recurrence.days.mon') },
  { value: 2, label: t('schedule.recurrence.days.tue') },
  { value: 3, label: t('schedule.recurrence.days.wed') },
  { value: 4, label: t('schedule.recurrence.days.thu') },
  { value: 5, label: t('schedule.recurrence.days.fri') },
  { value: 6, label: t('schedule.recurrence.days.sat') },
])

const endOptions = computed(() => [
  { value: 'never' as const, label: t('schedule.recurrence.unlimited') },
  { value: 'date' as const, label: t('schedule.recurrence.byDate') },
  { value: 'count' as const, label: t('schedule.recurrence.byCount') },
])

const endCondition = ref<'never' | 'date' | 'count'>('never')

// 초기 종료 조건 설정
watch(
  () => props.modelValue,
  (val) => {
    if (val.endDate) {
      endCondition.value = 'date'
    } else if (val.maxOccurrences) {
      endCondition.value = 'count'
    } else {
      endCondition.value = 'never'
    }
  },
  { immediate: true },
)

const intervalUnit = computed(() => {
  const map: Record<RecurrenceType, string> = {
    NONE: '',
    DAILY: t('schedule.recurrence.perDays'),
    WEEKLY: t('schedule.recurrence.perWeeks'),
    MONTHLY: t('schedule.recurrence.perMonths'),
  }
  return map[props.modelValue.type]
})

function updateType(type: RecurrenceType) {
  emit('update:modelValue', {
    ...props.modelValue,
    type,
    interval: type === 'NONE' ? 1 : props.modelValue.interval,
    daysOfWeek: type === 'WEEKLY' ? (props.modelValue.daysOfWeek ?? []) : undefined,
  })
}

function updateInterval(value: string) {
  const num = Math.max(1, Math.min(99, parseInt(value) || 1))
  emit('update:modelValue', { ...props.modelValue, interval: num })
}

function isDaySelected(day: number): boolean {
  return props.modelValue.daysOfWeek?.includes(day) ?? false
}

function toggleDay(day: number) {
  const current = props.modelValue.daysOfWeek ?? []
  const updated = current.includes(day)
    ? current.filter((d) => d !== day)
    : [...current, day].sort()
  emit('update:modelValue', { ...props.modelValue, daysOfWeek: updated })
}

function handleEndConditionChange() {
  if (endCondition.value === 'never') {
    emit('update:modelValue', {
      ...props.modelValue,
      endDate: undefined,
      maxOccurrences: undefined,
    })
  } else if (endCondition.value === 'date') {
    emit('update:modelValue', {
      ...props.modelValue,
      endDate: props.modelValue.endDate ?? '',
      maxOccurrences: undefined,
    })
  } else if (endCondition.value === 'count') {
    emit('update:modelValue', {
      ...props.modelValue,
      endDate: undefined,
      maxOccurrences: props.modelValue.maxOccurrences ?? 10,
    })
  }
}

function updateEndDate(value: string) {
  emit('update:modelValue', { ...props.modelValue, endDate: value })
}

function updateMaxOccurrences(value: string) {
  const num = Math.max(2, Math.min(365, parseInt(value) || 10))
  emit('update:modelValue', { ...props.modelValue, maxOccurrences: num })
}
</script>
