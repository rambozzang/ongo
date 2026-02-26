<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  XMarkIcon,
  SparklesIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import type { ScriptFormat, ScriptTone, GenerateScriptRequest } from '@/types/scriptWriter'

interface Props {
  visible: boolean
  isGenerating: boolean
}

interface Emits {
  (e: 'close'): void
  (e: 'generate', request: GenerateScriptRequest): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const topic = ref('')
const format = ref<ScriptFormat>('LONG_FORM')
const tone = ref<ScriptTone>('CASUAL')
const targetDuration = ref(300)
const keywordInput = ref('')
const keywords = ref<string[]>([])
const targetAudience = ref('')
const additionalNotes = ref('')

const formatOptions: { value: ScriptFormat; label: string }[] = [
  { value: 'LONG_FORM', label: '롱폼' },
  { value: 'SHORT_FORM', label: '숏폼' },
  { value: 'TUTORIAL', label: '튜토리얼' },
  { value: 'REVIEW', label: '리뷰' },
  { value: 'VLOG', label: '브이로그' },
  { value: 'INTERVIEW', label: '인터뷰' },
]

const toneOptions: { value: ScriptTone; label: string }[] = [
  { value: 'CASUAL', label: '캐주얼' },
  { value: 'PROFESSIONAL', label: '전문적' },
  { value: 'HUMOROUS', label: '유머' },
  { value: 'EDUCATIONAL', label: '교육적' },
  { value: 'DRAMATIC', label: '드라마틱' },
  { value: 'STORYTELLING', label: '스토리텔링' },
]

const durationLabel = computed(() => {
  const min = Math.floor(targetDuration.value / 60)
  const sec = targetDuration.value % 60
  return sec > 0 ? `${min}분 ${sec}초` : `${min}분`
})

const isValid = computed(() => topic.value.trim().length > 0)

function addKeyword() {
  const kw = keywordInput.value.trim()
  if (kw && !keywords.value.includes(kw)) {
    keywords.value.push(kw)
  }
  keywordInput.value = ''
}

function removeKeyword(kw: string) {
  keywords.value = keywords.value.filter((k) => k !== kw)
}

function handleGenerate() {
  if (!isValid.value) return
  const request: GenerateScriptRequest = {
    topic: topic.value.trim(),
    format: format.value,
    tone: tone.value,
    targetDuration: targetDuration.value,
    keywords: keywords.value.length > 0 ? keywords.value : undefined,
    targetAudience: targetAudience.value.trim() || undefined,
    additionalNotes: additionalNotes.value.trim() || undefined,
  }
  emit('generate', request)
}

function resetForm() {
  topic.value = ''
  format.value = 'LONG_FORM'
  tone.value = 'CASUAL'
  targetDuration.value = 300
  keywordInput.value = ''
  keywords.value = []
  targetAudience.value = ''
  additionalNotes.value = ''
}

function handleClose() {
  resetForm()
  emit('close')
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
        v-if="visible"
        class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        @click="handleClose"
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
            v-if="visible"
            class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
            @click.stop
          >
            <!-- Modal Header -->
            <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <SparklesIcon class="w-5 h-5 text-primary-600 dark:text-primary-400" />
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  AI 스크립트 생성
                </h2>
              </div>
              <button
                @click="handleClose"
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                <XMarkIcon class="w-5 h-5" />
              </button>
            </div>

            <!-- Modal Body -->
            <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
              <!-- Topic -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  주제 <span class="text-red-500">*</span>
                </label>
                <input
                  v-model="topic"
                  type="text"
                  placeholder="스크립트 주제를 입력하세요 (예: 유튜브 성장 비법)"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                />
              </div>

              <!-- Format -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  형식
                </label>
                <select
                  v-model="format"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                >
                  <option
                    v-for="opt in formatOptions"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <!-- Tone -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  톤
                </label>
                <select
                  v-model="tone"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                >
                  <option
                    v-for="opt in toneOptions"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <!-- Target Duration Slider -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  목표 길이
                  <span class="text-primary-600 dark:text-primary-400 font-semibold ml-2">{{ durationLabel }}</span>
                </label>
                <input
                  v-model.number="targetDuration"
                  type="range"
                  min="30"
                  max="1800"
                  step="30"
                  class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-lg appearance-none cursor-pointer accent-primary-600"
                />
                <div class="flex justify-between text-xs text-gray-400 dark:text-gray-500 mt-1">
                  <span>30초</span>
                  <span>30분</span>
                </div>
              </div>

              <!-- Keywords -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  키워드
                </label>
                <div class="flex items-center gap-2">
                  <div class="relative flex-1">
                    <TagIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <input
                      v-model="keywordInput"
                      type="text"
                      placeholder="키워드 입력 후 Enter"
                      class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 pl-9 pr-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                      @keydown.enter.prevent="addKeyword"
                    />
                  </div>
                  <button
                    type="button"
                    @click="addKeyword"
                    class="px-3 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                  >
                    추가
                  </button>
                </div>
                <div v-if="keywords.length > 0" class="flex flex-wrap gap-1.5 mt-2">
                  <span
                    v-for="kw in keywords"
                    :key="kw"
                    class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                  >
                    #{{ kw }}
                    <button @click="removeKeyword(kw)" class="hover:text-primary-900 dark:hover:text-primary-100">
                      <XMarkIcon class="w-3 h-3" />
                    </button>
                  </span>
                </div>
              </div>

              <!-- Target Audience -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  대상 시청자
                </label>
                <input
                  v-model="targetAudience"
                  type="text"
                  placeholder="예: 초보 유튜버, 20-30대 직장인"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                />
              </div>

              <!-- Additional Notes -->
              <div>
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                  추가 요청사항
                </label>
                <textarea
                  v-model="additionalNotes"
                  rows="2"
                  placeholder="AI에게 전달할 추가 지시사항을 입력하세요"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                />
              </div>
            </div>

            <!-- Modal Footer -->
            <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
              <button
                @click="handleClose"
                class="px-4 py-2 text-sm font-medium rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
              >
                취소
              </button>
              <button
                @click="handleGenerate"
                :disabled="!isValid || isGenerating"
                class="btn-primary inline-flex items-center gap-2"
              >
                <SparklesIcon v-if="!isGenerating" class="w-4 h-4" />
                <div v-else class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                {{ isGenerating ? '생성 중...' : 'AI 스크립트 생성' }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
