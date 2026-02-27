<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  RectangleGroupIcon,
  EyeIcon,
  DocumentDuplicateIcon,
  GlobeAltIcon,
} from '@heroicons/vue/24/outline'
import { usePortfolioBuilderStore } from '@/stores/portfolioBuilder'
import { useLocale } from '@/composables/useLocale'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = usePortfolioBuilderStore()
const { portfolios, summary, loading } = storeToRefs(store)

onMounted(() => {
  store.fetchPortfolios()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('portfolioBuilder.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('portfolioBuilder.description') }}
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
              <RectangleGroupIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.totalPortfolios') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalPortfolios }}{{ $t('portfolioBuilder.countUnit') }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <GlobeAltIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.publishedPortfolios') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.publishedCount }}{{ $t('portfolioBuilder.countUnit') }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <EyeIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.totalViews') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.totalViews.toLocaleString('ko-KR') }}
              </p>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <DocumentDuplicateIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.popularTemplate') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ summary.topTemplate || '-' }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Portfolio Grid -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('portfolioBuilder.myPortfolios') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ portfolios.length }})</span>
        </h2>

        <div v-if="portfolios.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="portfolio in portfolios"
            :key="portfolio.id"
            class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
          >
            <div class="mb-3 flex items-start justify-between">
              <div class="min-w-0 flex-1">
                <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
                  {{ portfolio.title }}
                </h3>
                <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                  {{ portfolio.template }}
                </p>
              </div>
              <span
                class="ml-2 inline-flex flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
                :class="portfolio.isPublished ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'"
              >
                {{ portfolio.isPublished ? $t('portfolioBuilder.public') : $t('portfolioBuilder.private') }}
              </span>
            </div>

            <p v-if="portfolio.description" class="mb-3 line-clamp-2 text-xs text-gray-600 dark:text-gray-400">
              {{ portfolio.description }}
            </p>

            <div class="grid grid-cols-2 gap-3 text-xs">
              <div>
                <p class="text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.sections') }}</p>
                <p class="font-semibold text-gray-900 dark:text-gray-100">{{ portfolio.sections?.length ?? 0 }}{{ $t('portfolioBuilder.countUnit') }}</p>
              </div>
              <div>
                <p class="text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.views') }}</p>
                <p class="font-semibold text-gray-900 dark:text-gray-100">{{ portfolio.viewCount?.toLocaleString('ko-KR') ?? 0 }}</p>
              </div>
            </div>

            <div v-if="portfolio.publicUrl" class="mt-3 truncate text-xs text-primary-600 dark:text-primary-400">
              {{ portfolio.publicUrl }}
            </div>
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <RectangleGroupIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('portfolioBuilder.noPortfolios') }}</h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('portfolioBuilder.noPortfoliosDesc') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>
