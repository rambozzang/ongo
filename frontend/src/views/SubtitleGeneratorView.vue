<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  LanguageIcon,
  DocumentTextIcon,
  CheckCircleIcon,
  ArrowPathIcon,
  ChatBubbleBottomCenterTextIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import SubtitleJobCard from '@/components/subtitlegenerator/SubtitleJobCard.vue'
import SubtitleSegmentRow from '@/components/subtitlegenerator/SubtitleSegmentRow.vue'
import { useSubtitleGeneratorStore } from '@/stores/subtitleGenerator'
import type { SubtitleJob } from '@/types/subtitleGenerator'

const { t } = useI18n({ useScope: 'global' })
const store = useSubtitleGeneratorStore()

const selectedJobId = ref<number | null>(null)
const statusFilter = ref<SubtitleJob['status'] | 'ALL'>('ALL')

const selectedJob = computed(() =>
  store.jobs.find((j) => j.id === selectedJobId.value) ?? null,
)

const filteredJobs = computed(() => {
  if (statusFilter.value === 'ALL') return store.jobs
  return store.jobs.filter((j) => j.status === statusFilter.value)
})

const statusFilters: { key: SubtitleJob['status'] | 'ALL'; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'PENDING', label: '대기중' },
  { key: 'PROCESSING', label: '처리중' },
  { key: 'COMPLETED', label: '완료' },
  { key: 'FAILED', label: '실패' },
]

function handleSelectJob(id: number) {
  selectedJobId.value = selectedJobId.value === id ? null : id
  if (selectedJobId.value !== null) {
    store.fetchSegments(id)
  }
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

onMounted(async () => {
  await Promise.all([store.fetchJobs(), store.fetchSummary()])
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('subtitleGenerator.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('subtitleGenerator.description') }}
        </p>
      </div>
    </div>

    <PageGuide
      :title="$t('subtitleGenerator.pageGuideTitle')"
      :items="($tm('subtitleGenerator.pageGuide') as string[])"
    />

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" full-page />

    <template v-else>
      <!-- Summary Stats -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-3 desktop:grid-cols-5">
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <DocumentTextIcon class="h-5 w-5 text-blue-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.totalJobs') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalJobs }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <CheckCircleIcon class="h-5 w-5 text-green-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.completedJobs') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-green-600 dark:text-green-400">
            {{ store.summary.completedJobs }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ArrowPathIcon class="h-5 w-5 text-yellow-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.avgAccuracy') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.avgAccuracy.toFixed(1) }}%
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <ChatBubbleBottomCenterTextIcon class="h-5 w-5 text-purple-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.totalWords') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(store.summary.totalWords) }}
          </p>
        </div>

        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <LanguageIcon class="h-5 w-5 text-indigo-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.recentLanguage') }}</p>
          </div>
          <p class="mt-1 text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.recentLanguage === 'ko' ? '한국어' : store.summary.recentLanguage }}
          </p>
        </div>
      </div>

      <!-- Status Filter -->
      <div class="mb-6 flex gap-2 border-b border-gray-200 dark:border-gray-700 pb-0">
        <button
          v-for="filter in statusFilters"
          :key="filter.key"
          class="border-b-2 px-4 py-3 text-sm font-medium transition-colors"
          :class="
            statusFilter === filter.key
              ? 'border-primary-600 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
          "
          @click="statusFilter = filter.key"
        >
          {{ filter.label }}
          <span
            v-if="filter.key === 'ALL'"
            class="ml-1 rounded-full bg-gray-100 px-1.5 py-0.5 text-xs dark:bg-gray-700"
          >
            {{ store.jobs.length }}
          </span>
        </button>
      </div>

      <!-- Main content: Job list + Segment panel -->
      <div class="flex flex-col gap-6 desktop:flex-row">
        <!-- Job List -->
        <div class="flex-1">
          <div v-if="filteredJobs.length > 0" class="grid gap-4 tablet:grid-cols-2">
            <SubtitleJobCard
              v-for="job in filteredJobs"
              :key="job.id"
              :job="job"
              :selected="selectedJobId === job.id"
              @select="handleSelectJob"
            />
          </div>

          <!-- Empty state -->
          <div
            v-else
            class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
          >
            <LanguageIcon class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
            <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleGenerator.noJobs') }}</p>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('subtitleGenerator.noJobsDesc') }}</p>
          </div>
        </div>

        <!-- Segment Panel -->
        <div
          v-if="selectedJob && selectedJob.status === 'COMPLETED'"
          class="w-full flex-shrink-0 desktop:w-[420px]"
        >
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="mb-4 flex items-center justify-between">
              <h2 class="text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('subtitleGenerator.segments') }}
              </h2>
              <button
                class="text-xs text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
                @click="selectedJobId = null"
              >
                {{ $t('subtitleGenerator.closePanel') }}
              </button>
            </div>

            <p class="mb-3 text-xs text-gray-500 dark:text-gray-400">
              {{ store.segments.length }}{{ $t('subtitleGenerator.segmentUnit') }}
            </p>

            <div v-if="store.segments.length > 0" class="space-y-2 max-h-[600px] overflow-y-auto">
              <SubtitleSegmentRow
                v-for="seg in store.segments"
                :key="seg.id"
                :segment="seg"
              />
            </div>

            <div v-else class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
              {{ $t('subtitleGenerator.noSegments') }}
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
