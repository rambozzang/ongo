<script setup lang="ts">
import { ref, computed } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import type { CompetitorPlatform } from '@/types/competitor'
import { competitorApi } from '@/api/competitor'

interface Props {
  isOpen: boolean
}

interface Emits {
  (e: 'close'): void
  (e: 'add', data: {
    name: string
    channelUrl: string
    platform: CompetitorPlatform
    avatarUrl: string
    subscriberCount: number
    videoCount: number
    avgViews: number
    avgEngagement: number
    growthRate: number
    lastVideoAt: string
    isTracking: boolean
  }): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const channelUrl = ref('')
const selectedPlatform = ref<CompetitorPlatform>('YOUTUBE')
const isLoading = ref(false)
const errorMessage = ref('')
const previewData = ref<{
  name: string
  avatarUrl: string
  subscriberCount: number
  videoCount: number
  platformChannelId: string
  channelUrl: string
  totalViews: number
} | null>(null)

// Manual input mode for non-YouTube platforms
const isManualInput = ref(false)
const manualName = ref('')
const manualSubscriberCount = ref<number>(0)
const manualVideoCount = ref<number>(0)

const detectedPlatform = computed(() => {
  const url = channelUrl.value.toLowerCase()
  if (url.includes('youtube.com') || url.includes('youtu.be')) return 'YOUTUBE'
  if (url.includes('tiktok.com')) return 'TIKTOK'
  if (url.includes('instagram.com')) return 'INSTAGRAM'
  if (url.includes('clip.naver.com') || url.includes('naver.com/clip')) return 'NAVER_CLIP'
  return null
})

const canPreview = computed(() => {
  return channelUrl.value.trim().length > 0
})

const canAddManual = computed(() => {
  return isManualInput.value && manualName.value.trim().length > 0
})

function detectAndSetPlatform() {
  if (detectedPlatform.value) {
    selectedPlatform.value = detectedPlatform.value
  }
}

async function loadPreview() {
  if (!canPreview.value) return

  isLoading.value = true
  previewData.value = null
  errorMessage.value = ''
  isManualInput.value = false

  try {
    const result = await competitorApi.lookup({
      platform: selectedPlatform.value,
      query: channelUrl.value.trim(),
    })

    if (result.requiresManualInput) {
      isManualInput.value = true
      errorMessage.value = result.message || '이 플랫폼은 채널 정보를 직접 입력해주세요.'
    } else if (result.found) {
      previewData.value = {
        name: result.channelName || '',
        avatarUrl: result.profileImageUrl || '',
        subscriberCount: result.subscriberCount,
        videoCount: result.videoCount,
        platformChannelId: result.platformChannelId || '',
        channelUrl: result.channelUrl || channelUrl.value.trim(),
        totalViews: result.totalViews,
      }
    } else {
      errorMessage.value = result.message || '채널을 찾을 수 없습니다.'
    }
  } catch (e: unknown) {
    const axiosErr = e as { response?: { data?: { message?: string } } }
    errorMessage.value = axiosErr?.response?.data?.message || '채널 정보 조회 중 오류가 발생했습니다.'
  } finally {
    isLoading.value = false
  }
}

function handleAdd() {
  if (previewData.value) {
    emit('add', {
      name: previewData.value.name,
      channelUrl: previewData.value.channelUrl,
      platform: selectedPlatform.value,
      avatarUrl: previewData.value.avatarUrl,
      subscriberCount: previewData.value.subscriberCount,
      videoCount: previewData.value.videoCount,
      avgViews: previewData.value.subscriberCount > 0
        ? Math.floor(previewData.value.totalViews / Math.max(previewData.value.videoCount, 1))
        : 0,
      avgEngagement: 0,
      growthRate: 0,
      lastVideoAt: new Date().toISOString(),
      isTracking: true,
    })
    handleClose()
  } else if (isManualInput.value && manualName.value.trim()) {
    emit('add', {
      name: manualName.value.trim(),
      channelUrl: channelUrl.value.trim(),
      platform: selectedPlatform.value,
      avatarUrl: '',
      subscriberCount: manualSubscriberCount.value,
      videoCount: manualVideoCount.value,
      avgViews: 0,
      avgEngagement: 0,
      growthRate: 0,
      lastVideoAt: new Date().toISOString(),
      isTracking: true,
    })
    handleClose()
  }
}

function handleClose() {
  channelUrl.value = ''
  selectedPlatform.value = 'YOUTUBE'
  previewData.value = null
  errorMessage.value = ''
  isManualInput.value = false
  manualName.value = ''
  manualSubscriberCount.value = 0
  manualVideoCount.value = 0
  emit('close')
}

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`
  }
  return num.toString()
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-200"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-competitor-modal-title"
        @click.self="handleClose"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          enter-to-class="opacity-100 scale-100"
          leave-active-class="transition-all duration-200"
          leave-from-class="opacity-100 scale-100"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="isOpen"
            class="relative w-full max-w-md bg-white dark:bg-gray-800 rounded-lg shadow-xl"
            @keydown.escape="handleClose"
          >
            <!-- Header -->
            <div class="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
              <h2 id="add-competitor-modal-title" class="text-lg font-semibold text-gray-900 dark:text-white">
                경쟁 채널 추가
              </h2>
              <button
                @click="handleClose"
                class="p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="모달 닫기"
              >
                <XMarkIcon class="w-5 h-5 text-gray-500 dark:text-gray-400" />
              </button>
            </div>

            <!-- Content -->
            <div class="p-4 space-y-4">
              <!-- URL Input -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  채널 URL 또는 핸들
                </label>
                <input
                  v-model="channelUrl"
                  type="text"
                  placeholder="https://youtube.com/@channel 또는 @handle"
                  @input="detectAndSetPlatform"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <!-- Platform Selector -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  플랫폼
                </label>
                <select
                  v-model="selectedPlatform"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="YOUTUBE">유튜브</option>
                  <option value="TIKTOK">틱톡</option>
                  <option value="INSTAGRAM">인스타그램</option>
                  <option value="NAVER_CLIP">네이버 클립</option>
                </select>
              </div>

              <!-- Preview Button -->
              <button
                @click="loadPreview"
                :disabled="!canPreview || isLoading"
                class="w-full px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {{ isLoading ? '불러오는 중...' : '채널 정보 불러오기' }}
              </button>

              <!-- Error Message -->
              <div
                v-if="errorMessage && !isManualInput"
                class="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg"
              >
                <p class="text-sm text-red-700 dark:text-red-400">{{ errorMessage }}</p>
              </div>

              <!-- Manual Input Form (for non-YouTube platforms) -->
              <Transition
                enter-active-class="transition-all duration-200"
                enter-from-class="opacity-0 scale-95"
                enter-to-class="opacity-100 scale-100"
                leave-active-class="transition-all duration-200"
                leave-from-class="opacity-100 scale-100"
                leave-to-class="opacity-0 scale-95"
              >
                <div
                  v-if="isManualInput"
                  class="p-4 border border-yellow-200 dark:border-yellow-800 rounded-lg bg-yellow-50 dark:bg-yellow-900/20 space-y-3"
                >
                  <p class="text-sm text-yellow-700 dark:text-yellow-400">
                    {{ errorMessage }}
                  </p>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                      채널명 <span class="text-red-500">*</span>
                    </label>
                    <input
                      v-model="manualName"
                      type="text"
                      placeholder="채널 이름을 입력하세요"
                      class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div class="grid grid-cols-2 gap-3">
                    <div>
                      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        구독자 수
                      </label>
                      <input
                        v-model.number="manualSubscriberCount"
                        type="number"
                        min="0"
                        placeholder="0"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                    <div>
                      <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                        영상 수
                      </label>
                      <input
                        v-model.number="manualVideoCount"
                        type="number"
                        min="0"
                        placeholder="0"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  </div>
                </div>
              </Transition>

              <!-- Preview Card -->
              <Transition
                enter-active-class="transition-all duration-200"
                enter-from-class="opacity-0 scale-95"
                enter-to-class="opacity-100 scale-100"
                leave-active-class="transition-all duration-200"
                leave-from-class="opacity-100 scale-100"
                leave-to-class="opacity-0 scale-95"
              >
                <div
                  v-if="previewData"
                  class="p-4 border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-900"
                >
                  <div class="flex items-center space-x-3 mb-3">
                    <img
                      v-if="previewData.avatarUrl"
                      :src="previewData.avatarUrl"
                      :alt="previewData.name"
                      class="w-12 h-12 rounded-full"
                    />
                    <div
                      v-else
                      class="w-12 h-12 rounded-full bg-gray-300 dark:bg-gray-600 flex items-center justify-center"
                    >
                      <span class="text-lg font-bold text-gray-500 dark:text-gray-400">
                        {{ previewData.name.charAt(0) }}
                      </span>
                    </div>
                    <div>
                      <h3 class="font-semibold text-gray-900 dark:text-white">
                        {{ previewData.name }}
                      </h3>
                      <p class="text-sm text-gray-600 dark:text-gray-400">
                        {{ selectedPlatform === 'YOUTUBE' ? '유튜브' : selectedPlatform === 'TIKTOK' ? '틱톡' : selectedPlatform === 'INSTAGRAM' ? '인스타그램' : '네이버 클립' }}
                      </p>
                    </div>
                  </div>
                  <div class="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <span class="text-gray-600 dark:text-gray-400">구독자</span>
                      <p class="font-semibold text-gray-900 dark:text-white">
                        {{ formatNumber(previewData.subscriberCount) }}
                      </p>
                    </div>
                    <div>
                      <span class="text-gray-600 dark:text-gray-400">영상 수</span>
                      <p class="font-semibold text-gray-900 dark:text-white">
                        {{ previewData.videoCount }}
                      </p>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Footer -->
            <div class="flex items-center justify-end space-x-2 p-4 border-t border-gray-200 dark:border-gray-700">
              <button
                @click="handleClose"
                class="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
              >
                취소
              </button>
              <button
                @click="handleAdd"
                :disabled="!previewData && !canAddManual"
                class="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                추가
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
