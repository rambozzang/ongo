<template>
  <div class="bg-white rounded-lg border border-gray-200 p-6">
    <h3 class="text-lg font-semibold text-gray-900 mb-4">AI 영상 리사이즈</h3>
    <p class="text-sm text-gray-500 mb-4">원본 영상을 다양한 비율로 자동 변환합니다. (비율당 3 크레딧)</p>

    <!-- 비율 선택 -->
    <div class="flex flex-wrap gap-3 mb-6">
      <button
        v-for="spec in specs"
        :key="spec.ratio"
        :class="[
          'flex flex-col items-center px-4 py-3 rounded-lg border-2 transition-all',
          selectedRatios.includes(spec.ratio)
            ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
            : 'border-gray-200 hover:border-gray-300 text-gray-600',
        ]"
        @click="toggleRatio(spec.ratio)"
      >
        <div
          class="bg-gray-300 rounded mb-2"
          :style="{ width: spec.previewW + 'px', height: spec.previewH + 'px' }"
        />
        <span class="text-sm font-medium">{{ spec.ratio }}</span>
        <span class="text-xs text-gray-400">{{ spec.width }}x{{ spec.height }}</span>
      </button>
    </div>

    <button
      :disabled="selectedRatios.length === 0 || loading"
      class="w-full py-2.5 px-4 rounded-lg text-white font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      :class="loading ? 'bg-indigo-400' : 'bg-indigo-600 hover:bg-indigo-700'"
      @click="requestResize"
    >
      <span v-if="loading" class="flex items-center justify-center gap-2">
        <svg class="animate-spin h-4 w-4" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        처리 중...
      </span>
      <span v-else>
        리사이즈 요청 ({{ selectedRatios.length * 3 }} 크레딧)
      </span>
    </button>

    <!-- 기존 리사이즈 결과 -->
    <div v-if="resizes.length > 0" class="mt-6 border-t pt-4">
      <h4 class="text-sm font-medium text-gray-700 mb-3">리사이즈 결과</h4>
      <div class="space-y-2">
        <div
          v-for="resize in resizes"
          :key="resize.id"
          class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
        >
          <div class="flex items-center gap-3">
            <span class="text-sm font-medium text-gray-900">{{ resize.aspectRatio }}</span>
            <span class="text-xs text-gray-500">{{ resize.width }}x{{ resize.height }}</span>
          </div>
          <div class="flex items-center gap-2">
            <span
              :class="[
                'text-xs px-2 py-1 rounded-full font-medium',
                statusStyle(resize.status),
              ]"
            >
              {{ statusLabel(resize.status) }}
            </span>
            <a
              v-if="resize.fileUrl"
              :href="resize.fileUrl"
              target="_blank"
              class="text-xs text-indigo-600 hover:text-indigo-800"
            >
              다운로드
            </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { videoApi } from '@/api/video'
import type { VideoResize } from '@/types/video'

const props = defineProps<{
  videoId: number
}>()

const specs = [
  { ratio: '9:16', width: 1080, height: 1920, previewW: 27, previewH: 48 },
  { ratio: '1:1', width: 1080, height: 1080, previewW: 40, previewH: 40 },
  { ratio: '4:5', width: 1080, height: 1350, previewW: 32, previewH: 40 },
  { ratio: '16:9', width: 1920, height: 1080, previewW: 48, previewH: 27 },
]

const selectedRatios = ref<string[]>([])
const resizes = ref<VideoResize[]>([])
const loading = ref(false)

function toggleRatio(ratio: string) {
  const idx = selectedRatios.value.indexOf(ratio)
  if (idx >= 0) {
    selectedRatios.value.splice(idx, 1)
  } else {
    selectedRatios.value.push(ratio)
  }
}

async function requestResize() {
  if (selectedRatios.value.length === 0) return
  loading.value = true
  try {
    const result = await videoApi.requestResize(props.videoId, selectedRatios.value)
    resizes.value = [...result, ...resizes.value]
    selectedRatios.value = []
  } catch (e) {
    console.error('리사이즈 요청 실패:', e)
  } finally {
    loading.value = false
  }
}

async function loadResizes() {
  try {
    resizes.value = await videoApi.getResizes(props.videoId)
  } catch (e) {
    console.error('리사이즈 목록 로드 실패:', e)
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '대기중',
    PROCESSING: '처리중',
    COMPLETED: '완료',
    FAILED: '실패',
  }
  return map[status] || status
}

function statusStyle(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'bg-gray-100 text-gray-600',
    PROCESSING: 'bg-blue-100 text-blue-700',
    COMPLETED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
  }
  return map[status] || 'bg-gray-100 text-gray-600'
}

onMounted(loadResizes)
</script>
