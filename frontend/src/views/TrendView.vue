<template>
  <div class="max-w-6xl mx-auto space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900">트렌드 모니터링</h1>
      <button
        :disabled="analyzing"
        class="px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700 disabled:opacity-50"
        @click="runAnalysis"
      >
        {{ analyzing ? 'AI 분석 중...' : 'AI 트렌드 분석 (5 크레딧)' }}
      </button>
    </div>

    <!-- 탭 -->
    <div class="border-b border-gray-200">
      <nav class="flex gap-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            'pb-3 text-sm font-medium border-b-2 transition-colors',
            activeTab === tab.key
              ? 'border-indigo-600 text-indigo-600'
              : 'border-transparent text-gray-500 hover:text-gray-700',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- 트렌드 차트 탭 -->
    <div v-if="activeTab === 'trends'">
      <div class="mb-4 flex gap-3">
        <input
          v-model="searchKeyword"
          type="text"
          placeholder="키워드 검색..."
          class="px-3 py-2 border rounded-lg text-sm w-64"
          @keyup.enter="searchTrends"
        />
        <select v-model="sourceFilter" class="px-3 py-2 border rounded-lg text-sm" @change="loadTrends">
          <option value="">전체 소스</option>
          <option value="GOOGLE_TRENDS">Google Trends</option>
          <option value="YOUTUBE">YouTube</option>
          <option value="INTERNAL">내부</option>
        </select>
      </div>
      <TrendChart :trends="store.trends" />
    </div>

    <!-- AI 분석 탭 -->
    <div v-if="activeTab === 'analysis'">
      <div v-if="!store.analysis" class="text-center py-12 text-gray-400">
        AI 분석을 실행하면 결과가 여기에 표시됩니다.
      </div>
      <div v-else class="space-y-4">
        <div class="bg-white rounded-lg border p-6">
          <h3 class="text-lg font-semibold mb-3">분석 요약</h3>
          <p class="text-sm text-gray-700 whitespace-pre-line">{{ store.analysis.summary }}</p>
        </div>
        <div class="bg-white rounded-lg border p-6">
          <h3 class="text-lg font-semibold mb-3">콘텐츠 추천</h3>
          <ul class="space-y-2">
            <li v-for="(rec, i) in store.analysis.recommendations" :key="i" class="flex gap-2 text-sm text-gray-700">
              <span class="text-indigo-600 font-bold">{{ i + 1 }}.</span>
              {{ rec }}
            </li>
          </ul>
        </div>
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
import { useTrendStore } from '@/stores/trend'
import { useNotification } from '@/composables/useNotification'
import TrendChart from '@/components/trend/TrendChart.vue'
import TrendAlertManager from '@/components/trend/TrendAlertManager.vue'

const store = useTrendStore()
const notification = useNotification()

const activeTab = ref('trends')
const searchKeyword = ref('')
const sourceFilter = ref('')
const analyzing = ref(false)

const tabs = [
  { key: 'trends', label: '키워드 트렌드' },
  { key: 'analysis', label: 'AI 분석' },
  { key: 'alerts', label: '알림 관리' },
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
