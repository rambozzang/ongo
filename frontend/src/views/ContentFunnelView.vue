<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  FunnelIcon,
  ArrowTrendingUpIcon,
  FilmIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import { useLocale } from '@/composables/useLocale'
import { useContentFunnelStore } from '@/stores/contentFunnel'
import FunnelChart from '@/components/contentfunnel/FunnelChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = useContentFunnelStore()
const { funnels, summary, loading } = storeToRefs(store)

onMounted(() => {
  store.fetchFunnels()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentFunnel.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentFunnel.description') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <FilmIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentFunnel.analyzedVideos') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalVideos }}{{ $t('contentFunnel.countSuffix') }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <ArrowTrendingUpIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentFunnel.avgConversion') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.avgConversion }}%
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <FunnelIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentFunnel.bestConversionVideo') }}</p>
              <p class="truncate text-sm font-bold text-gray-900 dark:text-gray-100">
                {{ summary.bestConversionVideo }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-red-100 dark:bg-red-900/30">
              <ExclamationTriangleIcon class="h-5 w-5 text-red-600 dark:text-red-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentFunnel.worstDropOffStage') }}</p>
              <p class="text-sm font-bold text-gray-900 dark:text-gray-100">
                {{ summary.worstDropOffStage }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Funnel Charts -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentFunnel.funnelByVideo') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ funnels.length }})</span>
        </h2>

        <div v-if="funnels.length > 0" class="space-y-6">
          <div
            v-for="funnel in funnels"
            :key="funnel.id"
            class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900"
          >
            <div class="mb-4 flex items-center justify-between">
              <div>
                <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ funnel.videoTitle }}</h3>
                <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ funnel.platform }} · {{ $t('contentFunnel.conversionRate') }} {{ funnel.overallConversion }}%</p>
              </div>
              <span
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="funnel.overallConversion >= 1 ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'"
              >
                {{ funnel.overallConversion >= 1 ? $t('contentFunnel.statusGood') : $t('contentFunnel.statusNeedsImprovement') }}
              </span>
            </div>

            <FunnelChart :funnel="funnel" />
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <FunnelIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('contentFunnel.noFunnelTitle') }}</h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('contentFunnel.noFunnelDesc') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>
