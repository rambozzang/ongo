<template>
  <BaseModal
    :model-value="modelValue"
    :title="$t('calendar.aiCalendarGenerate')"
    max-width="lg"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="space-y-4">
      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ $t('calendar.aiCalendarGenerateDesc') }}
      </p>

      <!-- 기간 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('calendar.selectDateRange') }}
        </label>
        <div class="flex gap-2">
          <input v-model="form.startDate" type="date" class="input-field flex-1" />
          <input v-model="form.endDate" type="date" class="input-field flex-1" />
        </div>
      </div>

      <!-- 플랫폼 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('calendar.selectPlatform') }}
        </label>
        <div class="flex flex-wrap gap-2">
          <label
            v-for="p in platformOptions"
            :key="p.value"
            class="flex cursor-pointer items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm transition-colors"
            :class="
              form.platforms.includes(p.value)
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                : 'border-gray-300 text-gray-700 dark:border-gray-600 dark:text-gray-300'
            "
          >
            <input v-model="form.platforms" type="checkbox" :value="p.value" class="hidden" />
            {{ p.label }}
          </label>
        </div>
      </div>

      <!-- 빈도 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('calendar.selectFrequency') }}
        </label>
        <select v-model="form.frequency" class="input-field">
          <option value="DAILY">{{ $t('calendar.frequencyDaily') }}</option>
          <option value="WEEKLY_2_3">{{ $t('calendar.frequencyWeekly') }}</option>
          <option value="WEEKLY_1_2">{{ $t('calendar.frequencyBiweekly') }}</option>
        </select>
      </div>

      <button
        class="btn-primary inline-flex w-full items-center justify-center gap-2"
        :disabled="loading || form.platforms.length === 0"
        @click="generate"
      >
        <SparklesIcon class="h-4 w-4" />
        {{ loading ? $t('calendar.generating') : $t('calendar.aiCalendarGenerate') }}
      </button>

      <!-- 결과 -->
      <div v-if="suggestions.length > 0" class="space-y-3">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('calendar.aiCalendarGenerateDesc') }}
        </h3>
        <div class="max-h-64 space-y-2 overflow-y-auto">
          <div
            v-for="suggestion in suggestions"
            :key="suggestion.id"
            class="card flex items-center justify-between rounded-lg p-3"
          >
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span
                  class="rounded bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                >
                  {{ suggestion.platform }}
                </span>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ suggestion.suggestedDate }}
                </span>
              </div>
              <p class="mt-1 truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ suggestion.title }}
              </p>
              <p v-if="suggestion.reason" class="text-xs text-gray-500 dark:text-gray-400">
                {{ suggestion.reason }}
              </p>
            </div>
            <button
              v-if="suggestion.status !== 'ACCEPTED'"
              class="btn-primary ml-3 shrink-0 px-3 py-1 text-xs"
              @click="accept(suggestion.id)"
            >
              {{ $t('calendar.accept') }}
            </button>
            <span
              v-else
              class="ml-3 shrink-0 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-400"
            >
              {{ $t('calendar.accepted') }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SparklesIcon } from '@heroicons/vue/24/outline'
import BaseModal from '@/components/common/BaseModal.vue'
import { useNotification } from '@/composables/useNotification'
import { contentCalendarAiApi, type ContentCalendarSuggestion } from '@/api/contentCalendarAi'
import { toDateStr } from '@/utils/schedule'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 제안 적용 완료 → 부모가 예약 목록을 다시 로드 */
  accepted: []
}>()

const { error } = useNotification()

const platformOptions = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const today = new Date()
const form = ref({
  startDate: toDateStr(today),
  endDate: toDateStr(new Date(today.getFullYear(), today.getMonth() + 1, today.getDate())),
  platforms: ['YOUTUBE'] as string[],
  frequency: 'WEEKLY_2_3',
})

const loading = ref(false)
const suggestions = ref<ContentCalendarSuggestion[]>([])

async function generate() {
  loading.value = true
  try {
    suggestions.value = await contentCalendarAiApi.generate({
      startDate: form.value.startDate,
      endDate: form.value.endDate,
      platforms: form.value.platforms,
      frequency: form.value.frequency,
    })
  } catch {
    error('AI 캘린더 생성에 실패했습니다')
  } finally {
    loading.value = false
  }
}

async function accept(id: number) {
  try {
    const updated = await contentCalendarAiApi.acceptSuggestion(id)
    const idx = suggestions.value.findIndex((s) => s.id === id)
    if (idx !== -1) {
      suggestions.value[idx] = updated
    }
    emit('accepted')
  } catch {
    error('적용에 실패했습니다')
  }
}
</script>
