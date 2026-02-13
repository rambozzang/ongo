<script setup lang="ts">
import { ref, computed } from 'vue'
import { PIPELINE_STEPS, type PipelineStepType, type PipelineStepInfo } from '@/types/ai'

const props = defineProps<{
  videoId: number
  channelId?: number
  creditBalance: number
}>()

const emit = defineEmits<{
  start: [steps: PipelineStepType[], channelId?: number]
}>()

const selectedSteps = ref<PipelineStepType[]>([
  'STT',
  'ANALYZE_SCRIPT',
  'GENERATE_META',
  'GENERATE_HASHTAGS',
  'SUGGEST_SCHEDULE',
])

const dragIndex = ref<number | null>(null)

const orderedSteps = computed(() => {
  return selectedSteps.value
    .map((key) => PIPELINE_STEPS.find((s) => s.key === key)!)
    .filter(Boolean)
})

const rawCost = computed(() => {
  return orderedSteps.value.reduce((sum, s) => sum + s.creditCost, 0)
})

const discountApplied = computed(() => selectedSteps.value.length >= 3)

const totalCost = computed(() => {
  if (discountApplied.value) {
    return Math.floor(rawCost.value * 0.8)
  }
  return rawCost.value
})

const discountAmount = computed(() => rawCost.value - totalCost.value)

const hasEnoughCredits = computed(() => props.creditBalance >= totalCost.value)

function isSelected(key: PipelineStepType) {
  return selectedSteps.value.includes(key)
}

function toggleStep(key: PipelineStepType) {
  const idx = selectedSteps.value.indexOf(key)
  if (idx >= 0) {
    selectedSteps.value.splice(idx, 1)
  } else {
    selectedSteps.value.push(key)
  }
}

function handleDragStart(index: number) {
  dragIndex.value = index
}

function handleDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (dragIndex.value === null || dragIndex.value === index) return
  const items = [...selectedSteps.value]
  const [moved] = items.splice(dragIndex.value, 1)
  items.splice(index, 0, moved)
  selectedSteps.value = items
  dragIndex.value = index
}

function handleDragEnd() {
  dragIndex.value = null
}

function handleStart() {
  if (selectedSteps.value.length === 0 || !hasEnoughCredits.value) return
  emit('start', [...selectedSteps.value], props.channelId)
}
</script>

<template>
  <div class="space-y-6">
    <!-- Step Selection -->
    <div>
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">
        AI 파이프라인 스텝 선택
      </h3>
      <div class="space-y-2">
        <label
          v-for="step in PIPELINE_STEPS"
          :key="step.key"
          class="flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all"
          :class="
            isSelected(step.key)
              ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 dark:border-primary-600'
              : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
          "
        >
          <input
            type="checkbox"
            :checked="isSelected(step.key)"
            class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
            @change="toggleStep(step.key)"
          />
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                {{ step.displayName }}
              </span>
              <span class="inline-flex items-center rounded-full bg-blue-100 dark:bg-blue-900/30 px-2 py-0.5 text-xs font-medium text-blue-700 dark:text-blue-300">
                {{ step.creditCost }} 크레딧
              </span>
            </div>
            <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
              {{ step.description }}
            </p>
          </div>
        </label>
      </div>
    </div>

    <!-- Step Order (drag-and-drop) -->
    <div v-if="orderedSteps.length > 0">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300 mb-3">
        실행 순서 (드래그하여 변경)
      </h3>
      <div class="space-y-1">
        <div
          v-for="(step, index) in orderedSteps"
          :key="step.key"
          draggable="true"
          class="flex items-center gap-3 p-3 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 cursor-grab active:cursor-grabbing transition-shadow hover:shadow-sm"
          :class="{ 'opacity-50': dragIndex === index }"
          @dragstart="handleDragStart(index)"
          @dragover="(e) => handleDragOver(e, index)"
          @dragend="handleDragEnd"
        >
          <span class="flex h-6 w-6 items-center justify-center rounded-full bg-primary-100 dark:bg-primary-900/30 text-xs font-bold text-primary-700 dark:text-primary-300">
            {{ index + 1 }}
          </span>
          <span class="text-sm font-medium text-gray-900 dark:text-gray-100 flex-1">
            {{ step.displayName }}
          </span>
          <!-- Connection arrow -->
          <svg v-if="index < orderedSteps.length - 1" class="absolute -bottom-3 left-1/2 -translate-x-1/2 w-4 h-4 text-gray-400" fill="none" viewBox="0 0 16 16">
            <path d="M8 2v10M4 8l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
      </div>
    </div>

    <!-- Credit Summary -->
    <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-4 bg-gray-50 dark:bg-gray-800/50">
      <div class="space-y-2 text-sm">
        <div class="flex justify-between text-gray-600 dark:text-gray-400">
          <span>스텝 합계</span>
          <span>{{ rawCost }} 크레딧</span>
        </div>
        <div v-if="discountApplied" class="flex justify-between text-green-600 dark:text-green-400 font-medium">
          <span>파이프라인 할인 (20%)</span>
          <span>-{{ discountAmount }} 크레딧</span>
        </div>
        <div v-else class="text-xs text-gray-500 dark:text-gray-500">
          3개 이상 선택 시 20% 할인 적용
        </div>
        <div class="border-t border-gray-200 dark:border-gray-700 pt-2 flex justify-between font-semibold text-gray-900 dark:text-gray-100">
          <span>총 비용</span>
          <span>{{ totalCost }} 크레딧</span>
        </div>
        <div class="flex justify-between text-xs" :class="hasEnoughCredits ? 'text-gray-500 dark:text-gray-400' : 'text-red-600 dark:text-red-400'">
          <span>보유 크레딧</span>
          <span>{{ creditBalance }}</span>
        </div>
      </div>
    </div>

    <!-- Start Button -->
    <button
      :disabled="selectedSteps.length === 0 || !hasEnoughCredits"
      class="w-full rounded-lg bg-gradient-to-r from-primary-600 to-primary-500 px-6 py-3 text-sm font-semibold text-white shadow-sm transition-all hover:from-primary-700 hover:to-primary-600 disabled:cursor-not-allowed disabled:opacity-50 disabled:from-gray-400 disabled:to-gray-400"
      @click="handleStart"
    >
      <span v-if="!hasEnoughCredits">크레딧 부족</span>
      <span v-else-if="selectedSteps.length === 0">스텝을 선택해주세요</span>
      <span v-else>파이프라인 시작 ({{ totalCost }} 크레딧)</span>
    </button>
  </div>
</template>
