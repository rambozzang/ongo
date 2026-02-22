<template>
  <button
    type="button"
    class="w-full rounded-2xl border-2 p-5 text-left transition-all duration-200"
    :class="[
      isSelected
        ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 shadow-lg'
        : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-md',
      isRecommended ? 'relative' : ''
    ]"
    @click="$emit('select', plan.type)"
  >
    <!-- Recommended badge -->
    <div
      v-if="isRecommended"
      class="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-gradient-to-r from-primary-600 to-purple-600 px-3 py-1 text-xs font-semibold text-white shadow-lg"
    >
      {{ t('onboarding.recommended') }}
    </div>

    <!-- Plan header -->
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ plan.name }}</h3>
        <div class="mt-1 flex items-baseline gap-1">
          <span class="text-2xl font-bold text-primary-600 dark:text-primary-400">
            {{ formatPrice(plan.price) }}
          </span>
          <span v-if="plan.price > 0" class="text-sm text-gray-500 dark:text-gray-400">{{ t('onboarding.perMonth') }}</span>
        </div>
      </div>

      <!-- Selection indicator -->
      <div
        class="flex h-6 w-6 items-center justify-center rounded-full border-2 transition-colors"
        :class="isSelected
          ? 'border-primary-600 bg-primary-600'
          : 'border-gray-300 dark:border-gray-600'"
      >
        <svg
          v-if="isSelected"
          class="h-4 w-4 text-white"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="3"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M4.5 12.75l6 6 9-13.5" />
        </svg>
      </div>
    </div>

    <!-- Key features -->
    <ul class="space-y-2">
      <li class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
        <svg class="h-4 w-4 shrink-0 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ plan.maxUploadsPerMonth === -1 ? t('onboarding.unlimitedUploads') : t('onboarding.uploadsPerMonth', { count: plan.maxUploadsPerMonth }) }}</span>
      </li>
      <li class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
        <svg class="h-4 w-4 shrink-0 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ t('onboarding.platformConnections', { count: plan.maxPlatforms }) }}</span>
      </li>
      <li class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
        <svg class="h-4 w-4 shrink-0 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ t('onboarding.aiCredits', { count: plan.freeAiCredits }) }}</span>
      </li>
      <li v-if="plan.maxScheduleDays > 0" class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
        <svg class="h-4 w-4 shrink-0 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ t('onboarding.scheduleDays', { count: plan.maxScheduleDays }) }}</span>
      </li>
    </ul>

    <!-- Storage info -->
    <div class="mt-3 pt-3 border-t border-gray-200 dark:border-gray-700">
      <p class="text-xs text-gray-500 dark:text-gray-400">
        {{ t('onboarding.storage') }} {{ formatStorage(plan.storageMb) }} • {{ plan.support }}
      </p>
    </div>
  </button>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { Plan, PlanType } from '@/types/subscription'

const { t } = useI18n()

interface Props {
  plan: Plan
  isSelected: boolean
  isRecommended?: boolean
}

defineProps<Props>()

defineEmits<{
  select: [planType: PlanType]
}>()

function formatPrice(price: number): string {
  if (price === 0) return t('onboarding.free')
  return price.toLocaleString('ko-KR')
}

function formatStorage(mb: number): string {
  if (mb >= 1024) {
    return `${(mb / 1024).toFixed(0)}GB`
  }
  return `${mb}MB`
}
</script>
