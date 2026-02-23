<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('trend.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('trend.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          :disabled="analyzing"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="runAnalysis"
        >
          {{ analyzing ? $t('trend.analyzing') : $t('trend.analyzeButton') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('trend.pageGuideTitle')" :items="($tm('trend.pageGuide') as string[])" />

    <!-- 탭 -->
    <div class="border-b border-gray-200 dark:border-gray-700">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-primary-600 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 트렌드 차트 탭 -->
    <div v-if="activeTab === 'trends'" class="mt-6">
      <div class="mb-4 flex gap-3">
        <input
          v-model="searchKeyword"
          type="text"
          :placeholder="$t('trend.searchPlaceholder')"
          class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm w-64 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100"
          @keyup.enter="searchTrends"
        />
        <select v-model="sourceFilter" class="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg text-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100" @change="loadTrends">
          <option value="">{{ $t('trend.allSources') }}</option>
          <option value="GOOGLE_TRENDS">Google Trends</option>
          <option value="YOUTUBE">YouTube</option>
          <option value="INTERNAL">{{ $t('trend.internal') }}</option>
        </select>
      </div>
      <TrendChart :trends="store.trends" />
    </div>

    <!-- AI 분석 탭 -->
    <div v-if="activeTab === 'analysis'" class="mt-6">
      <div v-if="!store.analysis" class="text-center py-12 text-gray-400 dark:text-gray-500">
        {{ $t('trend.analysisEmpty') }}
      </div>
      <div v-else class="space-y-4">
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <h3 class="text-lg font-semibold mb-3 text-gray-900 dark:text-gray-100">{{ $t('trend.analysisSummary') }}</h3>
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-line">{{ store.analysis.summary }}</p>
        </div>
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-6">
          <h3 class="text-lg font-semibold mb-3 text-gray-900 dark:text-gray-100">{{ $t('trend.contentRecommendations') }}</h3>
          <ul class="space-y-2">
            <li v-for="(rec, i) in store.analysis.recommendations" :key="i" class="flex gap-2 text-sm text-gray-700 dark:text-gray-300">
              <span class="text-primary-600 dark:text-primary-400 font-bold">{{ i + 1 }}.</span>
              {{ rec }}
            </li>
          </ul>
        </div>
      </div>
    </div>

    <!-- 알림 관리 탭 -->
    <div v-if="activeTab === 'alerts'" class="mt-6">
      <TrendAlertManager :alerts="store.alerts" @refresh="store.loadAlerts()" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTrendStore } from '@/stores/trend'
import { useNotification } from '@/composables/useNotification'
import TrendChart from '@/components/trend/TrendChart.vue'
import TrendAlertManager from '@/components/trend/TrendAlertManager.vue'
import PageGuide from '@/components/common/PageGuide.vue'

const { t } = useI18n()
const store = useTrendStore()
const notification = useNotification()

const activeTab = ref('trends')
const searchKeyword = ref('')
const sourceFilter = ref('')
const analyzing = ref(false)

const tabs = [
  { key: 'trends', label: t('trend.tabTrends') },
  { key: 'analysis', label: t('trend.tabAnalysis') },
  { key: 'alerts', label: t('trend.tabAlerts') },
]

async function loadTrends() {
  await store.loadTrends({ source: sourceFilter.value || undefined })
}

async function searchTrends() {
  if (searchKeyword.value.trim()) {
    const { trendApi } = await import('@/api/trend')
    store.trends = await trendApi.search(searchKeyword.value)
  } else {
    await loadTrends()
  }
}

async function runAnalysis() {
  analyzing.value = true
  try {
    await store.loadAnalysis()
    activeTab.value = 'analysis'
  } catch (e) {
    console.error('AI 분석 실패:', e)
    notification.error('AI 트렌드 분석에 실패했습니다')
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadTrends(), store.loadAlerts()])
})
</script>
