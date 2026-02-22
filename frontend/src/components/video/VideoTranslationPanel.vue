<template>
  <div class="bg-white rounded-lg border border-gray-200 p-6">
    <h3 class="text-lg font-semibold text-gray-900 mb-4">AI 다국어 자동화</h3>
    <p class="text-sm text-gray-500 mb-4">제목, 설명, 태그를 선택한 언어로 자동 번역합니다. (언어당 3 크레딧)</p>

    <!-- 언어 선택 -->
    <div class="flex flex-wrap gap-3 mb-6">
      <button
        v-for="lang in supportedLanguages"
        :key="lang.code"
        :class="[
          'px-4 py-2 rounded-lg border-2 transition-all text-sm font-medium',
          selectedLanguages.includes(lang.code)
            ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
            : 'border-gray-200 hover:border-gray-300 text-gray-600',
        ]"
        @click="toggleLanguage(lang.code)"
      >
        {{ lang.label }}
      </button>
    </div>

    <button
      :disabled="selectedLanguages.length === 0 || loading"
      class="w-full py-2.5 px-4 rounded-lg text-white font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      :class="loading ? 'bg-indigo-400' : 'bg-indigo-600 hover:bg-indigo-700'"
      @click="requestTranslation"
    >
      <span v-if="loading" class="flex items-center justify-center gap-2">
        <svg class="animate-spin h-4 w-4" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" fill="none" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        번역 중...
      </span>
      <span v-else>
        번역 요청 ({{ selectedLanguages.length * 3 }} 크레딧)
      </span>
    </button>

    <!-- 기존 번역 결과 -->
    <div v-if="translations.length > 0" class="mt-6 border-t pt-4">
      <h4 class="text-sm font-medium text-gray-700 mb-3">번역 결과</h4>
      <div class="space-y-2">
        <div
          v-for="translation in translations"
          :key="translation.id"
          class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
        >
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span class="text-sm font-medium text-gray-900">{{ languageLabel(translation.language) }}</span>
              <span
                :class="[
                  'text-xs px-2 py-0.5 rounded-full font-medium',
                  statusStyle(translation.status),
                ]"
              >
                {{ statusLabel(translation.status) }}
              </span>
            </div>
            <p v-if="translation.title" class="text-xs text-gray-500 truncate">
              {{ translation.title }}
            </p>
          </div>
          <button
            class="ml-3 text-xs text-red-500 hover:text-red-700 font-medium shrink-0"
            @click="deleteTranslation(translation.id)"
          >
            삭제
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { videoApi } from '@/api/video'
import type { VideoTranslation } from '@/types/video'

const props = defineProps<{
  videoId: number
}>()

const supportedLanguages = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
  { code: 'ja', label: '日本語' },
  { code: 'zh', label: '中文' },
  { code: 'es', label: 'Español' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
  { code: 'pt', label: 'Português' },
]

const selectedLanguages = ref<string[]>([])
const translations = ref<VideoTranslation[]>([])
const loading = ref(false)

function toggleLanguage(code: string) {
  const idx = selectedLanguages.value.indexOf(code)
  if (idx >= 0) {
    selectedLanguages.value.splice(idx, 1)
  } else {
    selectedLanguages.value.push(code)
  }
}

function languageLabel(code: string): string {
  const lang = supportedLanguages.find((l) => l.code === code)
  return lang ? lang.label : code
}

async function requestTranslation() {
  if (selectedLanguages.value.length === 0) return
  loading.value = true
  try {
    const result = await videoApi.requestTranslation(props.videoId, selectedLanguages.value)
    translations.value = [...result, ...translations.value]
    selectedLanguages.value = []
  } catch (e) {
    console.error('번역 요청 실패:', e)
  } finally {
    loading.value = false
  }
}

async function deleteTranslation(translationId: number) {
  try {
    await videoApi.deleteTranslation(props.videoId, translationId)
    translations.value = translations.value.filter((t) => t.id !== translationId)
  } catch (e) {
    console.error('번역 삭제 실패:', e)
  }
}

async function loadTranslations() {
  try {
    translations.value = await videoApi.getTranslations(props.videoId)
  } catch (e) {
    console.error('번역 목록 로드 실패:', e)
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '대기중',
    PROCESSING: '번역중',
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

onMounted(loadTranslations)
</script>
