<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  DocumentDuplicateIcon,
  ClipboardDocumentListIcon,
  CalculatorIcon,
  PencilSquareIcon,
} from '@heroicons/vue/24/outline'
import { useContentVersioningStore } from '@/stores/contentVersioning'
import VersionGroupCard from '@/components/contentversioning/VersionGroupCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useContentVersioningStore()
const { groups, summary, isLoading } = storeToRefs(store)

onMounted(() => {
  store.fetchGroups()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            콘텐츠 버전 관리
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠의 변경 이력을 추적하고 각 변경이 성과에 미친 영향을 분석하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <DocumentDuplicateIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 콘텐츠</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalContents.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ClipboardDocumentListIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 버전</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalVersions.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CalculatorIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 버전수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgVersionsPerContent.toFixed(1) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <PencilSquareIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">가장 많이 편집된 콘텐츠</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.mostEditedContent || '-' }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Version Group List -->
    <div v-else>
      <div v-if="groups.length > 0" class="space-y-4">
        <VersionGroupCard
          v-for="g in groups"
          :key="g.contentId"
          :group="g"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <DocumentDuplicateIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          버전 이력이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠를 수정하면 버전 이력이 자동으로 기록됩니다
        </p>
      </div>
    </div>
  </div>
</template>
