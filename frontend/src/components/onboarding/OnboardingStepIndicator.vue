<template>
  <div class="flex items-center justify-center">
    <template v-for="(step, index) in steps" :key="step.number">
      <!-- Step circle -->
      <div class="flex flex-col items-center">
        <div
          class="flex h-10 w-10 items-center justify-center rounded-full text-sm font-semibold transition-all duration-300"
          :class="getStepCircleClass(step.number)"
        >
          <svg
            v-if="step.number < currentStep"
            class="h-5 w-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            stroke-width="2.5"
          >
            <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" />
          </svg>
          <span v-else>{{ step.number }}</span>
        </div>
        <span
          class="mt-2 text-xs font-medium transition-colors duration-300"
          :class="step.number <= currentStep ? 'text-primary-600 dark:text-primary-400' : 'text-gray-400 dark:text-gray-500'"
        >
          {{ step.label }}
        </span>
      </div>
      <!-- Connector line -->
      <div
        v-if="index < steps.length - 1"
        class="mx-3 mb-6 h-0.5 w-16 rounded transition-all duration-300"
        :class="step.number < currentStep ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
      ></div>
    </template>
  </div>

  <!-- Progress bar -->
  <div class="mt-6 w-full max-w-md mx-auto">
    <div class="h-1.5 w-full rounded-full bg-gray-200 dark:bg-gray-700 overflow-hidden">
      <div
        class="h-full bg-gradient-to-r from-primary-600 to-purple-600 transition-all duration-500 ease-out"
        :style="{ width: `${progressPercentage}%` }"
      ></div>
    </div>
    <p class="mt-2 text-center text-xs text-gray-500 dark:text-gray-400">
      {{ progressPercentage }}% {{ t('onboarding.completed') }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n({ useScope: 'global' })

interface Step {
  number: number
  label: string
}

interface Props {
  currentStep: number
  steps: Step[]
}

const props = defineProps<Props>()

const progressPercentage = computed(() => {
  if (props.currentStep === 0) return 0
  return Math.round(((props.currentStep - 1) / (props.steps.length - 1)) * 100)
})

function getStepCircleClass(stepNumber: number): string {
  if (stepNumber < props.currentStep) {
    return 'bg-primary-600 text-white'
  }
  if (stepNumber === props.currentStep) {
    return 'bg-primary-600 text-white ring-4 ring-primary-100 dark:ring-primary-900/50'
  }
  return 'bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400'
}
</script>
