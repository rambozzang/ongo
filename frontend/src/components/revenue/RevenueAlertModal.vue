<script setup lang="ts">
import { ref, watch } from 'vue'
import BaseModal from '@/components/common/BaseModal.vue'
import { useRevenueStore } from '@/stores/revenue'
import type { AlertType, RevenueAlertConfig } from '@/types/revenue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const revenueStore = useRevenueStore()

// 알림 타입별 로컬 설정 상태 (백엔드 필드명에 맞춤)
interface LocalConfig {
  isEnabled: boolean
  scheduleTime: string // HH:mm (DAILY_SUMMARY용)
  thresholdValue: number // 임계값 (ANOMALY_DETECTION용)
}

const configs = ref<Record<AlertType, LocalConfig>>({
  DAILY_SUMMARY: { isEnabled: false, scheduleTime: '09:00', thresholdValue: 30 },
  ANOMALY_DETECTION: { isEnabled: false, scheduleTime: '09:00', thresholdValue: 30 },
  GOAL_ACHIEVEMENT: { isEnabled: false, scheduleTime: '09:00', thresholdValue: 0 },
  MILESTONE: { isEnabled: false, scheduleTime: '09:00', thresholdValue: 0 },
})

const saving = ref(false)
const error = ref<string | null>(null)

// 모달 열릴 때 스토어의 설정을 로컬 상태에 동기화
watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      error.value = null
      await revenueStore.fetchAlertConfigs()
      error.value = revenueStore.alertConfigError
      syncFromStore()
    }
  },
)

function syncFromStore() {
  for (const cfg of revenueStore.alertConfigs) {
    const type = cfg.alertType as AlertType
    if (configs.value[type]) {
      configs.value[type].isEnabled = cfg.isEnabled
      if (cfg.scheduleTime) configs.value[type].scheduleTime = cfg.scheduleTime
      if (cfg.thresholdValue != null) configs.value[type].thresholdValue = cfg.thresholdValue
    }
  }
}

