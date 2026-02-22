<template>
  <div class="bg-white rounded-lg border border-gray-200 p-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900">트렌드 알림</h3>
      <button
        class="text-sm px-3 py-1.5 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
        @click="showForm = !showForm"
      >
        + 알림 추가
      </button>
    </div>

    <!-- 알림 추가 폼 -->
    <div v-if="showForm" class="mb-4 p-4 bg-gray-50 rounded-lg">
      <div class="flex gap-3">
        <input
          v-model="newKeyword"
          type="text"
          placeholder="키워드"
          class="flex-1 px-3 py-2 border rounded-lg text-sm"
        />
        <input
          v-model.number="newThreshold"
          type="number"
          placeholder="임계값"
          class="w-24 px-3 py-2 border rounded-lg text-sm"
        />
        <button
          class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700"
          @click="createAlert"
        >
          추가
        </button>
      </div>
    </div>

    <!-- 알림 목록 -->
    <div v-if="alerts.length === 0" class="text-center py-6 text-gray-400 text-sm">
      설정된 알림이 없습니다.
    </div>
    <div v-else class="space-y-2">
      <div
        v-for="alert in alerts"
        :key="alert.id"
        class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
      >
        <div class="flex items-center gap-3">
          <button
            :class="[
              'w-10 h-5 rounded-full relative transition-colors',
              alert.enabled ? 'bg-indigo-600' : 'bg-gray-300',
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
          <span class="text-sm font-medium text-gray-900">{{ alert.keyword }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-xs text-gray-500">임계값: {{ alert.threshold }}</span>
          <button
            class="text-xs text-red-500 hover:text-red-700"
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
