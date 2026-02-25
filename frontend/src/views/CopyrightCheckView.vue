<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('copyrightCheck.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('copyrightCheck.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('copyrightCheck.pageGuideTitle')"
      :items="($tm('copyrightCheck.pageGuideMobile') as string[])"
    />

    <!-- Credit Display (Mobile) -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('copyrightCheck.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Summary Cards (Mobile) -->
    <div class="grid grid-cols-2 gap-3">
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('copyrightCheck.summaryPassed') }}</div>
        <div class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">{{ passedCount }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('copyrightCheck.summaryWarning') }}</div>
        <div class="mt-1 text-xl font-bold text-yellow-600 dark:text-yellow-400">{{ warningCount }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('copyrightCheck.summaryBlocked') }}</div>
        <div class="mt-1 text-xl font-bold text-red-600 dark:text-red-400">{{ blockedCount }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('copyrightCheck.summaryUnchecked') }}</div>
        <div class="mt-1 text-xl font-bold text-gray-600 dark:text-gray-400">{{ uncheckedVideos.length }}</div>
      </div>
    </div>

    <!-- Loading State -->
    <div
      v-if="loading"
      class="flex items-center justify-center py-12"
    >
      <svg class="h-8 w-8 animate-spin text-primary-600" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
    </div>

    <template v-else>
      <!-- Video Selector (Mobile - stacked) -->
      <VideoCheckSelector
        :videos="videos"
        :checking="checking"
        @run-check="handleRunCheck"
      />

      <!-- Results (Mobile - stacked) -->
      <div>
        <h2 class="mb-3 text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('copyrightCheck.results') }}
        </h2>
        <div v-if="results.length > 0" class="space-y-3">
          <CheckResultCard
            v-for="result in results"
            :key="result.id"
            :result="result"
            @auto-fix="handleAutoFix"
            @select="store.selectedResult = result"
          />
        </div>
        <div v-else class="rounded-xl border border-gray-200 bg-white p-8 text-center dark:border-gray-700 dark:bg-gray-900">
          <ShieldCheckIcon class="mx-auto h-10 w-10 text-gray-300 dark:text-gray-600" />
          <p class="mt-3 text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ $t('copyrightCheck.noResults') }}
          </p>
          <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ $t('copyrightCheck.noResultsMobileDesc') }}
          </p>
        </div>
      </div>
    </template>
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('copyrightCheck.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('copyrightCheck.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
          :class="isLow
            ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
            : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('copyrightCheck.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('copyrightCheck.pageGuideTitle')"
      :items="($tm('copyrightCheck.pageGuide') as string[])"
    />

    <!-- Summary Cards (Desktop) -->
    <div class="mb-8 grid grid-cols-4 gap-6">
      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('copyrightCheck.summaryPassed') }}</div>
            <div class="text-3xl font-bold text-green-600 dark:text-green-400">{{ passedCount }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <CheckCircleIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('copyrightCheck.summaryWarning') }}</div>
            <div class="text-3xl font-bold text-yellow-600 dark:text-yellow-400">{{ warningCount }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-yellow-100 dark:bg-yellow-900/30">
            <ExclamationTriangleIcon class="h-6 w-6 text-yellow-600 dark:text-yellow-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('copyrightCheck.summaryBlocked') }}</div>
            <div class="text-3xl font-bold text-red-600 dark:text-red-400">{{ blockedCount }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-red-100 dark:bg-red-900/30">
            <XCircleIcon class="h-6 w-6 text-red-600 dark:text-red-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('copyrightCheck.summaryUnchecked') }}</div>
            <div class="text-3xl font-bold text-gray-600 dark:text-gray-400">{{ uncheckedVideos.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-700">
            <QuestionMarkCircleIcon class="h-6 w-6 text-gray-500 dark:text-gray-400" />
          </div>
        </div>
      </div>
    </div>

    <!-- Loading State -->
    <div
      v-if="loading"
      class="flex items-center justify-center py-16"
    >
      <svg class="h-8 w-8 animate-spin text-primary-600" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
    </div>

    <!-- Two-panel layout (Desktop) -->
    <div v-else class="grid grid-cols-12 gap-6">
      <!-- Left Panel: Video Selector -->
      <div class="col-span-5 desktop:col-span-4">
        <VideoCheckSelector
          :videos="videos"
          :checking="checking"
          @run-check="handleRunCheck"
        />
      </div>

      <!-- Right Panel: Results -->
      <div class="col-span-7 desktop:col-span-8">
        <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="border-b border-gray-200 px-4 py-3 dark:border-gray-700">
            <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('copyrightCheck.results') }}
            </h2>
          </div>
          <div class="p-4">
            <div v-if="results.length > 0" class="space-y-4">
              <CheckResultCard
                v-for="result in results"
                :key="result.id"
                :result="result"
                @auto-fix="handleAutoFix"
                @select="store.selectedResult = result"
              />
            </div>
            <div v-else class="py-12 text-center">
              <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
                <ShieldCheckIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
              </div>
              <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
                {{ $t('copyrightCheck.noResults') }}
              </h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">
                {{ $t('copyrightCheck.noResultsDesc') }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  QuestionMarkCircleIcon,
  ShieldCheckIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import VideoCheckSelector from '@/components/copyrightcheck/VideoCheckSelector.vue'
import CheckResultCard from '@/components/copyrightcheck/CheckResultCard.vue'
import { useCopyrightCheckStore } from '@/stores/copyrightCheck'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { RunCheckRequest } from '@/types/copyrightCheck'

const { t } = useI18n({ useScope: 'global' })
const store = useCopyrightCheckStore()
const { balance, isLow, fetchBalance } = useCredit()
const { success, error: showError } = useNotification()

const isTablet = useMediaQuery('(min-width: 768px)')

const {
  videos,
  results,
  loading,
  checking,
  passedCount,
  warningCount,
  blockedCount,
  uncheckedVideos,
} = storeToRefs(store)

async function handleRunCheck(request: RunCheckRequest) {
  try {
    await store.runCheck(request)
    success(t('copyrightCheck.checkSuccess'))
    await fetchBalance()
  } catch {
    showError(t('copyrightCheck.checkError'))
  }
}

async function handleAutoFix(resultId: number, issueId: string) {
  try {
    await store.autoFix(resultId, issueId)
    success(t('copyrightCheck.autoFixSuccess'))
  } catch {
    showError(t('copyrightCheck.autoFixError'))
  }
}

onMounted(() => {
  store.fetchVideos()
  store.fetchResults()
  fetchBalance()
})
</script>
