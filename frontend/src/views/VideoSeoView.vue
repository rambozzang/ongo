<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  MagnifyingGlassIcon,
  ChartBarSquareIcon,
  ArrowTrendingUpIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import { useVideoSeoStore } from '@/stores/videoSeo'
import SeoAnalysisCard from '@/components/videoseo/SeoAnalysisCard.vue'
import SeoKeywordRow from '@/components/videoseo/SeoKeywordRow.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useVideoSeoStore()
const { analyses, keywords, summary, isLoading } = storeToRefs(store)

const selectedAnalysisId = ref<number | null>(null)

const handleSelectAnalysis = (id: number) => {
  selectedAnalysisId.value = id
  store.fetchKeywords(id)
}

onMounted(() => {
  store.fetchAnalyses()
  store.fetchSummary()
  // 기본적으로 첫 번째 분석의 키워드 로드
  store.fetchKeywords(1)
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          비디오 SEO 최적화
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          영상의 SEO 점수를 분석하고 검색 노출을 개선합니다.
        </p>
      </div>
    </div>

    <!-- Summary Cards -->
    <div class="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
      <!-- 총 분석 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 dark:bg-blue-900/30">
            <MagnifyingGlassIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 분석</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.totalAnalyzed }}</p>
          </div>
        </div>
      </div>

      <!-- 평균 점수 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
            <ChartBarSquareIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 점수</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">{{ summary.avgScore.toFixed(1) }}</p>
          </div>
        </div>
      </div>

      <!-- 탑 키워드 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
            <TagIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">탑 키워드</p>
            <p class="text-lg font-bold text-gray-900 dark:text-gray-100 truncate">{{ summary.topKeyword }}</p>
          </div>
        </div>
      </div>

      <!-- 개선율 -->
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-3">
          <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 dark:bg-amber-900/30">
            <ArrowTrendingUpIcon class="h-5 w-5 text-amber-600 dark:text-amber-400" />
          </div>
          <div>
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">개선율</p>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">+{{ summary.improvementRate }}%</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <div v-else class="space-y-8">
      <!-- SEO Analysis List -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          SEO 분석 목록
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ analyses.length }})</span>
        </h2>

        <div v-if="analyses.length > 0" class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          <SeoAnalysisCard
            v-for="analysis in analyses"
            :key="analysis.id"
            :analysis="analysis"
            @select="handleSelectAnalysis"
          />
        </div>

        <!-- Empty state -->
        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
        >
          <MagnifyingGlassIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            SEO 분석 결과가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            영상을 선택하여 SEO 분석을 시작해보세요.
          </p>
        </div>
      </section>

      <!-- Keyword Table -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          키워드 분석
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ keywords.length }})</span>
        </h2>

        <div v-if="keywords.length > 0" class="overflow-hidden rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm">
          <div class="overflow-x-auto">
            <table class="w-full min-w-[600px]">
              <thead>
                <tr class="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                  <th class="py-3 px-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider w-10">#</th>
                  <th class="py-3 px-4 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">키워드</th>
                  <th class="py-3 px-4 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">검색량</th>
                  <th class="py-3 px-4 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">경쟁</th>
                  <th class="py-3 px-4 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">관련도</th>
                  <th class="py-3 px-4 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">트렌드</th>
                </tr>
              </thead>
              <tbody>
                <SeoKeywordRow
                  v-for="(kw, index) in keywords"
                  :key="kw.id"
                  :keyword="kw"
                  :rank="index + 1"
                />
              </tbody>
            </table>
          </div>
        </div>

        <!-- Empty state -->
        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-12 text-center shadow-sm"
        >
          <TagIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
            키워드 데이터가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            SEO 분석 카드를 클릭하면 관련 키워드를 확인할 수 있습니다.
          </p>
        </div>
      </section>
    </div>
  </div>
</template>
