<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { XMarkIcon, ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import type { AutomationRule, TriggerType, ActionType, AutomationStatus } from '@/types/automation'

export interface AutomationInitialTrigger {
  triggerType: string
  config: Record<string, unknown>
  name?: string
  description?: string
}

const props = defineProps<{
  isOpen: boolean
  rule?: AutomationRule
  initialTrigger?: AutomationInitialTrigger
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
  triggerConfig: {} as AutomationRule['trigger']['config'],
  actions: [] as AutomationRule['actions'],
  status: 'active' as AutomationStatus,
  isEnabled: true
})
const milestoneText = ref('1000, 5000, 10000, 100000')

const resetForm = () => {
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
  } else {
    formData.value = {
      name: props.initialTrigger?.name ?? '',
      description: props.initialTrigger?.description ?? '',
      triggerType: (props.initialTrigger?.triggerType ?? '') as TriggerType | '',
      triggerConfig: (props.initialTrigger?.config ?? {}) as AutomationRule['trigger']['config'],
      actions: [],
      status: 'active',
      isEnabled: true
    }
  }
  const milestones = formData.value.triggerConfig.milestones
  milestoneText.value = Array.isArray(milestones) ? milestones.join(', ') : '1000, 5000, 10000, 100000'
  currentStep.value = 1
}

watch(
  () => [props.isOpen, props.rule, props.initialTrigger] as const,
  ([isOpen]) => {
    if (isOpen) resetForm()
  },
  { immediate: true },
)

const triggerTypes = [
  { value: 'VIDEO_UPLOADED', label: '영상 업로드됨', description: '영상 업로드가 완료될 때' },
  { value: 'SCHEDULE_DUE', label: '예약 시간', description: '예약 게시 시간이 되면 실행' },
  { value: 'COMMENT_RECEIVED', label: '댓글 수신', description: '특정 키워드를 포함한 댓글을 받을 때' },
  { value: 'ANALYTICS_MILESTONE', label: '분석 마일스톤', description: '분석 지표가 목표에 도달할 때' },
  { value: 'CREDIT_LOW', label: 'AI 크레딧 부족', description: 'AI 크레딧이 설정한 기준보다 낮아질 때' },
  { value: 'VIEWS_MILESTONE', label: '조회수 마일스톤', description: '조회수가 설정한 목표에 도달할 때' },
  { value: 'VIRAL_DETECTED', label: '바이럴 감지', description: '평균 대비 급격한 조회수 상승을 감지할 때' },
  { value: 'ENGAGEMENT_DROP', label: '참여율 하락', description: '참여율이 평균 대비 크게 하락할 때' },
]

const actionTypes = [
  { value: 'SEND_NOTIFICATION', label: '알림 전송', description: '이메일 또는 앱 알림 전송' },
  { value: 'AUTO_PUBLISH', label: '자동 게시', description: '조건 충족 시 게시 큐에 등록' },
  { value: 'ADD_TAG', label: '태그 추가', description: '영상에 태그 추가' },
  { value: 'GENERATE_METADATA', label: 'AI 메타데이터 생성', description: 'AI로 제목·설명·태그 생성' }
]

const platforms = [
  { value: 'youtube', label: 'YouTube' },
  { value: 'tiktok', label: 'TikTok' },
  { value: 'instagram', label: 'Instagram' },
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
  // The current server contract stores one action per rule. Do not silently drop extras.
  if (formData.value.actions.length > 0) return
  formData.value.actions.push({
    type: actionType,
    config: {}
  })
}

