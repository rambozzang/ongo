<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('performanceHeatmap.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.description') }}
        </p>
      </div>
    </div>

    <PageGuide
      :title="$t('performanceHeatmap.pageGuideTitle')"
      :items="($tm('performanceHeatmap.pageGuide') as string[])"
    />

    <!-- Filter bar -->
    <div class="mb-6 flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <!-- Platform select -->
      <div>
        <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.filterPlatform') }}
        </label>
        <select
          v-model="store.selectedPlatform"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 tablet:w-40"
          @change="handleFilterChange"
        >
          <option value="youtube">{{ $t('performanceHeatmap.platformYoutube') }}</option>
          <option value="tiktok">{{ $t('performanceHeatmap.platformTiktok') }}</option>
          <option value="instagram">{{ $t('performanceHeatmap.platformInstagram') }}</option>
          <option value="naverclip">{{ $t('performanceHeatmap.platformNaverClip') }}</option>
        </select>
      </div>

      <!-- Metric select -->
      <div>
        <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.filterMetric') }}
        </label>
        <select
          v-model="store.selectedMetric"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 tablet:w-40"
          @change="handleFilterChange"
        >
          <option value="VIEWS">{{ $t('performanceHeatmap.metricViews') }}</option>
          <option value="LIKES">{{ $t('performanceHeatmap.metricLikes') }}</option>
          <option value="COMMENTS">{{ $t('performanceHeatmap.metricComments') }}</option>
          <option value="CTR">{{ $t('performanceHeatmap.metricCtr') }}</option>
          <option value="WATCH_TIME">{{ $t('performanceHeatmap.metricWatchTime') }}</option>
        </select>
      </div>

      <!-- Period select -->
      <div>
        <label class="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.filterPeriod') }}
        </label>
        <select
          v-model="store.selectedPeriod"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 tablet:w-40"
          @change="handleFilterChange"
        >
          <option value="7d">{{ $t('performanceHeatmap.period7d') }}</option>
          <option value="30d">{{ $t('performanceHeatmap.period30d') }}</option>
          <option value="90d">{{ $t('performanceHeatmap.period90d') }}</option>
        </select>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="store.loading" class="py-12 text-center">
      <div class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary-500 border-t-transparent" />
      <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('performanceHeatmap.loading') }}
      </p>
    </div>

    <!-- Content -->
    <template v-else-if="store.heatmapData">
      <!-- Heatmap Grid -->
      <HeatmapGrid
        :data="store.heatmapData"
        :max-value="store.maxValue"
        class="mb-6"
      />

      <!-- Best / Worst Slots -->
      <div class="mb-6 grid gap-6 tablet:grid-cols-2">
        <!-- Best slots -->
        <div>
          <h3 class="mb-3 flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
            <ArrowTrendingUpIcon class="h-5 w-5 text-green-500 dark:text-green-400" />
            {{ $t('performanceHeatmap.bestSlotsTitle') }}
          </h3>
          <div class="space-y-2">
            <TimeSlotCard
              v-for="(slot, index) in store.heatmapData.bestSlots"
              :key="`best-${index}`"
              :slot-data="slot"
              :rank="index + 1"
              type="best"
              :metric="store.selectedMetric"
            />
          </div>
        </div>

        <!-- Worst slots -->
        <div>
          <h3 class="mb-3 flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
            <ArrowTrendingDownIcon class="h-5 w-5 text-red-500 dark:text-red-400" />
            {{ $t('performanceHeatmap.worstSlotsTitle') }}
          </h3>
          <div class="space-y-2">
            <TimeSlotCard
              v-for="(slot, index) in store.heatmapData.worstSlots"
              :key="`worst-${index}`"
              :slot-data="slot"
              :rank="index + 1"
              type="worst"
              :metric="store.selectedMetric"
            />
          </div>
        </div>
      </div>

      <!-- Recommendations Section -->
      <div>
        <h3 class="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
          <LightBulbIcon class="h-5 w-5 text-yellow-500 dark:text-yellow-400" />
          {{ $t('performanceHeatmap.recommendationsTitle') }}
        </h3>
        <div v-if="store.recommendations.length > 0" class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
          <RecommendationCard
            v-for="(rec, index) in store.recommendations"
            :key="index"
            :recommendation="rec"
          />
        </div>
        <div v-else class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-8 text-center shadow-sm">
          <LightBulbIcon class="mx-auto h-10 w-10 text-gray-300 dark:text-gray-600" />
          <p class="mt-2 text-sm font-medium text-gray-500 dark:text-gray-400">
            {{ $t('performanceHeatmap.noRecommendations') }}
          </p>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ $t('performanceHeatmap.noRecommendationsDesc') }}
          </p>
        </div>
      </div>
    </template>

    <!-- Empty state -->
    <div v-else class="py-12 text-center">
      <ChartBarIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
      <p class="mt-3 text-sm font-medium text-gray-500 dark:text-gray-400">
        {{ $t('performanceHeatmap.noData') }}
      </p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        {{ $t('performanceHeatmap.noDataDesc') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  LightBulbIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import HeatmapGrid from '@/components/performanceheatmap/HeatmapGrid.vue'
import TimeSlotCard from '@/components/performanceheatmap/TimeSlotCard.vue'
import RecommendationCard from '@/components/performanceheatmap/RecommendationCard.vue'
import { usePerformanceHeatmapStore } from '@/stores/performanceHeatmap'

const store = usePerformanceHeatmapStore()

async function handleFilterChange() {
  await store.fetchHeatmap()
}

onMounted(async () => {
  await Promise.all([store.fetchHeatmap(), store.fetchRecommendations()])
})
</script>
