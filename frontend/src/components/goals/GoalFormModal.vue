<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { XMarkIcon, PlusIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { Goal, GoalType, GoalPeriod } from '@/types/goal'
import { useNotification } from '@/composables/useNotification'
import { useLocale } from '@/composables/useLocale'

interface Props {
  show: boolean
  goal?: Goal
}

interface Emits {
  (e: 'close'): void
  (e: 'submit', goal: Omit<Goal, 'id' | 'createdAt' | 'completedAt'>): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const notify = useNotification()
const { t } = useLocale()

const isEditMode = computed(() => !!props.goal)

const formData = ref({
  title: '',
  description: '',
  type: 'subscribers' as GoalType,
  period: 'monthly' as GoalPeriod,
  targetValue: 0,
  currentValue: 0,
  unit: '명',
  startDate: '',
  endDate: '',
  status: 'active' as Goal['status'],
})

const milestones = ref<Array<{ title: string; targetValue: number }>>([])

const typePresets = [
  { value: 'subscribers', label: '구독자 목표', unit: '명', icon: '👥' },
  { value: 'views', label: '조회수 목표', unit: '회', icon: '👁️' },
  { value: 'uploads', label: '업로드 목표', unit: '개', icon: '☁️' },
  { value: 'revenue', label: '수익 목표', unit: '원', icon: '💰' },
  { value: 'engagement', label: '참여율 목표', unit: '%', icon: '❤️' },
  { value: 'custom', label: '사용자 정의', unit: '', icon: '✨' },
]

const periodOptions = [
  { value: 'weekly', label: '주간' },
  { value: 'monthly', label: '월간' },
  { value: 'quarterly', label: '분기' },
  { value: 'yearly', label: '연간' },
  { value: 'custom', label: '사용자 정의' },
]

watch(() => props.show, (show) => {
  if (show) {
    if (props.goal) {
      formData.value = {
        title: props.goal.title,
        description: props.goal.description,
        type: props.goal.type,
        period: props.goal.period,
        targetValue: props.goal.targetValue,
        currentValue: props.goal.currentValue,
        unit: props.goal.unit,
        startDate: props.goal.startDate,
        endDate: props.goal.endDate,
        status: props.goal.status,
      }
      milestones.value = props.goal.milestones.map(m => ({
        title: m.title,
        targetValue: m.targetValue,
      }))
    } else {
      resetForm()
    }
  }
})

watch(() => formData.value.type, (newType) => {
  const preset = typePresets.find(p => p.value === newType)
  if (preset && preset.unit) {
    formData.value.unit = preset.unit
  }
})

const resetForm = () => {
  const today = new Date().toISOString().split('T')[0]
  const nextMonth = new Date()
  nextMonth.setMonth(nextMonth.getMonth() + 1)
  const endDate = nextMonth.toISOString().split('T')[0]

  formData.value = {
    title: '',
    description: '',
    type: 'subscribers',
    period: 'monthly',
    targetValue: 0,
    currentValue: 0,
    unit: '명',
    startDate: today,
    endDate: endDate,
    status: 'active',
  }
  milestones.value = []
}

const addMilestone = () => {
  milestones.value.push({
    title: '',
    targetValue: 0,
  })
}

const removeMilestone = (index: number) => {
  milestones.value.splice(index, 1)
}

const handleSubmit = () => {
  if (!validateForm()) return

  const submitData: Omit<Goal, 'id' | 'createdAt' | 'completedAt'> = {
    ...formData.value,
    milestones: milestones.value
      .filter(m => m.title.trim() && m.targetValue > 0)
      .map((m, index) => ({
        id: props.goal ? props.goal.milestones[index]?.id || Date.now() + index : Date.now() + index,
        title: m.title,
        targetValue: m.targetValue,
        isCompleted: props.goal?.milestones[index]?.isCompleted || false,
        completedAt: props.goal?.milestones[index]?.completedAt || null,
      })),
  }

  emit('submit', submitData)
  emit('close')
}

const validateForm = (): boolean => {
  if (!formData.value.title.trim()) {
    notify.error(t('goals.form.titleRequired'))
    return false
  }
  if (formData.value.targetValue <= 0) {
    notify.error(t('goals.form.targetValueRequired'))
    return false
  }
  if (!formData.value.startDate || !formData.value.endDate) {
    notify.error(t('goals.form.dateRangeRequired'))
    return false
  }
  if (new Date(formData.value.endDate) <= new Date(formData.value.startDate)) {
    notify.error(t('goals.form.endDateAfterStartDate'))
    return false
  }
  return true
}

const handleBackdropClick = (e: MouseEvent) => {
  if (e.target === e.currentTarget) {
    emit('close')
  }
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
        v-if="show"
        class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="goal-form-modal-title"
        @click="handleBackdropClick"
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
            v-if="show"
            class="bg-white dark:bg-gray-800 rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] overflow-hidden flex flex-col"
            @click.stop
            @keydown.escape="emit('close')"
          >
            <!-- Header -->
            <div class="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
              <h2 id="goal-form-modal-title" class="text-h2 font-semibold text-gray-900 dark:text-gray-100">
                {{ isEditMode ? '목표 수정' : '새 목표 만들기' }}
              </h2>
              <button
                class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                aria-label="모달 닫기"
                @click="emit('close')"
              >
                <XMarkIcon class="w-5 h-5" />
              </button>
            </div>

            <!-- Body -->
            <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
              <!-- Goal Type -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  목표 유형
                </label>
                <div class="grid grid-cols-3 gap-2">
                  <button
                    v-for="preset in typePresets"
                    :key="preset.value"
                    type="button"
                    :class="[
                      'px-3 py-2 text-body font-medium rounded-lg border-2 transition-all',
                      formData.type === preset.value
                        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                        : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500'
                    ]"
                    @click="formData.type = preset.value as GoalType"
                  >
                    <span class="mr-1">{{ preset.icon }}</span>
                    {{ preset.label }}
                  </button>
                </div>
              </div>

              <!-- Title -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  목표 제목
                </label>
                <input
                  v-model="formData.title"
                  type="text"
                  placeholder="예: 구독자 10만명 달성"
                  class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>

              <!-- Description -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  설명
                </label>
                <textarea
                  v-model="formData.description"
                  rows="3"
                  placeholder="목표에 대한 설명을 입력하세요"
                  class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                ></textarea>
              </div>

              <!-- Period -->
              <div>
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  기간
                </label>
                <div class="grid grid-cols-5 gap-2">
                  <button
                    v-for="period in periodOptions"
                    :key="period.value"
                    type="button"
                    :class="[
                      'px-3 py-2 text-body font-medium rounded-lg border-2 transition-all',
                      formData.period === period.value
                        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300'
                        : 'border-gray-300 dark:border-gray-600 text-gray-700 dark:text-gray-300 hover:border-gray-400 dark:hover:border-gray-500'
                    ]"
                    @click="formData.period = period.value as GoalPeriod"
                  >
                    {{ period.label }}
                  </button>
                </div>
              </div>

              <!-- Date Range -->
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    시작일
                  </label>
                  <input
                    v-model="formData.startDate"
                    type="date"
                    class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
                  />
                </div>
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    종료일
                  </label>
                  <input
                    v-model="formData.endDate"
                    type="date"
                    class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
                  />
                </div>
              </div>

              <!-- Target Values -->
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    현재 값
                  </label>
                  <div class="flex items-center gap-2">
                    <input
                      v-model.number="formData.currentValue"
                      type="number"
                      min="0"
                      placeholder="0"
                      class="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                    <span class="text-body text-gray-600 dark:text-gray-400">{{ formData.unit }}</span>
                  </div>
                </div>
                <div>
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                    목표 값
                  </label>
                  <div class="flex items-center gap-2">
                    <input
                      v-model.number="formData.targetValue"
                      type="number"
                      min="1"
                      placeholder="0"
                      class="flex-1 px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                    />
                    <span class="text-body text-gray-600 dark:text-gray-400">{{ formData.unit }}</span>
                  </div>
                </div>
              </div>

              <!-- Unit (if custom type) -->
              <div v-if="formData.type === 'custom'">
                <label class="block text-body font-medium text-gray-700 dark:text-gray-300 mb-2">
                  단위
                </label>
                <input
                  v-model="formData.unit"
                  type="text"
                  placeholder="예: 개, 회, 명"
                  class="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>

              <!-- Milestones -->
              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="block text-body font-medium text-gray-700 dark:text-gray-300">
                    마일스톤 (선택사항)
                  </label>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 px-3 py-1 text-body font-medium text-info-strong hover:bg-info-subtle rounded-lg transition-colors"
                    @click="addMilestone"
                  >
                    <PlusIcon class="w-4 h-4" />
                    추가
                  </button>
                </div>

                <div v-if="milestones.length > 0" class="space-y-2">
                  <div
                    v-for="(milestone, index) in milestones"
                    :key="index"
                    class="flex items-center gap-2 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg"
                  >
                    <div class="flex-1 grid grid-cols-2 gap-2">
                      <input
                        v-model="milestone.title"
                        type="text"
                        placeholder="마일스톤 제목"
                        class="px-3 py-1.5 text-body border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                      />
                      <div class="flex items-center gap-2">
                        <input
                          v-model.number="milestone.targetValue"
                          type="number"
                          min="0"
                          placeholder="목표 값"
                          class="flex-1 px-3 py-1.5 text-body border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500"
                        />
                        <span class="text-body-xs text-gray-600 dark:text-gray-400">{{ formData.unit }}</span>
                      </div>
                    </div>
                    <button
                      type="button"
                      class="p-1.5 text-error-strong hover:bg-error-subtle rounded-lg transition-colors"
                      @click="removeMilestone(index)"
                    >
                      <TrashIcon class="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <p v-else class="text-body text-gray-500 dark:text-gray-400 text-center py-4 border border-dashed border-gray-300 dark:border-gray-600 rounded-lg">
                  마일스톤을 추가하여 목표를 단계별로 추적하세요
                </p>
              </div>
            </div>

            <!-- Footer -->
            <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700">
              <button
                class="px-4 py-2 text-body font-medium text-gray-700 dark:text-gray-300 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-lg transition-colors"
                @click="emit('close')"
              >
                취소
              </button>
              <button
                class="px-4 py-2 text-body font-medium text-white bg-primary-600 hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600 rounded-lg transition-colors"
                @click="handleSubmit"
              >
                {{ isEditMode ? '수정' : '생성' }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
