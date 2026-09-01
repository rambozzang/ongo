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
    subscriberCount: number | null
    /** 영상 수를 모르면 `null` — 0 은 "영상이 없다" 는 관측이다. */
    videoCount: number | null
    /** 영상 수를 모르거나 0 이면 평균의 분모가 없다 → `null`. */
    avgViews: number | null
    avgEngagement: number | null
    /** 추가 시점에는 관측된 두 시점이 없다 → 항상 `null`. */
    growthRate: number | null
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
  /** 조회가 구독자 수를 주지 못했으면 `null` — 미리보기가 0 을 그리면 안 된다. */
  subscriberCount: number | null
  /** 조회가 영상 수를 주지 못했으면 `null`. */
  videoCount: number | null
  platformChannelId: string
  channelUrl: string
  /** 조회가 총 조회수를 주지 못했으면 `null` — 평균의 분자가 없다는 뜻이다. */
  totalViews: number | null
} | null>(null)

// Manual input mode for non-YouTube platforms
const isManualInput = ref(false)
const manualName = ref('')
/**
 * 수동 입력 구독자 수. **문자열로 들고 있는다 — 빈 값이 "모른다" 이기 때문이다.**
 *
 * 예전에는 `ref<number>(0)` 이라 입력칸이 `0` 으로 채워진 채 열렸고, 구독자 수를 모르는
 * 크리에이터가 그대로 저장하면 **실제로 0 명인 채널과 구분되지 않는 0** 이 저장됐다.
 * 그 0 은 순위·평균·비교표에 관측값처럼 섞인다.
 *
 * 타입이 `string | number` 인 이유: `<input type="number">` 의 `v-model` 은 Vue 가 값을
 * 자동으로 숫자로 바꾸지만, **비어 있으면 숫자로 바꿀 수 없어 빈 문자열이 그대로 남는다.**
 * 그 빈 문자열이 곧 "모른다" 이므로 [parseManualCount] 가 두 형태를 모두 받는다.
 */
const manualSubscriberCount = ref<string | number>('')
/**
 * 수동 입력 영상 수. **구독자 수와 같은 계약** — 빈 값은 "모른다" 이지 0 이 아니다.
 *
 * 예전에는 `ref<number>(0)` 이라 입력칸이 0 으로 채워진 채 열렸고, 모르는 사람이 그대로
 * 저장하면 **영상이 실제로 0 개인 채널과 구분되지 않는 0** 이 저장됐다. 이 값은 평균
 * 조회수의 분모라서, 0 이 되면 평균까지 "계산할 수 없음" 으로 바뀐다.
 */
const manualVideoCount = ref<string | number>('')

/**
 * 수동 입력값을 측정값으로 읽는다. **빈 값은 `null`(모른다) 이지 0 이 아니다.**
 *
 * 사용자가 직접 `0` 을 적었으면 그것은 "구독자가 없다" 는 **주장**이므로 `0` 으로 남긴다.
 * 숫자가 아니거나 음수인 값은 구독자 수가 될 수 없으므로 모르는 것으로 본다.
 */
