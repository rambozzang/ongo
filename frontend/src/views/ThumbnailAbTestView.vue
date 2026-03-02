<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  BeakerIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import { useThumbnailAbTestStore } from '@/stores/thumbnailAbTest'
import ThumbnailTestCard from '@/components/thumbnailabtest/ThumbnailTestCard.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

useI18n({ useScope: 'global' })
const store = useThumbnailAbTestStore()

const { tests, summary, loading } = storeToRefs(store)

// ─── Filters ──────────────────────────────────────
const statusFilter = ref<string>('')
const platformFilter = ref<string>('')

const filteredTests = computed(() => {
  return tests.value.filter((test) => {
    if (statusFilter.value && test.status !== statusFilter.value) return false
    if (platformFilter.value && test.platform !== platformFilter.value) return false
    return true
  })
})

onMounted(() => {
  store.fetchTests()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('thumbnailAbTest.title')" :description="$t('thumbnailAbTest.description')">
      <template #title-suffix>
        <span
          v-if="store.activeCount > 0"
          class="rounded-full bg-green-600 px-2.5 py-0.5 text-xs font-semibold text-white"
        >
          {{ store.activeCount }} Active
        </span>
      </template>
    </PageHeader>

    <PageGuide
      :title="$t('thumbnailAbTest.pageGuideTitle')"
      :items="($tm('thumbnailAbTest.pageGuide') as string[])"
    />

    <!-- Summary Stats -->
    <div v-if="summary" class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="card">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('thumbnailAbTest.totalTests') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalTests }}
        </p>
      </div>

      <div class="card">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('thumbnailAbTest.activeTests') }}</p>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.activeTests }}
        </p>
      </div>

      <div class="card">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('thumbnailAbTest.avgCtrImprovement') }}</p>
        <p class="mt-1 text-2xl font-bold text-primary-600 dark:text-primary-400">
          +{{ summary.avgCtrImprovement.toFixed(1) }}%
        </p>
      </div>

      <div class="card">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('thumbnailAbTest.bestVariantWinRate') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.bestVariantWinRate.toFixed(1) }}%
        </p>
      </div>

      <div class="card">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('thumbnailAbTest.topPlatform') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.topPlatform }}
        </p>
      </div>
    </div>

    <!-- Filters -->
    <div class="mb-4 flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <div class="flex items-center gap-2">
        <FunnelIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        <select
          v-model="statusFilter"
          class="input-field"
        >
          <option value="">{{ $t('thumbnailAbTest.allStatuses') }}</option>
          <option value="ACTIVE">Active</option>
          <option value="COMPLETED">Completed</option>
          <option value="PENDING">Pending</option>
        </select>

        <select
          v-model="platformFilter"
          class="input-field"
        >
          <option value="">{{ $t('thumbnailAbTest.allPlatforms') }}</option>
          <option value="youtube">YouTube</option>
          <option value="tiktok">TikTok</option>
          <option value="instagram">Instagram</option>
          <option value="naverclip">Naver Clip</option>
        </select>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <!-- Test Cards -->
    <div v-else-if="filteredTests.length > 0" class="grid grid-cols-1 gap-4 desktop:grid-cols-2">
      <ThumbnailTestCard
        v-for="test in filteredTests"
        :key="test.id"
        :test="test"
      />
    </div>

    <!-- Empty state -->
    <div
      v-else
      class="card py-16 text-center"
    >
      <BeakerIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('thumbnailAbTest.emptyTests') }}
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('thumbnailAbTest.emptyTestsHint') }}
      </p>
    </div>
  </div>
</template>