const syncMilestones = () => {
  const milestones = milestoneText.value
    .split(',')
    .map(value => Number(value.trim()))
    .filter(value => Number.isFinite(value) && value > 0)
  formData.value.triggerConfig = {
    ...formData.value.triggerConfig,
    milestones,
  }
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
            <h3 id="automation-form-modal-title" class="text-title font-semibold text-gray-900 dark:text-white">
              {{ rule ? '규칙 수정' : '새 자동화 규칙' }}
            </h3>
            <button
              class="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
              aria-label="모달 닫기"
              @click="close"
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
                      ? 'border-primary-600 bg-primary-600 text-white'
                      : 'border-gray-300 dark:border-gray-600 text-gray-500 dark:text-gray-400'
                  ]"
                >
                  {{ step }}
                </div>
                <div
                  v-if="step < totalSteps"
                  :class="[
                    'flex-1 h-1 mx-2',
                    step < currentStep ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'
                  ]"
                ></div>
              </div>
              <div class="mt-2 text-body-xs text-center text-gray-600 dark:text-gray-400">
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
              <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                규칙 이름
              </label>
              <input
                v-model="formData.name"
                type="text"
                placeholder="예: YouTube → TikTok 자동 게시"
                class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
            </div>

            <div>
              <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                설명
              </label>
              <textarea
                v-model="formData.description"
                rows="3"
                placeholder="규칙에 대한 간단한 설명을 입력하세요"
                class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
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
                :class="[
                  'p-4 rounded-lg border-2 text-left transition-all',
                  formData.triggerType === trigger.value
                    ? 'border-primary-600 bg-primary-50 dark:bg-primary-900/20'
                    : 'border-gray-300 dark:border-gray-600 hover:border-gray-400 dark:hover:border-gray-500'
                ]"
                @click="formData.triggerType = trigger.value as TriggerType"
              >
                <div class="font-medium text-gray-900 dark:text-white">{{ trigger.label }}</div>
                <div class="text-body text-gray-600 dark:text-gray-400 mt-1">{{ trigger.description }}</div>
              </button>
            </div>

            <!-- Trigger Configuration -->
            <div v-if="formData.triggerType" class="mt-6 p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg space-y-4">
              <h5 class="text-body font-semibold text-gray-900 dark:text-white">트리거 설정</h5>

              <!-- video upload config -->
              <div v-if="formData.triggerType === 'VIDEO_UPLOADED'">
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  플랫폼 선택
                </label>
                <select
                  v-model="formData.triggerConfig.platform"
                  class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                >
                  <option value="">선택하세요</option>
                  <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                    {{ platform.label }}
                  </option>
                </select>
              </div>

              <!-- analytics milestone config -->
              <div v-if="formData.triggerType === 'ANALYTICS_MILESTONE'" class="space-y-3">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    목표 조회수
                  </label>
                  <input
                    v-model.number="formData.triggerConfig.threshold"
                    type="number"
                    placeholder="10000"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>
              </div>

              <div v-if="formData.triggerType === 'VIEWS_MILESTONE'" class="space-y-3">
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">조회수 목표 (쉼표로 구분)</label>
                <input v-model="milestoneText" type="text" placeholder="1000, 5000, 10000" @blur="syncMilestones" class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent">
                <p class="text-body-xs text-gray-500 dark:text-gray-400">목표 중 하나에 도달하면 규칙이 한 번 실행됩니다.</p>
              </div>

              <!-- schedule due config -->
              <div v-if="formData.triggerType === 'SCHEDULE_DUE'" class="space-y-3">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    반복 유형
                  </label>
                  <select
                    v-model="formData.triggerConfig.schedule"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="daily">매일</option>
                    <option value="weekly">매주</option>
                    <option value="monthly">매월</option>
                  </select>
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      시간
                    </label>
                    <input
                      v-model.number="formData.triggerConfig.hour"
                      type="number"
                      min="0"
                      max="23"
                      placeholder="9"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    >
                  </div>
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      분
                    </label>
                    <input
                      v-model.number="formData.triggerConfig.minute"
                      type="number"
                      min="0"
                      max="59"
                      placeholder="0"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    >
                  </div>
                </div>
              </div>

              <!-- comment_received config -->
              <div v-if="formData.triggerType === 'COMMENT_RECEIVED'" class="space-y-3">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    키워드 (쉼표로 구분)
                  </label>
                  <input
                    v-model="formData.triggerConfig.keywords"
                    type="text"
                    placeholder="협찬,광고,문의"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="">선택하세요</option>
                    <option v-for="platform in platforms" :key="platform.value" :value="platform.value">
                      {{ platform.label }}
                    </option>
                  </select>
                </div>
              </div>

              <!-- credit threshold config -->
              <div v-if="formData.triggerType === 'CREDIT_LOW'" class="space-y-3">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    남은 크레딧 기준
                  </label>
                  <input
                    v-model.number="formData.triggerConfig.milestone"
                    type="number"
                    placeholder="20"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                </div>
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    플랫폼
                  </label>
                  <select
                    v-model="formData.triggerConfig.platform"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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
              <span class="text-body-xs text-gray-500 dark:text-gray-400">규칙당 액션 1개</span>
            </div>

            <!-- Action Type Selection -->
            <div class="grid grid-cols-1 gap-3 mb-6">
              <button
                v-for="action in actionTypes"
                :key="action.value"
                :disabled="formData.actions.length > 0"
                class="p-4 rounded-lg border-2 border-dashed border-gray-300 dark:border-gray-600 hover:border-primary-500 dark:hover:border-primary-500 text-left transition-all disabled:cursor-not-allowed disabled:opacity-50"
                @click="addAction(action.value as ActionType)"
              >
                <div class="font-medium text-gray-900 dark:text-white">{{ action.label }}</div>
                <div class="text-body text-gray-600 dark:text-gray-400 mt-1">{{ action.description }}</div>
              </button>
            </div>

            <!-- Added Actions -->
            <div v-if="formData.actions.length > 0" class="space-y-4">
              <h5 class="text-body font-semibold text-gray-900 dark:text-white">추가된 액션</h5>

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
                    class="text-error-strong hover:text-error-strong text-body"
                    @click="removeAction(index)"
                  >
                    삭제
                  </button>
                </div>

                <!-- cross_post config -->
                <div v-if="action.type === 'AUTO_PUBLISH'" class="space-y-3">
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      대상 플랫폼
                    </label>
                    <select
                      v-model="action.config.targetPlatform"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
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
                      class="w-4 h-4 text-info-strong bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-primary-500"
                    >
                    <span class="ml-2 text-body text-gray-700 dark:text-gray-300">메타데이터 복사</span>
                  </label>
                </div>

                <!-- send_notification config -->
                <div v-if="action.type === 'SEND_NOTIFICATION'" class="space-y-3">
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      알림 메시지
                    </label>
                    <input
                      v-model="action.config.message"
                      type="text"
                      placeholder="알림 메시지를 입력하세요"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    >
                  </div>
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      알림 채널
                    </label>
                    <select
                      v-model="action.config.channel"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    >
                      <option value="">선택하세요</option>
                      <option v-for="channel in notificationChannels" :key="channel.value" :value="channel.value">
                        {{ channel.label }}
                      </option>
                    </select>
                  </div>
                </div>

                <!-- add_tag config -->
                <div v-if="action.type === 'ADD_TAG'">
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    태그
                  </label>
                  <input
                    v-model="action.config.tag"
                    type="text"
                    placeholder="태그를 입력하세요"
                    class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                </div>

                <!-- generate_ai_metadata config -->
                <div v-if="action.type === 'GENERATE_METADATA'" class="space-y-3">
                  <div>
                    <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                      언어
                    </label>
                    <select
                      v-model="action.config.language"
                      class="w-full px-4 py-2 bg-white dark:bg-gray-900 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                    >
                      <option value="ko">한국어</option>
                      <option value="en">영어</option>
                    </select>
                  </div>
                  <label class="flex items-center">
                    <input
                      v-model="action.config.includeHashtags"
                      type="checkbox"
                      class="w-4 h-4 text-info-strong bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-primary-500"
                    >
                    <span class="ml-2 text-body text-gray-700 dark:text-gray-300">해시태그 포함</span>
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
                <div class="text-body font-medium text-gray-500 dark:text-gray-400 mb-1">규칙 이름</div>
                <div class="text-body-lg text-gray-900 dark:text-white">{{ formData.name }}</div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-body font-medium text-gray-500 dark:text-gray-400 mb-1">설명</div>
                <div class="text-body-lg text-gray-900 dark:text-white">{{ formData.description }}</div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-body font-medium text-gray-500 dark:text-gray-400 mb-1">트리거</div>
                <div class="text-body-lg text-gray-900 dark:text-white">
                  {{ triggerTypes.find(t => t.value === formData.triggerType)?.label }}
                </div>
              </div>

              <div class="p-4 bg-gray-50 dark:bg-gray-900/50 rounded-lg">
                <div class="text-body font-medium text-gray-500 dark:text-gray-400 mb-2">액션</div>
                <div class="space-y-2">
                  <div
                    v-for="(action, index) in formData.actions"
                    :key="index"
                    class="text-body-lg text-gray-900 dark:text-white"
                  >
                    {{ index + 1 }}. {{ actionTypes.find(a => a.value === action.type)?.label }}
                  </div>
                </div>
              </div>

              <div class="flex items-center p-4 bg-info-subtle rounded-lg">
                <input
                  v-model="formData.isEnabled"
                  type="checkbox"
                  class="w-4 h-4 text-info-strong bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600 rounded focus:ring-primary-500"
                >
                <span class="ml-3 text-body font-medium text-gray-900 dark:text-white">생성 후 즉시 활성화</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="bg-gray-50 dark:bg-gray-900/50 px-6 py-4 flex items-center justify-between border-t border-gray-200 dark:border-gray-700">
          <button
            v-if="currentStep > 1"
            class="inline-flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            @click="prevStep"
          >
            <ChevronLeftIcon class="w-5 h-5" />
            이전
          </button>
          <div v-else></div>

          <div class="flex items-center gap-3">
            <button
              class="px-4 py-2 text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white transition-colors"
              @click="close"
            >
              취소
            </button>

            <button
              v-if="currentStep < totalSteps"
              :disabled="!canProceed"
              class="inline-flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              @click="nextStep"
            >
              다음
              <ChevronRightIcon class="w-5 h-5" />
            </button>

            <button
              v-else
              class="px-6 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
              @click="handleSave"
            >
              저장
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