function parseManualCount(raw: string | number): number | null {
  if (typeof raw === 'number') {
    return Number.isFinite(raw) && raw >= 0 ? raw : null
  }
  const trimmed = raw.trim()
  if (trimmed.length === 0) return null
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed < 0) return null
  return parsed
}

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
      /*
       * **`Math.max(videoCount, 1)` 은 없는 분모를 지어낸다.**
       *
       * 영상이 0 건이면 총 조회수를 1 로 나눠 "평균 = 총 조회수" 가 됐고, 조회수까지
       * 0 이면 "평균 0회" 라는 관측이 됐다. 영상이 있을 때만 실제 분모로 나눈다.
       */
      /*
       * **분자(총 조회수)와 분모(영상 수)가 모두 있어야 평균이 성립한다.**
       *
       * 조회 응답에 `viewCount` 가 없으면 예전에는 0 으로 채워져 "평균 0회" 가 됐다.
       * 서버 응답 DTO 의 `avgViews` 계약과 같은 규칙이다.
       */
      avgViews: previewData.value.videoCount !== null
        && previewData.value.videoCount > 0
        && previewData.value.totalViews !== null
        ? Math.floor(previewData.value.totalViews / previewData.value.videoCount)
        : null,
      avgEngagement: null,
      // 추가하는 시점에는 비교할 이전 관측이 없다. 0 은 "정체 중" 이라는 관측이 된다.
      growthRate: null,
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
      // 비워 두면 `null`(모른다). 직접 적은 0 은 사용자의 주장이므로 0 으로 남는다.
      subscriberCount: parseManualCount(manualSubscriberCount.value),
      videoCount: parseManualCount(manualVideoCount.value),
      // 수동 입력에는 총 조회수 항목이 없다 — 평균을 낼 근거가 아예 없다.
      avgViews: null,
      avgEngagement: null,
      growthRate: null,
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
  manualSubscriberCount.value = ''
  manualVideoCount.value = ''
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
              <h2 id="add-competitor-modal-title" class="text-title font-semibold text-gray-900 dark:text-white">
                경쟁 채널 추가
              </h2>
              <button
                class="p-1 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="모달 닫기"
                @click="handleClose"
              >
                <XMarkIcon class="w-5 h-5 text-gray-500 dark:text-gray-400" />
              </button>
            </div>

            <!-- Content -->
            <div class="p-4 space-y-4">
              <!-- URL Input -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-1">
                  채널 URL 또는 핸들
                </label>
                <input
                  v-model="channelUrl"
                  type="text"
                  placeholder="https://youtube.com/@channel 또는 @handle"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                  @input="detectAndSetPlatform"
                />
              </div>

              <!-- Platform Selector -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-1">
                  플랫폼
                </label>
                <select
                  v-model="selectedPlatform"
                  class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                >
                  <option value="YOUTUBE">유튜브</option>
                  <option value="TIKTOK">틱톡</option>
                  <option value="INSTAGRAM">인스타그램</option>
                </select>
              </div>

              <!-- Preview Button -->
              <button
                :disabled="!canPreview || isLoading"
                class="w-full px-4 py-2 bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                @click="loadPreview"
              >
                {{ isLoading ? '불러오는 중...' : '채널 정보 불러오기' }}
              </button>

              <!-- Error Message -->
              <div
                v-if="errorMessage && !isManualInput"
                class="p-3 bg-error-subtle border border-error rounded-lg"
              >
                <p class="text-body text-error-strong">{{ errorMessage }}</p>
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
                  class="p-4 border border-warning rounded-lg bg-warning-subtle space-y-3"
                >
                  <p class="text-body text-warning-strong">
                    {{ errorMessage }}
                  </p>
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-1">
                      채널명 <span class="text-error-strong">*</span>
                    </label>
                    <input
                      v-model="manualName"
                      type="text"
                      placeholder="채널 이름을 입력하세요"
                      class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                  </div>
                  <div class="grid grid-cols-2 gap-3">
                    <div>
                      <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-1">
                        구독자 수 <span class="text-gray-500 dark:text-gray-400">(선택)</span>
                      </label>
                      <!-- 기본값을 0 으로 채우면 "모른다" 와 "0 명" 이 같은 저장이 된다. -->
                      <input
                        v-model="manualSubscriberCount"
                        type="number"
                        min="0"
                        placeholder="모르면 비워 두세요"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                      />
                    </div>
                    <div>
                      <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-1">
                        영상 수 <span class="text-gray-500 dark:text-gray-400">(선택)</span>
                      </label>
                      <input
                        v-model="manualVideoCount"
                        type="number"
                        min="0"
                        placeholder="모르면 비워 두세요"
                        class="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
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
                      <span class="text-title font-bold text-gray-500 dark:text-gray-400">
                        {{ previewData.name.charAt(0) }}
                      </span>
                    </div>
                    <div>
                      <h3 class="font-semibold text-gray-900 dark:text-white">
                        {{ previewData.name }}
                      </h3>
                      <p class="text-body text-gray-600 dark:text-gray-400">
                        {{ selectedPlatform === 'YOUTUBE' ? '유튜브' : selectedPlatform === 'TIKTOK' ? '틱톡' : selectedPlatform === 'INSTAGRAM' ? '인스타그램' : '네이버 클립' }}
                      </p>
                    </div>
                  </div>
                  <div class="grid grid-cols-2 gap-2 text-body">
                    <div>
                      <span class="text-gray-600 dark:text-gray-400">구독자</span>
                      <p class="font-semibold text-gray-900 dark:text-white">
                        {{ previewData.subscriberCount === null ? $t('analyticsView.notMeasured') : formatNumber(previewData.subscriberCount) }}
                      </p>
                    </div>
                    <div>
                      <span class="text-gray-600 dark:text-gray-400">영상 수</span>
                      <p class="font-semibold text-gray-900 dark:text-white">
                        {{ previewData.videoCount === null ? $t('analyticsView.notMeasured') : previewData.videoCount }}
                      </p>
                    </div>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Footer -->
            <div class="flex items-center justify-end space-x-2 p-4 border-t border-gray-200 dark:border-gray-700">
              <button
                class="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
                @click="handleClose"
              >
                취소
              </button>
              <button
                :disabled="!previewData && !canAddManual"
                class="px-4 py-2 bg-primary-600 hover:bg-primary-700 text-white rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                @click="handleAdd"
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
