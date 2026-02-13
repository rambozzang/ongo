<script setup lang="ts">
import { ref, computed } from 'vue'
import { XMarkIcon, ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { AutomationRule, TriggerType, ActionType, AutomationStatus } from '@/types/automation'

const props = defineProps<{
  isOpen: boolean
  rule?: AutomationRule
}>()

const emit = defineEmits<{
  close: []
  save: [rule: Omit<AutomationRule, 'id' | 'createdAt' | 'updatedAt' | 'executionCount' | 'lastExecutedAt'>]
}>()

const currentStep = ref(1)
const totalSteps = 4

// Form data
const formData = ref({
  name: '',
  description: '',
  triggerType: '' as TriggerType | '',
  triggerConfig: {} as Record<string, string | number | boolean>,
  actions: [] as Array<{ type: ActionType; config: Record<string, string | number | boolean> }>,
  status: 'active' as AutomationStatus,
  isEnabled: true
})

// Initialize form with rule data if editing
if (props.rule) {
  formData.value = {
    name: props.rule.name,
    description: props.rule.description,
    triggerType: props.rule.trigger.type,
    triggerConfig: { ...props.rule.trigger.config },
    actions: props.rule.actions.map(a => ({ type: a.type, config: { ...a.config } })),
    status: props.rule.status,
    isEnabled: props.rule.isEnabled
  }
}

const triggerTypes = [
  { value: 'video_published', label: '영상 게시됨', description: '특정 플랫폼에 영상이 게시될 때' },
  { value: 'views_threshold', label: '조회수 도달', description: '영상 조회수가 목표치에 도달할 때' },
  { value: 'schedule_time', label: '예약 시간', description: '특정 시간에 실행' },
  { value: 'comment_received', label: '댓글 수신', description: '특정 키워드를 포함한 댓글을 받을 때' },
  { value: 'subscriber_milestone', label: '구독자 달성', description: '채널 구독자가 목표에 도달할 때' }
]

const actionTypes = [
  { value: 'cross_post', label: '크로스 포스팅', description: '다른 플랫폼에 자동 게시' },
  { value: 'send_notification', label: '알림 전송', description: '이메일 또는 앱 알림 전송' },
  { value: 'add_tag', label: '태그 추가', description: '영상에 태그 추가' },
  { value: 'move_to_status', label: '상태 변경', description: '영상 상태 변경' },
  { value: 'generate_ai_metadata', label: 'AI 메타데이터 생성', description: 'AI로 제목/설명/태그 생성' }
]

const platforms = [
  { value: 'youtube', label: 'YouTube' },
  { value: 'tiktok', label: 'TikTok' },
  { value: 'instagram', label: 'Instagram' },
  { value: 'naver_clip', label: 'Naver Clip' }
]

const notificationChannels = [
  { value: 'email', label: '이메일' },
  { value: 'app', label: '앱 알림' },
  { value: 'both', label: '이메일 + 앱' }
]

const canProceed = computed(() => {
  if (currentStep.value === 1) {
    return formData.value.name.trim() !== '' && formData.value.description.trim() !== ''
  }
  if (currentStep.value === 2) {
    return formData.value.triggerType !== ''
  }
  if (currentStep.value === 3) {
    return formData.value.actions.length > 0
  }
  return true
})

const nextStep = () => {
  if (canProceed.value && currentStep.value < totalSteps) {
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

const addAction = (actionType: ActionType) => {
  formData.value.actions.push({
    type: actionType,
    config: {}
  })
}

const removeAction = (index: number) => {
  formData.value.actions.splice(index, 1)
}

const handleSave = () => {
  if (!canProceed.value) return

  const rule = {
    name: formData.value.name,
    description: formData.value.description,
    trigger: {
      type: formData.value.triggerType as TriggerType,
      config: formData.value.triggerConfig
    },
    actions: formData.value.actions,
    status: formData.value.status,
    isEnabled: formData.value.isEnabled
  }

  emit('save', rule)
  emit('close')
}

const close = () => {
  emit('close')
}
</script>

<template>
  <div
    v-if="isOpen"
    class="fixed inset-0 z-50 overflow-y-auto"
    aria-labelledby="automation-form-modal-title"
    role="dialog"
    aria-modal="true"
  >
    <div class="flex items-center justify-center min-h-screen px-4 pt-4 pb-20 text-center sm:block sm:p-0">
      <!-- Background overlay -->
      <div
        class="fixed inset-0 bg-gray-500 dark:bg-gray-900 bg-opacity-75 dark:bg-opacity-75 transition-opacity"
        aria-hidden="true"
        @click="close"
      ></div>

      <!-- Modal panel -->
      <div
        class="inline-block align-bottom bg-white dark:bg-gray-800 rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-3xl sm:w-full"
        @keydown.escape="close"
      >
        <!-- Header -->
        <div class="bg-white dark:bg-gray-800 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between">
            <h3 id="automation-form-modal-title" class="text-lg font-semibold text-gray-900 dark:text-white">
              {{ rule ? '규칙 수정' : '새 자동화 규칙' }}
            </h3>
            <button
              @click="close"
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              aria-label="모달 닫기"
            >
              <XMarkIcon class="w-6 h-6" />
            </button>
          </div>

          <!-- Step indicator -->
          <div class="mt-4 flex items-center justify-between">
            <div
              v-for="step in totalSteps"
              :key="step"
              class="flex-1"
            >
              <div class="flex items-center">
                <div
                  :class="[
                    'flex items-center justify-center w-10 h-10 rounded-full border-2 transition-colors',
                    step <= currentStep
                      ? 'border-blue-600 bg-blue-600 text-white'
                      : 'border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400'
                  ]"
                >
                  {{ step }}
                </div>
                <div
                  v-if="step < totalSteps"
                  :class="[
                    'flex-1 h-1 mx-2',
                    step < currentStep ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600'
                  ]"
                ></div>
              </div>
              <div class="mt-2 text-xs text-center text-gray-600 dark:text-gray-400">
                <span v-if="step === 1">기본 정보</span>
                <span v-if="step === 2">트리거</span>
                <span v-if="step === 3">액션</span>
                <span v-if="step === 4">검토</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Body -->
        <div class="bg-white dark:bg-gray-800 px-6 py-6 max-h-[60vh] overflow-y-auto">
          <!-- Step 1: Name & Description -->
          <div v-if="currentStep === 1" class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                규칙 이름
              </label>
              <input
                v-model="formData.name"
                type="text"
                placeholder="예: YouTube → TikTok 자동 게시"
                class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                설명
              </label>
              <textarea
                v-model="formData.description"
                rows="3"
                placeholder="규칙에 대한 간단한 설명을 입력하세요"
                class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
              ></textarea>
            </div>
          </div>

          <!-- Step 2: Trigger Selection -->
          <div v-if="currentStep === 2" class="space-y-4">
            <h4 class="text-md font-semibold text-gray-900 dark:text-white mb-4">
              트리거를 선택하세요
            </h4>

            <div class="grid grid-cols-1 gap-3">
              <button
                v-for="trigger in triggerTypes"
                :key="trigger.value"
                @click="formData.triggerType = trigger.value as TriggerType"
                :class="[
                  'p-4 rounded-lg border-2 text-left transition-all',
                  formData.triggerType === trigger.value
                    ? 'border-blue-600 bg-blue-50 dark:bg-blue-900/20'
                    : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                ]"
              >
                <div class="font-medium text-gray-900 dark:text-white">{{ trigger.label }}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ trigger.description }}</div>
              </button>
            </div>

            <!-- Trigger Configuration -->
            <div v-if="formData.triggerType" class="mt-6 p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg space-y-4">
              <h5 class="text-sm font-semibold text-gray-900 dark:text-white">트리거 설정</h5>

              <!-- video_published config -->
              <div v-if="formData.triggerType === 'video_published'">
                <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                  플랫폼 선택
                </label>
                <select
                  v-model="formData.triggerConfig.platform"
                  class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="">선택하세요</option>
                  <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                    {{ platform.label }}
                  </option>
                </select>
              </div>

              <!-- views_threshold config -->
              <div v-if="formData.triggerType === 'views_threshold'" class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    목표 조회수
                  </label>
                  <input
                    v-model.number="formData.triggerConfig.threshold"
                    type="number"
                    placeholder="10000"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>
              </div>

              <!-- schedule_time config -->
              <div v-if="formData.triggerType === 'schedule_time'" class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    반복 유형
                  </label>
                  <select
                    v-model="formData.triggerConfig.schedule"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="daily">매일</option>
                    <option value="weekly">매주</option>
                    <option value="monthly">매월</option>
                  </select>
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      시간
                    </label>
                    <input
                      v-model.number="formData.triggerConfig.hour"
                      type="number"
                      min="0"
                      max="23"
                      placeholder="9"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      분
                    </label>
                    <input
                      v-model.number="formData.triggerConfig.minute"
                      type="number"
                      min="0"
                      max="59"
                      placeholder="0"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                  </div>
                </div>
              </div>

              <!-- comment_received config -->
              <div v-if="formData.triggerType === 'comment_received'" class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    키워드 (쉼표로 구분)
                  </label>
                  <input
                    v-model="formData.triggerConfig.keywords"
                    type="text"
                    placeholder="협찬,광고,문의"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>
              </div>

              <!-- subscriber_milestone config -->
              <div v-if="formData.triggerType === 'subscriber_milestone'" class="space-y-3">
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    목표 구독자 수
                  </label>
                  <input
                    v-model.number="formData.triggerConfig.milestone"
                    type="number"
                    placeholder="10000"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          <!-- Step 3: Action Configuration -->
          <div v-if="currentStep === 3" class="space-y-4">
            <div class="flex items-center justify-between mb-4">
              <h4 class="text-md font-semibold text-gray-900 dark:text-white">
                액션 추가
              </h4>
            </div>

            <!-- Action Type Selection -->
            <div class="grid grid-cols-1 gap-3 mb-6">
              <button
                v-for="action in actionTypes"
                :key="action.value"
                @click="addAction(action.value as ActionType)"
                class="p-4 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 hover:border-blue-500 dark:hover:border-blue-500 text-left transition-all"
              >
                <div class="font-medium text-gray-900 dark:text-white">{{ action.label }}</div>
                <div class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ action.description }}</div>
              </button>
            </div>

            <!-- Added Actions -->
            <div v-if="formData.actions.length > 0" class="space-y-4">
              <h5 class="text-sm font-semibold text-gray-900 dark:text-white">추가된 액션</h5>

              <div
                v-for="(action, index) in formData.actions"
                :key="index"
                class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg space-y-3"
              >
                <div class="flex items-center justify-between">
                  <span class="font-medium text-gray-900 dark:text-white">
                    {{ actionTypes.find(a => a.value === action.type)?.label }}
                  </span>
                  <button
                    @click="removeAction(index)"
                    class="text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 text-sm"
                  >
                    삭제
                  </button>
                </div>

                <!-- cross_post config -->
                <div v-if="action.type === 'cross_post'" class="space-y-3">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      대상 플랫폼
                    </label>
                    <select
                      v-model="action.config.targetPlatform"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                      <option value="">선택하세요</option>
                      <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                        {{ platform.label }}
                      </option>
                    </select>
                  </div>
                  <label class="flex items-center">
                    <input
                      v-model="action.config.copyMetadata"
                      type="checkbox"
                      class="w-4 h-4 text-blue-600 bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-blue-500"
                    >
                    <span class="ml-2 text-sm text-gray-700 dark:text-gray-300">메타데이터 복사</span>
                  </label>
                </div>

                <!-- send_notification config -->
                <div v-if="action.type === 'send_notification'" class="space-y-3">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      알림 메시지
                    </label>
                    <input
                      v-model="action.config.message"
                      type="text"
                      placeholder="알림 메시지를 입력하세요"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      알림 채널
                    </label>
                    <select
                      v-model="action.config.channel"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                      <option value="">선택하세요</option>
                      <option v-for="channel in notificationChannels" :key="channel.value" :value="channel.value">
                        {{ channel.label }}
                      </option>
                    </select>
                  </div>
                </div>

                <!-- add_tag config -->
                <div v-if="action.type === 'add_tag'">
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    태그
                  </label>
                  <input
                    v-model="action.config.tag"
                    type="text"
                    placeholder="태그를 입력하세요"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                </div>

                <!-- move_to_status config -->
                <div v-if="action.type === 'move_to_status'">
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                    상태
                  </label>
                  <select
                    v-model="action.config.status"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option value="draft">임시저장</option>
                    <option value="published">게시됨</option>
                    <option value="review">검토 필요</option>
                    <option value="archived">보관됨</option>
                  </select>
                </div>

                <!-- generate_ai_metadata config -->
                <div v-if="action.type === 'generate_ai_metadata'" class="space-y-3">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                      언어
                    </label>
                    <select
                      v-model="action.config.language"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                      <option value="ko">한국어</option>
                      <option value="en">영어</option>
                    </select>
                  </div>
                  <label class="flex items-center">
                    <input
                      v-model="action.config.includeHashtags"
                      type="checkbox"
                      class="w-4 h-4 text-blue-600 bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-blue-500"
                    >
                    <span class="ml-2 text-sm text-gray-700 dark:text-gray-300">해시태그 포함</span>
                  </label>
                </div>
              </div>
            </div>
          </div>

          <!-- Step 4: Review -->
          <div v-if="currentStep === 4" class="space-y-6">
            <h4 class="text-md font-semibold text-gray-900 dark:text-white mb-4">
              규칙 검토
            </h4>

            <div class="space-y-4">
              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">규칙 이름</div>
                <div class="text-base text-gray-900 dark:text-white">{{ formData.name }}</div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">설명</div>
                <div class="text-base text-gray-900 dark:text-white">{{ formData.description }}</div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">트리거</div>
                <div class="text-base text-gray-900 dark:text-white">
                  {{ triggerTypes.find(t => t.value === formData.triggerType)?.label }}
                </div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">액션</div>
                <div class="space-y-2">
                  <div
                    v-for="(action, index) in formData.actions"
                    :key="index"
                    class="text-base text-gray-900 dark:text-white"
                  >
                    {{ index + 1 }}. {{ actionTypes.find(a => a.value === action.type)?.label }}
                  </div>
                </div>
              </div>

              <div class="flex items-center p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
                <input
                  v-model="formData.isEnabled"
                  type="checkbox"
                  class="w-4 h-4 text-blue-600 bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-blue-500"
                >
                <span class="ml-3 text-sm font-medium text-gray-900 dark:text-white">생성 후 즉시 활성화</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="bg-gray-50 dark:bg-gray-900/50 px-6 py-4 flex items-center justify-between border-t border-gray-200 dark:border-gray-700">
          <button
            v-if="currentStep > 1"
            @click="prevStep"
            class="inline-flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            <ChevronLeftIcon class="w-5 h-5" />
            이전
          </button>
          <div v-else></div>

          <div class="flex items-center gap-3">
            <button
              @click="close"
              class="px-4 py-2 text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white transition-colors"
            >
              취소
            </button>

            <button
              v-if="currentStep < totalSteps"
              @click="nextStep"
              :disabled="!canProceed"
              class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              다음
              <ChevronRightIcon class="w-5 h-5" />
            </button>

            <button
              v-else
              @click="handleSave"
              class="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              저장
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
