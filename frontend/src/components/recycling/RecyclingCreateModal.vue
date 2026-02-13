<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { XMarkIcon } from '@heroicons/vue/24/outline'
import type {
  RecyclingQueue,
  RecyclingFrequency,
  TitleVariation,
  RecyclingQueueCreateRequest,
} from '@/types/recycling'

const props = defineProps<{
  isOpen: boolean
  queue?: RecyclingQueue
}>()

const emit = defineEmits<{
  close: []
  save: [data: RecyclingQueueCreateRequest]
}>()

const name = ref('')
const frequency = ref<RecyclingFrequency>('weekly')
const scheduleDays = ref<number[]>([1])
const scheduleTime = ref('18:00')
const platforms = ref<string[]>([])
const minViews = ref<number | undefined>(undefined)
const maxAge = ref<number | undefined>(undefined)
const categories = ref('')
const titleVariation = ref<TitleVariation>('same')

const DAY_OPTIONS = [
  { value: 1, label: '월' },
  { value: 2, label: '화' },
  { value: 3, label: '수' },
  { value: 4, label: '목' },
  { value: 5, label: '금' },
  { value: 6, label: '토' },
  { value: 0, label: '일' },
]

const PLATFORM_OPTIONS = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const FREQUENCY_OPTIONS: { value: RecyclingFrequency; label: string }[] = [
  { value: 'weekly', label: '주간' },
  { value: 'biweekly', label: '격주' },
  { value: 'monthly', label: '월간' },
]

const TITLE_VARIATION_OPTIONS: { value: TitleVariation; label: string; description: string }[] = [
  { value: 'same', label: '동일', description: '원본 제목 그대로 사용' },
  { value: 'ai', label: 'AI 변형', description: 'AI가 제목을 자동으로 변형' },
  { value: 'manual', label: '직접 수정', description: '게시 전 직접 수정' },
]

const isEditing = computed(() => !!props.queue)
const modalTitle = computed(() => (isEditing.value ? '큐 수정' : '새 큐 생성'))

const isValid = computed(() => {
  return (
    name.value.trim().length > 0 &&
    scheduleDays.value.length > 0 &&
    platforms.value.length > 0
  )
})

function resetForm() {
  name.value = ''
  frequency.value = 'weekly'
  scheduleDays.value = [1]
  scheduleTime.value = '18:00'
  platforms.value = []
  minViews.value = undefined
  maxAge.value = undefined
  categories.value = ''
  titleVariation.value = 'same'
}

function populateFromQueue(queue: RecyclingQueue) {
  name.value = queue.name
  frequency.value = queue.frequency
  scheduleDays.value = [...queue.scheduleDays]
  scheduleTime.value = queue.scheduleTime
  platforms.value = [...queue.platforms]
  minViews.value = queue.filterCriteria.minViews
  maxAge.value = queue.filterCriteria.maxAge
  categories.value = queue.filterCriteria.categories?.join(', ') || ''
  titleVariation.value = queue.titleVariation
}

watch(
  () => props.isOpen,
  (newVal) => {
    if (newVal) {
      if (props.queue) {
        populateFromQueue(props.queue)
      } else {
        resetForm()
      }
    }
  }
)

function toggleDay(day: number) {
  const index = scheduleDays.value.indexOf(day)
  if (index === -1) {
    scheduleDays.value.push(day)
  } else {
    scheduleDays.value.splice(index, 1)
  }
}

function togglePlatform(platform: string) {
  const index = platforms.value.indexOf(platform)
  if (index === -1) {
    platforms.value.push(platform)
  } else {
    platforms.value.splice(index, 1)
  }
}

