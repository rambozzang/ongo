<template>
  <BaseModal v-model="isOpen" :title="$t('ugc.scheduleModalTitle')" max-width="lg">
    <p class="mb-5 text-sm text-gray-500 dark:text-gray-400">{{ $t('ugc.scheduleModalDescription') }}</p>

    <div class="space-y-3">
      <section class="rounded-xl border border-gray-200 bg-gray-50/70 p-4 dark:border-gray-700 dark:bg-gray-800/50">
        <div class="mb-3 flex items-start gap-3">
          <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
            <CalendarDaysIcon class="h-5 w-5" />
          </div>
          <div>
            <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.startAt') }}</h4>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ $t('ugc.startAtHint') }}</p>
          </div>
        </div>
        <div class="grid grid-cols-1 gap-3 mobile:grid-cols-2">
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ $t('ugc.date') }}</span>
            <input v-model="startDate" type="date" class="input-field" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ $t('ugc.time') }}</span>
            <select v-model="startTime" class="input-field">
              <option v-for="time in timeOptions" :key="time" :value="time">{{ time }}</option>
            </select>
          </label>
        </div>
      </section>

      <section class="rounded-xl border border-gray-200 bg-gray-50/70 p-4 dark:border-gray-700 dark:bg-gray-800/50">
        <div class="mb-3 flex items-start gap-3">
          <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300">
            <FlagIcon class="h-5 w-5" />
          </div>
          <div>
            <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.endAt') }}</h4>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ $t('ugc.endAtHint') }}</p>
          </div>
        </div>
        <div class="grid grid-cols-1 gap-3 mobile:grid-cols-2">
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ $t('ugc.date') }}</span>
            <input v-model="endDate" type="date" class="input-field" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs font-medium text-gray-600 dark:text-gray-300">{{ $t('ugc.time') }}</span>
            <select v-model="endTime" class="input-field">
              <option v-for="time in timeOptions" :key="time" :value="time">{{ time }}</option>
            </select>
          </label>
        </div>
      </section>
    </div>

    <p v-if="errorMessage" class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-300" role="alert">
      {{ errorMessage }}
    </p>

    <template #footer>
      <button type="button" class="btn-secondary mr-auto" @click="clearSchedule">{{ $t('ugc.noSchedule') }}</button>
      <button type="button" class="btn-secondary" @click="isOpen = false">{{ $t('ugc.cancel') }}</button>
      <button type="button" class="btn-primary" @click="save">{{ $t('ugc.applySchedule') }}</button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CalendarDaysIcon, FlagIcon } from '@heroicons/vue/24/outline'
import BaseModal from '@/components/common/BaseModal.vue'

const props = defineProps<{
  modelValue: boolean
  startAt: string
  endAt: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [value: { startAt: string; endAt: string }]
}>()

const { t } = useI18n({ useScope: 'global' })
const startDate = ref('')
const startTime = ref('09:00')
const endDate = ref('')
const endTime = ref('18:00')
const errorMessage = ref('')

const isOpen = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const timeOptions = Array.from({ length: 48 }, (_, index) => {
  const hour = Math.floor(index / 2).toString().padStart(2, '0')
  const minute = index % 2 === 0 ? '00' : '30'
  return `${hour}:${minute}`
})

function splitDateTime(value: string, fallbackTime: string): [string, string] {
  if (!value) return ['', fallbackTime]
  const [date, time = fallbackTime] = value.slice(0, 16).split('T')
  return [date, time]
}

function hydrate() {
  const [nextStartDate, nextStartTime] = splitDateTime(props.startAt, '09:00')
  const [nextEndDate, nextEndTime] = splitDateTime(props.endAt, '18:00')
  startDate.value = nextStartDate
  startTime.value = timeOptions.includes(nextStartTime) ? nextStartTime : '09:00'
  endDate.value = nextEndDate
  endTime.value = timeOptions.includes(nextEndTime) ? nextEndTime : '18:00'
  errorMessage.value = ''
}

function save() {
  errorMessage.value = ''
  const hasStart = Boolean(startDate.value)
  const hasEnd = Boolean(endDate.value)
  if (hasStart !== hasEnd) {
    errorMessage.value = t('ugc.scheduleBothRequired')
    return
  }

  const startAt = hasStart ? `${startDate.value}T${startTime.value}` : ''
  const endAt = hasEnd ? `${endDate.value}T${endTime.value}` : ''
  if (startAt && endAt && endAt <= startAt) {
    errorMessage.value = t('ugc.periodInvalid')
    return
  }

  emit('save', { startAt, endAt })
  isOpen.value = false
}

function clearSchedule() {
  emit('save', { startAt: '', endAt: '' })
  isOpen.value = false
}

watch(() => props.modelValue, (open) => {
  if (open) hydrate()
})
</script>
