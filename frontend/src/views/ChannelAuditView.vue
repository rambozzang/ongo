<template>
  <div>
    <PageHeader :title="$t('channelAudit.title')" :description="$t('channelAudit.description')">
      <template #actions>
        <button
          :disabled="store.generating"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ store.generating ? $t('channelAudit.generating') : $t('channelAudit.generateButton') }}
        </button>
      </template>
    </PageHeader>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" />

    <!-- Empty -->
    <EmptyState
      v-else-if="store.reports.length === 0"
      :title="$t('channelAudit.emptyTitle')"
      :description="$t('channelAudit.emptyDescription')"
    >
      <template #action>
        <button
          :disabled="store.generating"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleGenerate"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ $t('channelAudit.generateButton') }}
        </button>
      </template>
    </EmptyState>

    <!-- Reports List -->
    <div v-else class="space-y-4">
      <div
        v-for="report in store.reports"
        :key="report.id"
        class="card cursor-pointer transition-colors hover:border-primary-300 dark:hover:border-primary-700"
        @click="openDetail(report)"
      >
          <div class="flex items-center justify-between gap-4">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-3 mb-2">
              <span class="text-h1 font-bold text-primary-600 dark:text-primary-400">
                {{ report.overallScore }}
              </span>
              <span class="text-body text-gray-500 dark:text-gray-400">/ 100</span>
              <span class="text-body-xs text-gray-400 dark:text-gray-500">
                {{ formatDate(report.createdAt) }}
              </span>
            </div>
            <p class="text-body text-gray-700 dark:text-gray-300 line-clamp-2">{{ report.growthForecast || 'AI 분석 결과가 저장되었습니다.' }}</p>
          </div>
          <ChevronRightIcon class="h-5 w-5 text-gray-400 shrink-0" />
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="store.totalPages > 1" class="flex items-center justify-center gap-3 pt-2">
        <button :disabled="!store.hasPrevPage" class="btn-secondary disabled:opacity-40" @click="store.prevPage()">
          {{ $t('action.prev') }}
        </button>
        <span class="text-body text-gray-500 dark:text-gray-400">
          {{ store.page + 1 }} / {{ store.totalPages }}
        </span>
        <button :disabled="!store.hasNextPage" class="btn-secondary disabled:opacity-40" @click="store.nextPage()">
          {{ $t('action.next') }}
        </button>
      </div>
    </div>

    <!-- Detail Modal -->
    <BaseModal
      v-model="detailModalOpen"
      :title="$t('channelAudit.detailTitle')"
      max-width="xl"
    >
      <div v-if="store.selectedReport" class="space-y-6">
        <!-- Score Summary -->
        <div class="grid grid-cols-2 gap-4">
          <div class="rounded-lg bg-primary-50 dark:bg-primary-900/20 p-4 text-center">
            <p class="text-display font-bold text-primary-600 dark:text-primary-400">
              {{ store.selectedReport.overallScore }}
            </p>
            <p class="text-body-xs text-gray-500 dark:text-gray-400 mt-1">{{ $t('channelAudit.overall') }}</p>
          </div>
          <div class="rounded-lg bg-gray-50 p-4 text-center dark:bg-gray-800/50">
            <p class="text-h1 font-bold text-gray-900 dark:text-white">{{ store.selectedReport.actionItems.length }}</p>
            <p class="mt-1 text-body-xs text-gray-500 dark:text-gray-400">{{ $t('channelAudit.actionItems') }}</p>
          </div>
        </div>

        <!-- Summary -->
        <div class="card">
          <h3 class="mb-2 text-body font-semibold text-gray-900 dark:text-white">{{ $t('channelAudit.summaryLabel') }}</h3>
          <p class="text-body text-gray-700 dark:text-gray-300 whitespace-pre-line">{{ store.selectedReport.growthForecast || 'AI 진단 요약이 없습니다.' }}</p>
        </div>

        <!-- Strengths & Weaknesses -->
        <div class="grid tablet:grid-cols-2 gap-4">
          <div class="card">
            <h3 class="mb-3 text-body font-semibold text-success-strong">{{ $t('channelAudit.strengths') }}</h3>
            <ul class="space-y-1.5">
              <li
                v-for="(s, i) in store.selectedReport.strengths"
                :key="i"
                class="flex gap-2 text-body text-gray-700 dark:text-gray-300"
              >
                <CheckCircleIcon class="h-4 w-4 text-success-strong shrink-0 mt-0.5" />
                {{ s }}
              </li>
            </ul>
          </div>
          <div class="card">
            <h3 class="mb-3 text-body font-semibold text-error-strong">{{ $t('channelAudit.weaknesses') }}</h3>
            <ul class="space-y-1.5">
              <li
                v-for="(w, i) in store.selectedReport.weaknesses"
                :key="i"
                class="flex gap-2 text-body text-gray-700 dark:text-gray-300"
              >
                <ExclamationCircleIcon class="h-4 w-4 text-error-strong shrink-0 mt-0.5" />
                {{ w }}
              </li>
            </ul>
          </div>
        </div>

        <!-- Action Items -->
        <div class="card">
          <h3 class="mb-3 text-body font-semibold text-gray-900 dark:text-white">{{ $t('channelAudit.actionItems') }}</h3>
          <div class="space-y-3">
            <div
              v-for="(item, i) in store.selectedReport.actionItems"
              :key="i"
              class="flex gap-3 rounded-lg border p-3"
              :class="{
                'border-error bg-error-subtle': item.priority === 'HIGH',
                'border-warning bg-warning-subtle': item.priority === 'MEDIUM',
                'border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50': item.priority === 'LOW',
              }"
            >
              <span
                class="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider h-fit mt-0.5"
                :class="{
                  'bg-error-subtle text-error-strong': item.priority === 'HIGH',
                  'bg-warning-subtle text-warning-strong': item.priority === 'MEDIUM',
                  'bg-gray-200 text-gray-600 dark:bg-gray-700 dark:text-gray-400': item.priority === 'LOW',
                }"
              >
                {{ item.priority }}
              </span>
              <div>
                <p class="text-body font-medium text-gray-900 dark:text-white">{{ item.action }}</p>
                <p class="mt-0.5 text-body-xs text-gray-600 dark:text-gray-400">{{ item.expectedImpact }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Outlier Videos -->
        <div v-if="store.selectedReport.outlierVideos.length > 0" class="card">
          <h3 class="mb-3 text-body font-semibold text-gray-900 dark:text-white">{{ $t('channelAudit.outlierVideos') }}</h3>
          <div class="space-y-3">
            <div
              v-for="video in store.selectedReport.outlierVideos"
              :key="`${video.videoTitle}-${video.metric}`"
              class="flex gap-3 items-start"
            >
              <div class="min-w-0 flex-1">
                <p class="truncate text-body font-medium text-gray-900 dark:text-white">{{ video.videoTitle }}</p>
                <p class="mt-0.5 text-body-xs text-gray-500 dark:text-gray-400">{{ video.metric }}</p>
                <p class="text-body-xs text-gray-500 dark:text-gray-400 mt-0.5">{{ video.reason }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <button class="btn-secondary" @click="detailModalOpen = false">
          {{ $t('action.close') }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  SparklesIcon,
  ChevronRightIcon,
  CheckCircleIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import { useChannelAuditStore } from '@/stores/channelAudit'
import type { ChannelAuditReport } from '@/types/channelAudit'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import BaseModal from '@/components/common/BaseModal.vue'

const store = useChannelAuditStore()
const detailModalOpen = ref(false)

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

function openDetail(report: ChannelAuditReport) {
  store.selectedReport = report
  detailModalOpen.value = true
}

async function handleGenerate() {
  await store.generateAudit()
  if (store.selectedReport) {
    detailModalOpen.value = true
  }
}

onMounted(() => {
  store.fetchReports()
})
</script>
