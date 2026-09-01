<script setup lang="ts">
import { computed } from 'vue'
import { TrophyIcon } from '@heroicons/vue/24/outline'
import type { AbTestVariant, AbTestType } from '@/types/abtest'

interface Props {
  variants: AbTestVariant[]
  type: AbTestType
}

const props = defineProps<Props>()

/**
 * 막대 기준값들. **측정된 변형만** 본다.
 *
 * 미측정을 0 으로 섞으면 그 변형의 막대가 바닥에 붙어 "가장 낮은 성과" 로 읽힌다.
 * 재지 않은 것과 성과가 없는 것은 다르다.
 */
function maxOf(pick: (v: AbTestVariant) => number | null): number {
  const measured = props.variants.map(pick).filter((n): n is number => n != null)
  return measured.length > 0 ? Math.max(...measured) : 0
}

const maxCTR = computed(() => maxOf(v => v.ctr))
const maxImpressions = computed(() => maxOf(v => v.impressions))
const maxClicks = computed(() => maxOf(v => v.clicks))
const maxWatchTime = computed(() => Math.max(...props.variants.map(v => v.avgWatchTime ?? 0), 0))

/** 노출이 측정돼야 클릭·CTR 도 존재한다. 세 지표는 함께 나타나거나 함께 없다. */
function isMeasured(variant: AbTestVariant): boolean {
  return variant.ctr != null
}

function barWidth(value: number | null, max: number): string {
  if (value == null || max <= 0) return '0%'
  return `${(value / max) * 100}%`
}
</script>

<template>
  <div class="grid grid-cols-2 gap-6">
    <div v-for="variant in variants" :key="variant.id" class="space-y-3">
      <!-- Header with label and winner badge -->
      <div class="flex items-center justify-between">
        <h4 class="text-title font-semibold text-gray-900 dark:text-white">{{ variant.label }}</h4>
        <div v-if="variant.isWinner" class="flex items-center gap-1.5 px-3 py-1 bg-warning-subtle rounded-full">
          <TrophyIcon class="w-4 h-4 text-warning-strong" />
          <span class="text-body font-medium text-warning-strong">우승</span>
        </div>
      </div>

      <!-- Content preview -->
      <div class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
        <!-- Thumbnail preview -->
        <div v-if="type === 'THUMBNAIL' && variant.thumbnailUrl" class="aspect-video bg-gray-100 dark:bg-gray-700">
          <img :src="variant.thumbnailUrl" :alt="variant.label" class="w-full h-full object-cover" />
        </div>

        <!-- Text preview -->
        <div v-else class="p-4 bg-gray-50 dark:bg-gray-700/50">
          <p class="text-body text-gray-700 dark:text-gray-300 whitespace-pre-wrap">{{ variant.value }}</p>
        </div>
      </div>

      <!-- Metrics -->
      <div class="space-y-3">
        <!-- Impressions -->
        <div>
          <div class="flex justify-between text-body mb-1">
            <span class="text-gray-600 dark:text-gray-400">노출 수</span>
            <span v-if="variant.impressions != null" :data-testid="`vc-impressions-${variant.label}`" class="font-semibold text-gray-900 dark:text-white">{{ variant.impressions.toLocaleString() }}</span>
            <span v-else :data-testid="`vc-impressions-${variant.label}-unavailable`" class="text-gray-500 dark:text-gray-400">{{ $t('abTest.metricUnavailable') }}</span>
          </div>
          <div v-if="isMeasured(variant)" class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: barWidth(variant.impressions, maxImpressions) }"
            ></div>
          </div>
        </div>

        <!-- Clicks -->
        <div>
          <div class="flex justify-between text-body mb-1">
            <span class="text-gray-600 dark:text-gray-400">클릭 수</span>
            <span v-if="variant.clicks != null" :data-testid="`vc-clicks-${variant.label}`" class="font-semibold text-gray-900 dark:text-white">{{ variant.clicks.toLocaleString() }}</span>
            <span v-else :data-testid="`vc-clicks-${variant.label}-unavailable`" class="text-gray-500 dark:text-gray-400">{{ $t('abTest.metricUnavailable') }}</span>
          </div>
          <div v-if="isMeasured(variant)" class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: barWidth(variant.clicks, maxClicks) }"
            ></div>
          </div>
        </div>

        <!-- CTR -->
        <div>
          <div class="flex justify-between text-body mb-1">
            <span class="text-gray-600 dark:text-gray-400">클릭률 (CTR)</span>
            <span v-if="variant.ctr != null" :data-testid="`vc-ctr-${variant.label}`" class="font-semibold text-gray-900 dark:text-white">{{ variant.ctr.toFixed(2) }}%</span>
            <span v-else :data-testid="`vc-ctr-${variant.label}-unavailable`" class="text-gray-500 dark:text-gray-400">{{ $t('abTest.metricUnavailable') }}</span>
          </div>
          <div v-if="isMeasured(variant)" class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: barWidth(variant.ctr, maxCTR) }"
            ></div>
          </div>
        </div>

        <!-- Watch time -->
        <div>
          <div class="flex justify-between text-body mb-1">
            <span class="text-gray-600 dark:text-gray-400">평균 시청 시간</span>
            <span class="font-semibold text-gray-900 dark:text-white">{{ variant.avgWatchTime != null ? `${variant.avgWatchTime}초` : '—' }}</span>
          </div>
          <div class="relative h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              v-if="variant.avgWatchTime != null"
              :class="['h-full transition-all', variant.isWinner ? 'bg-yellow-500' : 'bg-blue-500']"
              :style="{ width: `${maxWatchTime > 0 ? ((variant.avgWatchTime ?? 0) / maxWatchTime) * 100 : 0}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
