<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  ChartPieIcon,
  UsersIcon,
  ArrowsRightLeftIcon,
  UserGroupIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OverlapResultCard from '@/components/audienceoverlap/OverlapResultCard.vue'
import OverlapVennDiagram from '@/components/audienceoverlap/OverlapVennDiagram.vue'
import OverlapSegmentRow from '@/components/audienceoverlap/OverlapSegmentRow.vue'
import { useAudienceOverlapStore } from '@/stores/audienceOverlap'
import type { AudienceOverlapResult } from '@/types/audienceOverlap'

const store = useAudienceOverlapStore()

const selectedResult = ref<AudienceOverlapResult | null>(null)

onMounted(async () => {
  await Promise.all([
    store.fetchResults(),
    store.fetchSegments(),
    store.fetchSummary(),
  ])
})

function handleSelectResult(result: AudienceOverlapResult) {
  selectedResult.value = selectedResult.value?.id === result.id ? null : result
}

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          오디언스 오버랩
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          플랫폼 간 오디언스 중복을 분석하여 크로스 프로모션 전략을 수립하세요
        </p>
      </div>
    </div>

    <PageGuide
      title="오디언스 오버랩 활용 가이드"
      :items="[
        '플랫폼 간 오디언스 중복률을 확인하여 효과적인 크로스 프로모션 전략을 수립하세요.',
        '오버랩이 낮은 플랫폼 조합에 집중하면 새로운 오디언스를 더 많이 확보할 수 있습니다.',
        '세그먼트별 참여율을 비교하여 각 플랫폼에 적합한 콘텐츠 전략을 세우세요.',
      ]"
    />

    <!-- Loading -->
    <div v-if="store.isLoading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        <!-- Total Analyses -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <ChartPieIcon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 분석</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.totalAnalyses }}건
          </p>
        </div>

        <!-- Average Overlap -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-yellow-100 dark:bg-yellow-900/30">
              <ArrowsRightLeftIcon class="w-4 h-4 text-yellow-600 dark:text-yellow-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 오버랩</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.avgOverlap.toFixed(1) }}%
          </p>
        </div>

        <!-- Max Overlap Pair -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <UsersIcon class="w-4 h-4 text-purple-600 dark:text-purple-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">최대 오버랩 쌍</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ store.summary.maxOverlapPair }}
          </p>
        </div>

        <!-- Total Unique Audience -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
              <UserGroupIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 유니크 오디언스</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatNumber(store.summary.totalUniqueAudience) }}
          </p>
        </div>
      </div>

      <!-- Overlap Results Grid -->
      <section class="mb-8">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          오버랩 결과
          <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ store.results.length }})</span>
        </h2>

        <div v-if="store.results.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            v-for="result in store.results"
            :key="result.id"
            class="cursor-pointer"
            @click="handleSelectResult(result)"
          >
            <OverlapResultCard
              :result="result"
              :class="{ 'ring-2 ring-primary-500 dark:ring-primary-400 rounded-xl': selectedResult?.id === result.id }"
            />
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
        >
          <ArrowsRightLeftIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
          <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
            아직 분석 결과가 없습니다
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            플랫폼 간 오디언스 오버랩 분석을 시작해보세요.
          </p>
        </div>
      </section>

      <!-- Venn Diagram for Selected Result -->
      <section v-if="selectedResult" class="mb-8">
        <OverlapVennDiagram :result="selectedResult" />
      </section>

      <!-- Segment List -->
      <section>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          오디언스 세그먼트
          <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ store.segments.length }})</span>
        </h2>

        <div
          v-if="store.segments.length > 0"
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm overflow-hidden"
        >
          <!-- Table header -->
          <div class="hidden tablet:flex items-center gap-4 px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">
            <div class="flex-1">세그먼트</div>
            <div class="w-48">플랫폼</div>
            <div class="w-20 text-right">사이즈</div>
            <div class="w-16 text-right">참여율</div>
          </div>

          <!-- Rows -->
          <div class="divide-y divide-gray-200 dark:divide-gray-700">
            <OverlapSegmentRow
              v-for="segment in store.segments"
              :key="segment.id"
              :segment="segment"
            />
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-8 text-center shadow-sm"
        >
          <UserGroupIcon class="w-10 h-10 text-gray-400 dark:text-gray-500 mx-auto mb-2" />
          <p class="text-sm text-gray-500 dark:text-gray-400">세그먼트 데이터가 없습니다.</p>
        </div>
      </section>
    </template>
  </div>
</template>
