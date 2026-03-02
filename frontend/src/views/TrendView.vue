<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('trend.title')" :description="$t('trend.description')">
      <template #actions>
        <button
          :disabled="analyzing"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="runAnalysis"
        >
          {{ analyzing ? $t('trend.analyzing') : $t('trend.analyzeButton') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('trend.pageGuideTitle')" :items="($tm('trend.pageGuide') as string[])" />

    <!-- 탭 -->
    <OTabs v-model="activeTab" :tabs="tabs" />

    <!-- 트렌드 차트 탭 -->
    <div v-if="activeTab === 'trends'" class="mt-6">
      <div class="mb-4 flex gap-3">
        <input
          v-model="searchKeyword"
          type="text"
          :placeholder="$t('trend.searchPlaceholder')"
          class="input-field w-64"
          @keyup.enter="searchTrends"
        />
        <select v-model="sourceFilter" class="input-field" @change="loadTrends">
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
        <div class="card">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('trend.analysisSummary') }}</h3>
          <p class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-line">{{ store.analysis.summary }}</p>
        </div>
        <div class="card">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('trend.contentRecommendations') }}</h3>
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
import OTabs from '@/components/ui/OTabs.vue'
import TrendChart from '@/components/trend/TrendChart.vue'
import TrendAlertManager from '@/components/trend/TrendAlertManager.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'

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