async function handleSave() {
  saving.value = true
  error.value = null
  try {
    const alertTypes: AlertType[] = [
      'DAILY_SUMMARY',
      'ANOMALY_DETECTION',
      'GOAL_ACHIEVEMENT',
      'MILESTONE',
    ]

    for (const alertType of alertTypes) {
      const local = configs.value[alertType]
      const existing = revenueStore.alertConfigs.find((c) => c.alertType === alertType)

      const payload: Omit<RevenueAlertConfig, 'id'> = {
        alertType,
        isEnabled: local.isEnabled,
        scheduleTime: alertType === 'DAILY_SUMMARY' ? local.scheduleTime : undefined,
        thresholdValue: alertType === 'ANOMALY_DETECTION' ? local.thresholdValue : undefined,
      }

      if (existing) {
        await revenueStore.updateAlertConfig(existing.id, payload)
      } else {
        await revenueStore.saveAlertConfig(payload)
      }
    }

    emit('update:modelValue', false)
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : '수익 알림 설정을 저장하지 못했습니다.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <BaseModal
    :model-value="modelValue"
    :title="$t('revenue.alerts.title')"
    max-width="lg"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="space-y-6">
      <div
        v-if="error"
        class="rounded-lg border border-error-subtle bg-error-subtle px-3 py-2 text-sm text-error-strong"
        role="alert"
      >
        {{ error }}
      </div>
      <!-- 일간 수익 요약 -->
      <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1">
            <p class="font-medium text-gray-900 dark:text-gray-100">
              {{ $t('revenue.alerts.DAILY_SUMMARY.title') }}
            </p>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenue.alerts.DAILY_SUMMARY.description') }}
            </p>
            <!-- 시간 설정 -->
            <div v-if="configs.DAILY_SUMMARY.isEnabled" class="mt-3">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('revenue.alerts.DAILY_SUMMARY.timeLabel') }}
              </label>
              <input
                v-model="configs.DAILY_SUMMARY.scheduleTime"
                type="time"
                class="input-field w-32"
              />
            </div>
          </div>
          <!-- 토글 -->
          <button
            type="button"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
            :class="
              configs.DAILY_SUMMARY.isEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'
            "
            role="switch"
            :aria-checked="configs.DAILY_SUMMARY.isEnabled"
            @click="configs.DAILY_SUMMARY.isEnabled = !configs.DAILY_SUMMARY.isEnabled"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
              :class="configs.DAILY_SUMMARY.isEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>
      </div>

      <!-- 수익 이상 감지 -->
      <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1">
            <p class="font-medium text-gray-900 dark:text-gray-100">
              {{ $t('revenue.alerts.ANOMALY_DETECTION.title') }}
            </p>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenue.alerts.ANOMALY_DETECTION.description') }}
            </p>
            <!-- 임계값 설정 -->
            <div v-if="configs.ANOMALY_DETECTION.isEnabled" class="mt-3 space-y-1">
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('revenue.alerts.ANOMALY_DETECTION.thresholdLabel') }}
              </label>
              <input
                v-model.number="configs.ANOMALY_DETECTION.thresholdValue"
                type="number"
                min="1"
                max="100"
                class="input-field w-32"
              />
              <p class="text-xs text-gray-400">
                {{ $t('revenue.alerts.ANOMALY_DETECTION.thresholdHint') }}
              </p>
            </div>
          </div>
          <button
            type="button"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
            :class="
              configs.ANOMALY_DETECTION.isEnabled
                ? 'bg-primary-600'
                : 'bg-gray-200 dark:bg-gray-600'
            "
            role="switch"
            :aria-checked="configs.ANOMALY_DETECTION.isEnabled"
            @click="configs.ANOMALY_DETECTION.isEnabled = !configs.ANOMALY_DETECTION.isEnabled"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
              :class="configs.ANOMALY_DETECTION.isEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>
      </div>

      <!-- 목표 달성 알림 -->
      <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1">
            <p class="font-medium text-gray-900 dark:text-gray-100">
              {{ $t('revenue.alerts.GOAL_ACHIEVEMENT.title') }}
            </p>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenue.alerts.GOAL_ACHIEVEMENT.description') }}
            </p>
            <!-- 목표 금액 설정 -->
            <div v-if="configs.GOAL_ACHIEVEMENT.isEnabled" class="mt-3">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('revenue.alerts.GOAL_ACHIEVEMENT.goalLabel') }}
              </label>
              <input
                v-model.number="configs.GOAL_ACHIEVEMENT.thresholdValue"
                type="number"
                min="0"
                step="100000"
                class="input-field w-48"
              />
            </div>
          </div>
          <button
            type="button"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
            :class="
              configs.GOAL_ACHIEVEMENT.isEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'
            "
            role="switch"
            :aria-checked="configs.GOAL_ACHIEVEMENT.isEnabled"
            @click="configs.GOAL_ACHIEVEMENT.isEnabled = !configs.GOAL_ACHIEVEMENT.isEnabled"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
              :class="configs.GOAL_ACHIEVEMENT.isEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>
      </div>

      <!-- 마일스톤 알림 -->
      <div class="rounded-lg border border-gray-200 p-4 dark:border-gray-700">
        <div class="flex items-start justify-between gap-4">
          <div class="flex-1">
            <p class="font-medium text-gray-900 dark:text-gray-100">
              {{ $t('revenue.alerts.MILESTONE.title') }}
            </p>
            <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenue.alerts.MILESTONE.description') }}
            </p>
          </div>
          <button
            type="button"
            class="relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
            :class="configs.MILESTONE.isEnabled ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-600'"
            role="switch"
            :aria-checked="configs.MILESTONE.isEnabled"
            @click="configs.MILESTONE.isEnabled = !configs.MILESTONE.isEnabled"
          >
            <span
              class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
              :class="configs.MILESTONE.isEnabled ? 'translate-x-5' : 'translate-x-0'"
            />
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <button class="btn-secondary" @click="$emit('update:modelValue', false)">
        {{ $t('revenue.alerts.cancel') }}
      </button>
      <button class="btn-primary" :disabled="saving" @click="handleSave">
        {{ saving ? $t('action.loading') : $t('revenue.alerts.save') }}
      </button>
    </template>
  </BaseModal>
</template>
