<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
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
    <div v-if="activeTab === 'trends'" class="space-y-4">
      <SectionCard :title="$t('trend.tabTrends')" :meta="store.trends.length ? String(store.trends.length) : undefined">
        <div class="flex flex-wrap gap-3">
        <input
          v-model="searchKeyword"
          type="text"
          :placeholder="$t('trend.searchPlaceholder')"
          class="input-field w-full sm:w-64"
          @keyup.enter="searchTrends"
        />
        <select v-model="sourceFilter" class="input-field w-full sm:w-auto" @change="loadTrends">
          <option value="">{{ $t('trend.allSources') }}</option>
          <option value="GOOGLE_TRENDS">Google Trends</option>
          <option value="YOUTUBE">YouTube</option>
          <option value="INTERNAL">{{ $t('trend.internal') }}</option>
        </select>
        </div>
      </SectionCard>
      <TrendChart :trends="store.trends" />
    </div>

    <!-- AI 분석 탭 -->
    <div v-if="activeTab === 'analysis'" class="space-y-4">
      <SectionCard v-if="!store.analysis">
        <div class="py-12 text-center text-content-tertiary">
        {{ $t('trend.analysisEmpty') }}
        </div>
      </SectionCard>
      <div v-else class="space-y-4">
        <SectionCard :title="$t('trend.analysisSummary')">
          <p class="whitespace-pre-line text-body text-content-secondary">{{ store.analysis.summary }}</p>
        </SectionCard>
        <SectionCard :title="$t('trend.contentRecommendations')">
          <ul class="space-y-2">
            <li v-for="(rec, i) in store.analysis.recommendations" :key="i" class="flex gap-2 text-body text-content-secondary">
              <span class="font-mono font-bold text-accent">{{ String(i + 1).padStart(2, '0') }}</span>
              {{ rec }}
            </li>
          </ul>
        </SectionCard>
      </div>
    </div>

    <!-- 알림 관리 탭 -->
    <div v-if="activeTab === 'alerts'">
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
import SectionCard from '@/components/redesign/SectionCard.vue'

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
