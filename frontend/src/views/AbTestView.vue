<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('abTest.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('abTest.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('abTest.pageGuideTitle')"
      :items="($tm('abTest.pageGuideMobile') as string[])"
    />

    <!-- Credit Display -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Summary Cards (Mobile) -->
    <div class="grid grid-cols-2 gap-3">
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
        <div class="mt-1 text-xl font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
        <div class="mt-1 text-xl font-bold text-blue-600 dark:text-blue-400">{{ activeTests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
        <div class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">{{ completedTests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
        <div class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">+{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%</div>
      </div>
    </div>

    <!-- Tab Navigation (Mobile) -->
    <div class="border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="flex-1 border-b-2 px-1 py-3 text-center text-xs font-medium transition-colors"
          :class="activeTab === tab.key
            ? 'border-primary-500 text-primary-600 dark:text-primary-400'
            : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'"
          @click="store.setActiveTab(tab.key)"
        >
          <component :is="tab.icon" class="mx-auto mb-1 h-5 w-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Tab Content (Mobile) -->
    <div v-if="activeTab === 'active'">
      <div v-if="activeTests.length > 0" class="space-y-4">
        <AbTestCard
          v-for="test in activeTests"
          :key="test.id"
          :test="test"
          @select="handleSelectTest"
          @start="handleStartTest"
          @pause="handlePauseTest"
          @apply-winner="handleApplyWinner"
        />
      </div>
      <div v-else class="py-12 text-center">
        <BeakerIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ $t('abTest.emptyActiveDesc') }}</p>
      </div>
    </div>

    <div v-if="activeTab === 'completed'">
      <div v-if="completedTests.length > 0" class="space-y-4">
        <AbTestCard
          v-for="test in completedTests"
          :key="test.id"
          :test="test"
          @select="handleSelectTest"
          @start="handleStartTest"
          @pause="handlePauseTest"
          @apply-winner="handleApplyWinner"
        />
      </div>
      <div v-else class="py-12 text-center">
        <CheckCircleIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ $t('abTest.emptyCompletedDesc') }}</p>
      </div>
    </div>

    <div v-if="activeTab === 'create'">
      <CreateTestForm
        :videos="videos"
        :processing="processing"
        @create="handleCreateTest"
      />
    </div>
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('abTest.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('abTest.description') }}
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
          <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('abTest.pageGuideTitle')"
      :items="($tm('abTest.pageGuide') as string[])"
    />

    <!-- Summary Cards (Desktop) -->
    <div class="mb-8 grid grid-cols-4 gap-6">
      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
            <div class="text-3xl font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
            <BeakerIcon class="h-6 w-6 text-purple-600 dark:text-purple-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
            <div class="text-3xl font-bold text-blue-600 dark:text-blue-400">{{ activeTests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
            <div class="h-3 w-3 animate-pulse rounded-full bg-blue-500"></div>
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
            <div class="text-3xl font-bold text-green-600 dark:text-green-400">{{ completedTests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <CheckCircleIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
            <div class="text-3xl font-bold text-green-600 dark:text-green-400">
              +{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%
            </div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <ArrowTrendingUpIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
          </div>
        </div>
      </div>
    </div>

    <!-- Tab Navigation (Desktop) -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="inline-flex items-center gap-2 border-b-2 px-1 py-3 text-sm font-medium transition-colors"
          :class="activeTab === tab.key
            ? 'border-primary-500 text-primary-600 dark:text-primary-400'
            : 'border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 dark:hover:border-gray-600 hover:text-gray-700 dark:hover:text-gray-300'"
          @click="store.setActiveTab(tab.key)"
        >
          <component :is="tab.icon" class="h-5 w-5" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Tab Content (Desktop) -->
    <div class="mt-6">
      <!-- Active Tests -->
      <div v-if="activeTab === 'active'">
        <div v-if="activeTests.length > 0" class="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <AbTestCard
            v-for="test in activeTests"
            :key="test.id"
            :test="test"
            @select="handleSelectTest"
            @start="handleStartTest"
            @pause="handlePauseTest"
            @apply-winner="handleApplyWinner"
          />
        </div>
        <div v-else class="py-16 text-center">
          <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
            <BeakerIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
          </div>
          <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">{{ $t('abTest.emptyTitle') }}</h3>
          <p class="mb-6 text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.emptyActiveDesc') }}</p>
          <button
            class="btn-primary inline-flex items-center gap-2"
            @click="store.setActiveTab('create')"
          >
            <PlusIcon class="h-5 w-5" />
            {{ $t('abTest.createFirstTest') }}
          </button>
        </div>
      </div>

      <!-- Completed Tests -->
      <div v-if="activeTab === 'completed'">
        <div v-if="completedTests.length > 0" class="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <AbTestCard
            v-for="test in completedTests"
            :key="test.id"
            :test="test"
            @select="handleSelectTest"
            @start="handleStartTest"
            @pause="handlePauseTest"
            @apply-winner="handleApplyWinner"
          />
        </div>
        <div v-else class="py-16 text-center">
          <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
            <CheckCircleIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
          </div>
          <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">{{ $t('abTest.emptyTitle') }}</h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('abTest.emptyCompletedDesc') }}</p>
        </div>
      </div>

      <!-- Create New Test -->
      <div v-if="activeTab === 'create'">
        <CreateTestForm
          :videos="videos"
          :processing="processing"
          @create="handleCreateTest"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  BeakerIcon,
  CheckCircleIcon,
  PlusIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import AbTestCard from '@/components/abtest/AbTestCard.vue'
import CreateTestForm from '@/components/abtest/CreateTestForm.vue'
import { useAbTestStore } from '@/stores/abtest'
import { useCredit } from '@/composables/useCredit'
import type { AbTestType, VariantLabel } from '@/types/abtest'

const { t } = useI18n({ useScope: 'global' })
const store = useAbTestStore()
const { balance, isLow, fetchBalance } = useCredit()

const isTablet = useMediaQuery('(min-width: 768px)')

const {
  tests,
  videos,
  summary,
  activeTests,
  completedTests,
  activeTab,
  processing,
} = storeToRefs(store)

const tabs = computed(() => [
  { key: 'active' as const, label: t('abTest.tabActive'), icon: BeakerIcon },
  { key: 'completed' as const, label: t('abTest.tabCompleted'), icon: CheckCircleIcon },
  { key: 'create' as const, label: t('abTest.tabCreate'), icon: PlusIcon },
])

function handleSelectTest(testId: number) {
  const found = tests.value.find(item => item.id === testId)
  if (found) store.selectTest(found)
}

async function handleStartTest(testId: number) {
  await store.startTest(testId)
}

async function handlePauseTest(testId: number) {
  await store.pauseTest(testId)
}

async function handleApplyWinner(testId: number) {
  await store.applyWinner(testId)
}

async function handleCreateTest(data: {
  videoId: number
  type: AbTestType
  variants: { label: VariantLabel; value: string }[]
  durationHours: number
}) {
  await store.createTest(data.videoId, data.type, data.variants, data.durationHours)
  store.setActiveTab('active')
  await fetchBalance()
}

onMounted(() => {
  store.fetchTests()
  store.fetchVideos()
  store.fetchSummary()
  fetchBalance()
})
</script>
