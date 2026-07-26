<template>
  <BaseModal
    :model-value="modelValue"
    :title="$t('calendar.aiOptimalTime')"
    max-width="lg"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="space-y-4">
      <p class="text-sm text-gray-600 dark:text-gray-400">
        {{ $t('calendar.aiOptimalTimeDesc') }}
      </p>

      <!-- 플랫폼 선택 -->
      <div>
        <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('calendar.selectPlatform') }}
        </label>
        <select v-model="platform" class="input-field">
          <option v-for="p in platformOptions" :key="p.value" :value="p.value">{{ p.label }}</option>
        </select>
      </div>

      <button
        class="btn-primary inline-flex w-full items-center justify-center gap-2"
        :disabled="loading"
        @click="generate"
      >
        <SparklesIcon class="h-4 w-4" />
        {{ loading ? $t('calendar.generating') : $t('calendar.aiOptimalTime') }}
      </button>

      <!-- 결과 -->
      <div v-if="slots.length > 0" class="space-y-3">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ platform }} - {{ $t('calendar.aiOptimalTime') }}
        </h3>
        <div class="grid grid-cols-1 gap-3 mobile:grid-cols-2">
          <div v-for="slot in slots" :key="slot.id" class="card rounded-lg p-3">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ slot.dayOfWeek }} {{ slot.hour }}:00
              </span>
              <span
                class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-400"
              >
                {{ $t('calendar.score') }} {{ slot.score }}
              </span>
            </div>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              {{ $t('calendar.audience') }}: {{ slot.estimatedAudience.toLocaleString() }}
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">
              {{ $t('calendar.competition') }}: {{ slot.competition }}
            </p>
            <p class="mt-1 text-xs text-gray-600 dark:text-gray-300">{{ slot.reason }}</p>
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
import { scheduleOptimizerApi, type OptimalSlot } from '@/api/scheduleOptimizer'

defineProps<{
  modelValue: boolean
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { error } = useNotification()

const platformOptions = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const platform = ref('YOUTUBE')
const loading = ref(false)
const slots = ref<OptimalSlot[]>([])

async function generate() {
  loading.value = true
  try {
    slots.value = await scheduleOptimizerApi.generateSlots(platform.value)
  } catch {
    error('AI 최적 시간 분석에 실패했습니다')
  } finally {
    loading.value = false
  }
}
</script>
