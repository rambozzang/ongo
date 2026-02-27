<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ArrowPathRoundedSquareIcon,
  ClockIcon,
  CheckCircleIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import { useContentRepurposerStore } from '@/stores/contentRepurposer'
import RepurposeJobCard from '@/components/contentrepurposer/RepurposeJobCard.vue'
import RepurposeTemplateCard from '@/components/contentrepurposer/RepurposeTemplateCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useLocale } from '@/composables/useLocale'

const { t } = useLocale()

const store = useContentRepurposerStore()
const { jobs, templates, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'jobs' | 'templates'>('jobs')

const handleUseTemplate = (id: number) => {
  // 향후 리퍼포징 작업 생성 모달 연동
  console.log('Use template:', id)
}

onMounted(() => {
  store.fetchJobs()
  store.fetchTemplates()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentRepurposer.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentRepurposer.description') }}
        </p>
      </div>
    </div>

    <!-- Summary Cards -->
    <div class="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
      <!-- 총 작업 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
            <ArrowPathRoundedSquareIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentRepurposer.totalJobs') }}</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalJobs }}</p>
          </div>
        </div>
      </div>

      <!-- 완료 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <CheckCircleIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentRepurposer.completed') }}</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.completedJobs }}</p>
          </div>
        </div>
      </div>

      <!-- 평균 시간 절약 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
            <ClockIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentRepurposer.avgTimeSaved') }}</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.avgTimeSaved }}{{ $t('contentRepurposer.minutes') }}</p>
          </div>
        </div>
      </div>

      <!-- 성공률 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 dark:bg-amber-900/30">
            <ChartBarIcon class="h-5 w-5 text-amber-600 dark:text-amber-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('contentRepurposer.successRate') }}</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.successRate }}%</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-8">
        <button
          @click="activeTab = 'jobs'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'jobs'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('contentRepurposer.jobList') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'jobs'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ jobs.length }}
          </span>
        </button>

        <button
          @click="activeTab = 'templates'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'templates'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ $t('contentRepurposer.conversionTemplates') }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'templates'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ templates.length }}
          </span>
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Jobs Tab -->
    <div v-else-if="activeTab === 'jobs'">
      <div v-if="jobs.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        <RepurposeJobCard
          v-for="job in jobs"
          :key="job.id"
          :job="job"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
      >
        <ArrowPathRoundedSquareIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentRepurposer.noJobs') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentRepurposer.noJobsHint') }}
        </p>
      </div>
    </div>

    <!-- Templates Tab -->
    <div v-else-if="activeTab === 'templates'">
      <div v-if="templates.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
        <RepurposeTemplateCard
          v-for="tpl in templates"
          :key="tpl.id"
          :template="tpl"
          @use="handleUseTemplate"
        />
      </div>

      <!-- Empty state -->
      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
      >
        <ArrowPathRoundedSquareIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentRepurposer.noTemplates') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentRepurposer.noTemplatesHint') }}
        </p>
      </div>
    </div>
  </div>
</template>
