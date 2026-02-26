<script setup lang="ts">
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  BanknotesIcon,
  ArrowTrendingUpIcon,
  StarIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import { useRevenueAnalyzerStore } from '@/stores/revenueAnalyzer'
import RevenueStreamCard from '@/components/revenueanalyzer/RevenueStreamCard.vue'
import ProjectionRow from '@/components/revenueanalyzer/ProjectionRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useRevenueAnalyzerStore()
const { streams, projections, summary, loading } = storeToRefs(store)

const sourceLabels: Record<string, string> = {
  AD: '광고',
  MEMBERSHIP: '멤버십',
  SUPERCHAT: '슈퍼챗',
  SPONSORSHIP: '협찬',
  MERCHANDISE: '굿즈',
  AFFILIATE: '제휴',
}

const formatKRW = (amount: number) => {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(amount)
}

onMounted(() => {
  store.fetchStreams()
  store.fetchProjections(1)
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          수익 분석기
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          채널별 수익 현황 분석 및 예측
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- Summary Cards -->
      <div v-if="summary" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <!-- Total Revenue -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
              <BanknotesIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">총 수익</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ formatKRW(summary.totalRevenue) }}
              </p>
            </div>
          </div>
        </div>

        <!-- Monthly Growth -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <ArrowTrendingUpIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">월 성장률</p>
              <p class="text-xl font-bold" :class="summary.monthlyGrowth >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'">
                {{ summary.monthlyGrowth > 0 ? '+' : '' }}{{ summary.monthlyGrowth }}%
              </p>
            </div>
          </div>
        </div>

        <!-- Top Source -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <StarIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">주 수익원</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ sourceLabels[summary.topSource] ?? summary.topSource }}
              </p>
            </div>
          </div>
        </div>

        <!-- Next Month Projection -->
        <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <ChartBarIcon class="h-5 w-5 text-orange-600 dark:text-orange-400" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">다음달 예측</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ formatKRW(summary.projectedNextMonth) }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Revenue Streams Grid -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          수익 스트림
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ streams.length }})</span>
        </h2>

        <div v-if="streams.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <RevenueStreamCard
            v-for="stream in streams"
            :key="stream.id"
            :stream="stream"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <BanknotesIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            수익 데이터가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            채널을 연결하여 수익 분석을 시작하세요
          </p>
        </div>
      </section>

      <!-- Projections Table -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          수익 예측
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ projections.length }})</span>
        </h2>

        <div v-if="projections.length > 0" class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead>
                <tr class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    소스
                  </th>
                  <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    현재 월수익
                  </th>
                  <th class="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    예측 월수익
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    신뢰도
                  </th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">
                    요인
                  </th>
                </tr>
              </thead>
              <tbody>
                <ProjectionRow
                  v-for="projection in projections"
                  :key="projection.id"
                  :projection="projection"
                />
              </tbody>
            </table>
          </div>
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">
            예측 데이터가 없습니다
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