function handleSubmit() {
  if (!isValid.value) return

  const parsedCategories = categories.value
    .split(',')
    .map((c) => c.trim())
    .filter((c) => c.length > 0)

  const data: RecyclingQueueCreateRequest = {
    name: name.value.trim(),
    frequency: frequency.value,
    scheduleDays: scheduleDays.value,
    scheduleTime: scheduleTime.value,
    platforms: platforms.value,
    titleVariation: titleVariation.value,
    filterCriteria: {
      minViews: minViews.value,
      maxAge: maxAge.value,
      categories: parsedCategories.length > 0 ? parsedCategories : undefined,
    },
  }

  emit('save', data)
  emit('close')
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-50 overflow-y-auto"
      role="dialog"
      aria-modal="true"
      aria-labelledby="recycling-create-modal-title"
    >
      <!-- Backdrop -->
      <div
        class="fixed inset-0 bg-black/50 transition-opacity"
        aria-hidden="true"
        @click="handleClose"
      />

      <!-- Modal -->
      <div class="flex min-h-full items-center justify-center p-4">
        <div
          class="relative w-full max-w-2xl bg-white dark:bg-gray-800 rounded-2xl shadow-xl transform transition-all"
          @click.stop
          @keydown.escape="handleClose"
        >
          <!-- Header -->
          <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
            <h2 id="recycling-create-modal-title" class="text-xl font-bold text-gray-900 dark:text-white">
              {{ modalTitle }}
            </h2>
            <button
              @click="handleClose"
              class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              aria-label="모달 닫기"
            >
              <XMarkIcon class="w-5 h-5" />
            </button>
          </div>

          <!-- Body -->
          <div class="px-6 py-5 space-y-6 max-h-[70vh] overflow-y-auto">
            <!-- Queue Name -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                큐 이름
              </label>
              <input
                v-model="name"
                type="text"
                placeholder="예: 인기 영상 주간 재업로드"
                class="w-full px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
              />
            </div>

            <!-- Frequency -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                반복 주기
              </label>
              <div class="flex gap-3">
                <button
                  v-for="option in FREQUENCY_OPTIONS"
                  :key="option.value"
                  @click="frequency = option.value"
                  :class="[
                    'flex-1 px-4 py-2.5 rounded-lg border text-sm font-medium transition-colors',
                    frequency === option.value
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                      : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500',
                  ]"
                >
                  {{ option.label }}
                </button>
              </div>
            </div>

            <!-- Schedule Days -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                요일 선택
              </label>
              <div class="flex gap-2">
                <button
                  v-for="day in DAY_OPTIONS"
                  :key="day.value"
                  @click="toggleDay(day.value)"
                  :class="[
                    'w-10 h-10 rounded-lg text-sm font-medium transition-colors',
                    scheduleDays.includes(day.value)
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-gray-600',
                  ]"
                >
                  {{ day.label }}
                </button>
              </div>
            </div>

            <!-- Schedule Time -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                게시 시간
              </label>
              <input
                v-model="scheduleTime"
                type="time"
                class="w-full px-4 py-2.5 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
              />
            </div>

            <!-- Platforms -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                게시 플랫폼
              </label>
              <div class="grid grid-cols-2 gap-3">
                <button
                  v-for="platform in PLATFORM_OPTIONS"
                  :key="platform.value"
                  @click="togglePlatform(platform.value)"
                  :class="[
                    'flex items-center gap-2 px-4 py-2.5 rounded-lg border text-sm font-medium transition-colors',
                    platforms.includes(platform.value)
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                      : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500',
                  ]"
                >
                  <span
                    :class="[
                      'w-4 h-4 rounded border-2 flex items-center justify-center transition-colors',
                      platforms.includes(platform.value)
                        ? 'border-blue-500 bg-blue-500'
                        : 'border-gray-400 dark:border-gray-500',
                    ]"
                  >
                    <svg
                      v-if="platforms.includes(platform.value)"
                      class="w-3 h-3 text-white"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
                    </svg>
                  </span>
                  {{ platform.label }}
                </button>
              </div>
            </div>

            <!-- Filter Criteria -->
            <div class="space-y-4">
              <h3 class="text-sm font-medium text-gray-700 dark:text-gray-300">
                필터 조건
              </h3>

              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">
                    최소 조회수
                  </label>
                  <input
                    v-model.number="minViews"
                    type="number"
                    placeholder="예: 10000"
                    min="0"
                    class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
                  />
                </div>
                <div>
                  <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">
                    최대 게시 기간 (개월)
                  </label>
                  <input
                    v-model.number="maxAge"
                    type="number"
                    placeholder="예: 6"
                    min="1"
                    max="24"
                    class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
                  />
                </div>
              </div>

              <div>
                <label class="block text-xs text-gray-500 dark:text-gray-400 mb-1">
                  카테고리 (쉼표로 구분)
                </label>
                <input
                  v-model="categories"
                  type="text"
                  placeholder="예: 브이로그, 일상, 꿀팁"
                  class="w-full px-3 py-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500 transition-colors"
                />
              </div>
            </div>

            <!-- Title Variation -->
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                제목 변형 옵션
              </label>
              <div class="space-y-2">
                <button
                  v-for="option in TITLE_VARIATION_OPTIONS"
                  :key="option.value"
                  @click="titleVariation = option.value"
                  :class="[
                    'w-full flex items-start gap-3 px-4 py-3 rounded-lg border text-left transition-colors',
                    titleVariation === option.value
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30'
                      : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 hover:border-gray-400 dark:hover:border-gray-500',
                  ]"
                >
                  <span
                    :class="[
                      'mt-0.5 w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0',
                      titleVariation === option.value
                        ? 'border-blue-500'
                        : 'border-gray-400 dark:border-gray-500',
                    ]"
                  >
                    <span
                      v-if="titleVariation === option.value"
                      class="w-2 h-2 rounded-full bg-blue-500"
                    />
                  </span>
                  <div>
                    <p
                      :class="[
                        'text-sm font-medium',
                        titleVariation === option.value
                          ? 'text-blue-700 dark:text-blue-300'
                          : 'text-gray-700 dark:text-gray-300',
                      ]"
                    >
                      {{ option.label }}
                    </p>
                    <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                      {{ option.description }}
                    </p>
                  </div>
                </button>
              </div>
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700">
            <button
              @click="handleClose"
              class="px-4 py-2.5 text-sm font-medium text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-600 transition-colors"
            >
              취소
            </button>
            <button
              @click="handleSubmit"
              :disabled="!isValid"
              :class="[
                'px-6 py-2.5 text-sm font-medium text-white rounded-lg transition-colors',
                isValid
                  ? 'bg-blue-600 hover:bg-blue-700'
                  : 'bg-gray-300 dark:bg-gray-600 cursor-not-allowed',
              ]"
            >
              {{ isEditing ? '수정' : '생성' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
