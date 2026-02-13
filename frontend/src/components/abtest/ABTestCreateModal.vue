<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { XMarkIcon, PlusIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { TestType, TestVariant } from '@/types/abtest'
import { videoApi } from '@/api/video'

interface Emits {
  (e: 'close'): void
  (e: 'create', data: {
    name: string
    videoTitle: string
    videoId: string
    type: TestType
    variants: TestVariant[]
    duration: number
    sampleSize: number
    confidence: number
    status: 'draft' | 'running'
  }): void
}

const emit = defineEmits<Emits>()

const step = ref(1)
const testName = ref('')
const videoTitle = ref('')
const videoId = ref('v_' + Date.now())
const testType = ref<TestType>('thumbnail')

const variants = ref<Array<{
  id: string
  label: string
  content: string
  thumbnailUrl?: string
}>>([
  { id: 'v1', label: '변형 A', content: '', thumbnailUrl: '' },
  { id: 'v2', label: '변형 B', content: '', thumbnailUrl: '' }
])

const duration = ref(24)
const sampleSize = ref(1000)
const confidence = ref(95)

const videoOptions = ref<Array<{ id: string; title: string }>>([])
const loadingVideos = ref(false)

onMounted(async () => {
  loadingVideos.value = true
  try {
    const response = await videoApi.list({ page: 0, size: 50 })
    videoOptions.value = response.content.map(v => ({
      id: String(v.id),
      title: v.title,
    }))
  } catch {
    // silently ignore — video list will be empty
  } finally {
    loadingVideos.value = false
  }
})

const canProceedStep1 = computed(() => {
  return testName.value.trim() !== '' && videoTitle.value.trim() !== ''
})

const canProceedStep2 = computed(() => {
  return variants.value.every(v => {
    if (testType.value === 'thumbnail') {
      return v.label.trim() !== '' && v.thumbnailUrl && v.thumbnailUrl.trim() !== ''
    }
    return v.label.trim() !== '' && v.content.trim() !== ''
  })
})

const addVariant = () => {
  if (variants.value.length < 4) {
    const letters = ['C', 'D', 'E', 'F']
    const nextLetter = letters[variants.value.length - 2]
    variants.value.push({
      id: 'v' + (variants.value.length + 1),
      label: '변형 ' + nextLetter,
      content: '',
      thumbnailUrl: ''
    })
  }
}

const removeVariant = (index: number) => {
  if (variants.value.length > 2) {
    variants.value.splice(index, 1)
  }
}

const selectVideo = (video: { id: string; title: string }) => {
  videoId.value = video.id
  videoTitle.value = video.title
}

const nextStep = () => {
  if (step.value < 3) {
    step.value++
  }
}

const prevStep = () => {
  if (step.value > 1) {
    step.value--
  }
}

const handleCreate = (startNow: boolean) => {
  const testVariants: TestVariant[] = variants.value.map(v => ({
    id: v.id,
    label: v.label,
    content: v.content,
    thumbnailUrl: v.thumbnailUrl,
    impressions: 0,
    clicks: 0,
    ctr: 0,
    watchTime: 0,
    isWinner: false
  }))

  emit('create', {
    name: testName.value,
    videoTitle: videoTitle.value,
    videoId: videoId.value,
    type: testType.value,
    variants: testVariants,
    duration: duration.value,
    sampleSize: sampleSize.value,
    confidence: confidence.value,
    status: startNow ? 'running' : 'draft'
  })
}

const typeLabel = computed(() => {
  const labels = {
    thumbnail: '썸네일',
    title: '제목',
    description: '설명'
  }
  return labels[testType.value]
})
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="abtest-create-modal-title"
  >
    <div
      class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-3xl w-full max-h-[90vh] overflow-hidden flex flex-col"
      @keydown.escape="emit('close')"
    >
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
        <div>
          <h2 id="abtest-create-modal-title" class="text-2xl font-bold text-gray-900 dark:text-white">새 A/B 테스트 생성</h2>
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">단계 {{ step }}/3</p>
        </div>
        <button
          @click="emit('close')"
          class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          aria-label="모달 닫기"
        >
          <XMarkIcon class="w-6 h-6 text-gray-500 dark:text-gray-400" />
        </button>
      </div>

      <!-- Step indicator -->
      <div class="flex items-center justify-center gap-2 p-4 bg-gray-50 dark:bg-gray-700/50">
        <div
          v-for="i in 3"
          :key="i"
          :class="[
            'h-2 rounded-full transition-all',
            i === step ? 'w-12 bg-blue-600' : i < step ? 'w-8 bg-blue-400' : 'w-8 bg-gray-300 dark:bg-gray-600'
          ]"
        ></div>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-6">
        <!-- Step 1: Select video & test type -->
        <div v-if="step === 1" class="space-y-6">
          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              테스트 이름
            </label>
            <input
              v-model="testName"
              type="text"
              placeholder="예: 썸네일 색상 테스트"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
            />
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              비디오 선택
            </label>
            <div class="space-y-2">
              <div
                v-for="video in videoOptions"
                :key="video.id"
                @click="selectVideo(video)"
                :class="[
                  'p-4 border rounded-lg cursor-pointer transition-all',
                  videoId === video.id
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                    : 'border-gray-300 dark:border-gray-600 hover:border-blue-300 dark:hover:border-blue-500'
                ]"
              >
                <div class="flex items-center justify-between">
                  <span class="text-sm font-medium text-gray-900 dark:text-white">{{ video.title }}</span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ video.id }}</span>
                </div>
              </div>
            </div>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              테스트 유형
            </label>
            <div class="grid grid-cols-3 gap-3">
              <button
                v-for="type in ['thumbnail', 'title', 'description'] as TestType[]"
                :key="type"
                @click="testType = type"
                :class="[
                  'p-4 border rounded-lg text-center transition-all',
                  testType === type
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400'
                    : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-blue-300 dark:hover:border-blue-500'
                ]"
              >
                <div class="font-medium">
                  {{ type === 'thumbnail' ? '썸네일' : type === 'title' ? '제목' : '설명' }}
                </div>
              </button>
            </div>
          </div>
        </div>

        <!-- Step 2: Create variants -->
        <div v-if="step === 2" class="space-y-6">
          <div class="flex items-center justify-between">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-white">
              {{ typeLabel }} 변형 만들기
            </h3>
            <button
              v-if="variants.length < 4"
              @click="addVariant"
              class="flex items-center gap-2 px-3 py-1.5 text-sm font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
            >
              <PlusIcon class="w-4 h-4" />
              변형 추가
            </button>
          </div>

          <div class="space-y-4">
            <div
              v-for="(variant, index) in variants"
              :key="variant.id"
              class="p-4 border border-gray-200 dark:border-gray-700 rounded-lg space-y-3"
            >
              <div class="flex items-center justify-between">
                <input
                  v-model="variant.label"
                  type="text"
                  placeholder="변형 이름"
                  class="flex-1 px-3 py-1.5 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
                />
                <button
                  v-if="variants.length > 2"
                  @click="removeVariant(index)"
                  class="ml-2 p-1.5 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors"
                >
                  <TrashIcon class="w-4 h-4" />
                </button>
              </div>

              <!-- Thumbnail input -->
              <div v-if="testType === 'thumbnail'" class="space-y-2">
                <input
                  v-model="variant.thumbnailUrl"
                  type="text"
                  placeholder="썸네일 이미지 URL"
                  class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
                />
                <div v-if="variant.thumbnailUrl" class="aspect-video rounded-lg overflow-hidden bg-gray-100 dark:bg-gray-700">
                  <img :src="variant.thumbnailUrl" :alt="variant.label" class="w-full h-full object-cover" />
                </div>
              </div>

              <!-- Text input -->
              <div v-else>
                <textarea
                  v-model="variant.content"
                  :placeholder="testType === 'title' ? '제목 입력...' : '설명 입력...'"
                  :rows="testType === 'title' ? 2 : 4"
                  class="w-full px-3 py-2 text-sm border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent resize-none"
                ></textarea>
                <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">
                  {{ variant.content.length }} 자
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Step 3: Configure settings -->
        <div v-if="step === 3" class="space-y-6">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white">테스트 설정</h3>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              테스트 기간 (시간)
            </label>
            <input
              v-model.number="duration"
              type="number"
              min="1"
              max="168"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
            />
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
              약 {{ Math.floor(duration / 24) }}일 {{ duration % 24 }}시간
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              샘플 크기
            </label>
            <input
              v-model.number="sampleSize"
              type="number"
              min="100"
              step="100"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
            />
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
              테스트에 참여할 사용자 수
            </p>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              신뢰 수준 (%)
            </label>
            <select
              v-model.number="confidence"
              class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 dark:focus:ring-blue-400 focus:border-transparent"
            >
              <option :value="90">90%</option>
              <option :value="95">95%</option>
              <option :value="99">99%</option>
            </select>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-1">
              통계적 유의성 수준
            </p>
          </div>

          <!-- Summary -->
          <div class="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg space-y-2">
            <h4 class="text-sm font-semibold text-blue-900 dark:text-blue-300">테스트 요약</h4>
            <div class="space-y-1 text-sm text-blue-800 dark:text-blue-400">
              <p><span class="font-medium">이름:</span> {{ testName }}</p>
              <p><span class="font-medium">비디오:</span> {{ videoTitle }}</p>
              <p><span class="font-medium">유형:</span> {{ typeLabel }}</p>
              <p><span class="font-medium">변형 수:</span> {{ variants.length }}개</p>
              <p><span class="font-medium">기간:</span> {{ duration }}시간</p>
              <p><span class="font-medium">샘플:</span> {{ sampleSize.toLocaleString() }}명</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-between p-6 border-t border-gray-200 dark:border-gray-700">
        <button
          v-if="step > 1"
          @click="prevStep"
          class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
        >
          이전
        </button>
        <div v-else></div>

        <div class="flex items-center gap-3">
          <button
            v-if="step === 3"
            @click="handleCreate(false)"
            class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          >
            초안으로 저장
          </button>
          <button
            v-if="step < 3"
            @click="nextStep"
            :disabled="(step === 1 && !canProceedStep1) || (step === 2 && !canProceedStep2)"
            :class="[
              'px-4 py-2 text-sm font-medium rounded-lg transition-colors',
              (step === 1 && !canProceedStep1) || (step === 2 && !canProceedStep2)
                ? 'bg-gray-300 dark:bg-gray-600 text-gray-500 dark:text-gray-400 cursor-not-allowed'
                : 'bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700 text-white'
            ]"
          >
            다음
          </button>
          <button
            v-else
            @click="handleCreate(true)"
            class="px-4 py-2 text-sm font-medium text-white bg-green-600 hover:bg-green-700 dark:bg-green-600 dark:hover:bg-green-700 rounded-lg transition-colors"
          >
            생성 및 시작
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
