<template>
  <div class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
    <div class="flex items-center justify-between border-b border-line-row px-[15px] py-3">
      <h3 class="text-[13px] font-bold text-content">트렌드 알림</h3>
      <button
        class="btn-primary text-body"
        @click="showForm = !showForm"
      >
        + 알림 추가
      </button>
    </div>

    <!-- 알림 추가 폼 -->
    <div v-if="showForm" class="m-4 rounded-lg bg-surface-muted p-4">
      <div class="flex gap-3">
        <input
          v-model="newKeyword"
          type="text"
          placeholder="키워드"
          class="input-field min-w-0 flex-1"
        />
        <input
          v-model.number="newThreshold"
          type="number"
          placeholder="임계값"
          class="input-field w-24"
        />
        <button
          class="btn-primary text-body"
          @click="createAlert"
        >
          추가
        </button>
      </div>
    </div>

    <!-- 알림 목록 -->
    <div v-if="alerts.length === 0" class="px-4 py-10 text-center text-body text-content-tertiary">
      설정된 알림이 없습니다.
    </div>
    <div v-else class="space-y-2 p-4">
      <div
        v-for="alert in alerts"
        :key="alert.id"
        class="flex items-center justify-between rounded-lg border border-line-row bg-surface-muted p-3"
      >
        <div class="flex items-center gap-3">
          <button
            :class="[
              'w-10 h-5 rounded-full relative transition-colors',
              alert.enabled ? 'bg-accent' : 'bg-muted-strong',
            ]"
            @click="toggleAlert(alert)"
          >
            <span
              :class="[
                'absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform',
                alert.enabled ? 'left-5' : 'left-0.5',
              ]"
            />
          </button>
          <span class="text-body font-medium text-content">{{ alert.keyword }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="font-mono text-body-xs text-content-tertiary">임계값: {{ alert.threshold }}</span>
          <button
            class="text-body-xs text-error-strong transition-opacity hover:opacity-80"
            @click="removeAlert(alert.id)"
          >
            삭제
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { trendApi } from '@/api/trend'
import type { TrendAlert } from '@/types/trend'

defineProps<{
  alerts: TrendAlert[]
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const showForm = ref(false)
const newKeyword = ref('')
const newThreshold = ref(50)

async function createAlert() {
  if (!newKeyword.value.trim()) return
  try {
    await trendApi.createAlert({ keyword: newKeyword.value, threshold: newThreshold.value })
    newKeyword.value = ''
    newThreshold.value = 50
    showForm.value = false
    emit('refresh')
  } catch (e) {
    console.error('알림 생성 실패:', e)
  }
}

async function toggleAlert(alert: TrendAlert) {
  try {
    await trendApi.updateAlert(alert.id, { enabled: !alert.enabled })
    emit('refresh')
  } catch (e) {
    console.error('알림 토글 실패:', e)
  }
}

async function removeAlert(id: number) {
  try {
    await trendApi.deleteAlert(id)
    emit('refresh')
  } catch (e) {
    console.error('알림 삭제 실패:', e)
  }
}
</script>
