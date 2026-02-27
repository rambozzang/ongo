<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  SignalIcon,
  ChartBarIcon,
  TrophyIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useChannelHealthStore } from '@/stores/channelHealth'
import HealthMetricCard from '@/components/channelhealth/HealthMetricCard.vue'
import TrendRow from '@/components/channelhealth/TrendRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = useChannelHealthStore()

const selectedMetricId = ref<number | null>(null)

const selectedMetric = computed(() =>
  store.metrics.find((m) => m.id === selectedMetricId.value) ?? null,
)

function handleSelectMetric(id: number) {
  selectedMetricId.value = id
  store.fetchTrends(id)
}

const weakestAreaLabel = computed(() => {
  const labels: Record<string, string> = {
    GROWTH: t('channelHealth.areaGrowth'),
    ENGAGEMENT: t('channelHealth.areaEngagement'),
    CONSISTENCY: t('channelHealth.areaConsistency'),
    AUDIENCE: t('channelHealth.areaAudience'),
    MONETIZATION: t('channelHealth.areaMonetization'),
  }
  return labels[store.summary?.weakestArea ?? ''] ?? store.summary?.weakestArea ?? '-'
})

onMounted(() => {
  store.fetchMetrics()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('channelHealth.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('channelHealth.description') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Channels -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <SignalIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('channelHealth.totalChannels') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.totalChannels ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- Average Score -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <ChartBarIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('channelHealth.avgScore') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary?.avgOverallScore ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- Top Channel -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-yellow-100 dark:bg-yellow-900/30">
              <TrophyIcon class="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('channelHealth.topChannel') }}</p>
              <p class="text-base font-bold text-gray-900 dark:text-gray-100 truncate max-w-[140px]">
                {{ store.summary?.topChannel ?? '-' }}
              </p>
            </div>
          </div>
        </div>

        <!-- Weakest Area -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-red-100 dark:bg-red-900/30">
              <ExclamationTriangleIcon class="h-5 w-5 text-red-600 dark:text-red-400" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('channelHealth.weakestArea') }}</p>
              <p class="text-base font-bold text-gray-900 dark:text-gray-100">
                {{ weakestAreaLabel }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Channel Health Cards Grid -->
      <section class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('channelHealth.channelHealthByChannel') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">
            ({{ store.metrics.length }})
          </span>
        </h2>

        <div v-if="store.metrics.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <HealthMetricCard
            v-for="m in store.metrics"
            :key="m.id"
            :metric="m"
            :selected="m.id === selectedMetricId"
            @select="handleSelectMetric"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <SignalIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('channelHealth.noChannelsTitle') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('channelHealth.noChannelsDesc') }}
          </p>
        </div>
      </section>

      <!-- Trend Table for selected channel -->
      <section v-if="selectedMetric">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            "{{ selectedMetric.channelName }}" {{ $t('channelHealth.trend') }}
          </h2>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ store.trends.length }}{{ $t('channelHealth.itemCount') }}
          </span>
        </div>

        <div v-if="store.trends.length > 0" class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
                  <th class="px-4 py-3 text-left font-medium">{{ $t('channelHealth.category') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('channelHealth.date') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('channelHealth.score') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('channelHealth.changeRate') }}</th>
                  <th class="px-4 py-3 text-left font-medium">{{ $t('channelHealth.recommendation') }}</th>
                </tr>
              </thead>
              <tbody>
                <TrendRow
                  v-for="trend in store.trends"
                  :key="trend.id"
                  :trend="trend"
                />
              </tbody>
            </table>
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ChartBarIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('channelHealth.noTrendData') }}
          </p>
        </div>
      </section>
    </template>
  </div>
</template>
