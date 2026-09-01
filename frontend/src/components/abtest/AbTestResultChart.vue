<script setup lang="ts">
import { computed } from 'vue'
import { StarIcon } from '@heroicons/vue/24/outline'
import type { AbTestVariant, VariantLabel } from '@/types/abtest'

const props = defineProps<{
  variants: AbTestVariant[]
}>()

/**
 * 막대 길이의 기준. **측정된 변형만** 본다.
 *
 * 미측정을 0 으로 섞으면 그 변형의 막대가 바닥에 붙어 "가장 낮은 성과" 로 읽힌다.
 * 재지 않은 것과 성과가 없는 것은 다르다.
 */
const maxCtr = computed(() => {
  const measured = props.variants.map(v => v.ctr).filter((ctr): ctr is number => ctr != null)
  return Math.max(...measured, 0.01)
})

const barColors: Record<VariantLabel, string> = {
  A: 'bg-blue-500',
  B: 'bg-purple-500',
  C: 'bg-orange-500',
  D: 'bg-teal-500',
}

const textColors: Record<VariantLabel, string> = {
  A: 'text-blue-600 dark:text-blue-400',
  B: 'text-purple-600 dark:text-purple-400',
  C: 'text-orange-600 dark:text-orange-400',
  D: 'text-teal-600 dark:text-teal-400',
}

function getBarColor(label: VariantLabel): string {
  return barColors[label] ?? 'bg-gray-500'
}

function getTextColor(label: VariantLabel): string {
  return textColors[label] ?? 'text-gray-600 dark:text-gray-400'
}

function getBarWidth(ctr: number | null): string {
  if (ctr == null || maxCtr.value === 0) return '0%'
  return `${(ctr / maxCtr.value) * 100}%`
}

/** 측정된 값만 숫자로 보여준다. 아니면 화면이 "측정 불가" 를 그린다. */
function isMeasured(variant: AbTestVariant): boolean {
  return variant.ctr != null
}
</script>

<template>
  <div class="space-y-3">
    <div
      v-for="variant in variants"
      :key="variant.id"
      class="space-y-1"
    >
      <!-- Label row -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span :class="['text-body font-semibold', getTextColor(variant.label)]">
            {{ variant.label }}
          </span>
          <StarIcon
            v-if="variant.isWinner"
            class="h-4 w-4 text-yellow-500"
          />
        </div>
        <span
          v-if="variant.ctr != null"
          :data-testid="`ab-ctr-${variant.label}`"
          class="text-body font-bold text-gray-900 dark:text-white"
        >
          {{ variant.ctr.toFixed(1) }}%
        </span>
        <span
          v-else
          :data-testid="`ab-ctr-${variant.label}-unavailable`"
          class="text-body text-gray-500 dark:text-gray-400"
        >
          {{ $t('abTest.metricUnavailable') }}
        </span>
      </div>

      <!--
        CTR Bar — 측정된 변형만 그린다. 0 폭 막대는 "가장 낮은 성과" 로 읽힌다.
      -->
      <div v-if="isMeasured(variant)" class="h-6 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
        <div
          :class="['flex h-full items-center justify-end rounded-full px-2 transition-all duration-500', getBarColor(variant.label)]"
          :style="{ width: getBarWidth(variant.ctr) }"
        >
          <StarIcon
            v-if="variant.isWinner"
            class="h-3.5 w-3.5 text-white"
          />
        </div>
      </div>

      <!-- 미측정 사유. 막대 자리를 비워 두기만 하면 왜 없는지 알 수 없다. -->
      <p
        v-if="!isMeasured(variant)"
        :data-testid="`ab-metrics-${variant.label}-unavailable`"
        class="text-body-xs text-gray-500 dark:text-gray-400"
      >
        {{ variant.metricsUnavailableReason ?? $t('abTest.metricUnavailable') }}
      </p>

      <!-- Metrics row -->
      <div
        v-if="isMeasured(variant)"
        :data-testid="`ab-metrics-row-${variant.label}`"
        class="flex items-center gap-4 text-body-xs text-gray-500 dark:text-gray-400"
      >
        <span>
          {{ $t('abTest.impressions') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.impressions?.toLocaleString() }}</span>
        </span>
        <span>
          {{ $t('abTest.clicks') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.clicks?.toLocaleString() }}</span>
        </span>
        <span>
          {{ $t('abTest.ctr') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.ctr?.toFixed(2) }}%</span>
        </span>
        <span>
          {{ $t('abTest.avgWatchTime') }}:
          <span class="font-medium text-gray-700 dark:text-gray-300">{{ variant.avgWatchTime != null ? `${variant.avgWatchTime}${$t('abTest.sec')}` : '—' }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
