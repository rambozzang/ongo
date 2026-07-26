<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <div>
      <h1 class="text-title font-bold text-gray-900 dark:text-gray-100">
        {{ $t('abTest.title') }}
      </h1>
      <p class="mt-0.5 text-body-xs text-gray-500 dark:text-gray-400">
        {{ $t('abTest.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('abTest.pageGuideTitle')"
      :items="($tm('abTest.pageGuideMobile') as string[])"
    />

    <!-- Credit Display -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-body-xs"
      :class="isLow
        ? 'border-error bg-error-subtle'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-error-strong' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-error-strong' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Summary Cards (Mobile) -->
    <div class="grid grid-cols-2 gap-3">
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
        <div class="mt-1 text-h2 font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
        <div class="mt-1 text-h2 font-bold text-info-strong">{{ activeTests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
        <div class="mt-1 text-h2 font-bold text-success-strong">{{ completedTests.length }}</div>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800">
        <div class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
        <div class="mt-1 text-h2 font-bold text-success-strong">+{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%</div>
      </div>
    </div>

    <!-- Tab Navigation (Mobile) -->
    <OTabs v-model="activeTab" :tabs="tabs" class="mb-4" />

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
        <p class="mt-3 text-body text-gray-500 dark:text-gray-400">{{ $t('abTest.emptyActiveDesc') }}</p>
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
        <p class="mt-3 text-body text-gray-500 dark:text-gray-400">{{ $t('abTest.emptyCompletedDesc') }}</p>
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
    <PageHeader :title="$t('abTest.title')" :description="$t('abTest.description')">
      <template #actions>
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-body"
          :class="isLow
            ? 'border-error bg-error-subtle'
            : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-error-strong' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('abTest.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-error-strong' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </template>
    </PageHeader>

    <PageGuide
      :title="$t('abTest.pageGuideTitle')"
      :items="($tm('abTest.pageGuide') as string[])"
    />

    <!-- Summary Cards (Desktop) -->
    <div class="page-grid page-grid--metrics mb-8">
      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.totalTests') }}</div>
            <div class="text-display font-bold text-gray-900 dark:text-white">{{ summary?.totalTests ?? tests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
            <BeakerIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.activeTests') }}</div>
            <div class="text-display font-bold text-info-strong">{{ activeTests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-info-subtle">
            <div class="h-3 w-3 animate-pulse rounded-full bg-info"></div>
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.completedTests') }}</div>
            <div class="text-display font-bold text-success-strong">{{ completedTests.length }}</div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-success-subtle">
            <CheckCircleIcon class="h-6 w-6 text-success-strong" />
          </div>
        </div>
      </div>

      <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center justify-between">
          <div>
            <div class="mb-1 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.avgCtrImprovement') }}</div>
            <div class="text-display font-bold text-success-strong">
              +{{ (summary?.avgCtrImprovement ?? 0).toFixed(1) }}%
            </div>
          </div>
          <div class="flex h-12 w-12 items-center justify-center rounded-lg bg-success-subtle">
            <ArrowTrendingUpIcon class="h-6 w-6 text-success-strong" />
          </div>
        </div>
      </div>
    </div>

    <!-- Tab Navigation (Desktop) -->
    <OTabs v-model="activeTab" :tabs="tabs" class="mb-6" />

    <!-- Tab Content (Desktop) -->
    <div class="mt-6">
      <!-- Active Tests -->
      <div v-if="activeTab === 'active'">
        <div v-if="activeTests.length > 0" class="page-grid page-grid--split">
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
          <h3 class="mb-2 text-title font-medium text-gray-900 dark:text-gray-100">{{ $t('abTest.emptyTitle') }}</h3>
          <p class="mb-6 text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.emptyActiveDesc') }}</p>
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
        <div v-if="completedTests.length > 0" class="page-grid page-grid--split">
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
          <h3 class="mb-2 text-title font-medium text-gray-900 dark:text-gray-100">{{ $t('abTest.emptyTitle') }}</h3>
          <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('abTest.emptyCompletedDesc') }}</p>
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
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'
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
