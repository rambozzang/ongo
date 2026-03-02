<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ChartBarSquareIcon,
  ArrowTrendingUpIcon,
  CheckBadgeIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OTabs from '@/components/ui/OTabs.vue'
import TrendPredictionCard from '@/components/trendpredictor/TrendPredictionCard.vue'
import TrendTopicRow from '@/components/trendpredictor/TrendTopicRow.vue'
import { useTrendPredictorStore } from '@/stores/trendPredictor'

const store = useTrendPredictorStore()

const selectedPredictionId = ref<number | null>(null)
const activeCategory = ref<string>('all')

onMounted(async () => {
  await Promise.all([store.fetchPredictions(), store.fetchSummary()])
})

/* ---- Categories ---- */
const categories = computed(() => {
  const cats = new Set(store.predictions.map(p => p.category))
  return ['all', ...Array.from(cats)]
})

const categoryLabel = (cat: string) => {
  return cat === 'all' ? '전체' : cat
}

const categoryTabs = computed(() =>
  categories.value.map(cat => ({
    key: cat,
    label: categoryLabel(cat),
    count: cat !== 'all' ? store.predictions.filter(p => p.category === cat).length : undefined,
  })),
)

/* ---- Filtered predictions ---- */
const filteredPredictions = computed(() => {
  if (activeCategory.value === 'all') return store.predictions
  return store.predictions.filter(p => p.category === activeCategory.value)
})

/* ---- Selected prediction topics ---- */
const selectedPrediction = computed(() =>
  store.predictions.find(p => p.id === selectedPredictionId.value) ?? null,
)

function handleSelect(id: number) {
  selectedPredictionId.value = id
  store.fetchTopics(id)
}

/* ---- Summary stats ---- */
const summaryCards = computed(() => {
  const s = store.summary
  if (!s) return []
  return [
    { label: '총 예측 수', value: s.totalPredictions.toLocaleString('ko-KR'), icon: ChartBarSquareIcon, color: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30' },
    { label: '상승 트렌드', value: `${s.risingTrends}개`, icon: ArrowTrendingUpIcon, color: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30' },
    { label: '정확도', value: `${s.accuracy}%`, icon: CheckBadgeIcon, color: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30' },
    { label: '상위 카테고리', value: s.topCategory, icon: TagIcon, color: 'text-orange-600 dark:text-orange-400 bg-orange-100 dark:bg-orange-900/30' },
  ]
})
</script>

<template>
  <div class="relative">
    <PageHeader title="AI 트렌드 예측기" description="AI가 분석한 트렌드 예측과 추천 토픽을 확인하세요" />

    <PageGuide
      title="트렌드 예측기 가이드"
      :items="[
        'AI가 분석한 키워드별 트렌드 방향과 점수를 확인할 수 있습니다.',
        '카테고리 탭으로 관심 분야의 트렌드를 필터링하세요.',
        '예측 카드를 클릭하면 관련 토픽과 세부 데이터를 볼 수 있습니다.',
        '신뢰도가 높을수록 예측 정확도가 높습니다.',
      ]"
    />

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <div v-else class="space-y-6 mt-6">
      <!-- Summary cards -->
      <div
        v-if="store.summary"
        class="grid grid-cols-2 tablet:grid-cols-4 gap-4"
      >
        <div
          v-for="card in summaryCards"
          :key="card.label"
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
        >
          <div class="flex items-center gap-3">
            <div
              :class="['flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center', card.color]"
            >
              <component :is="card.icon" class="w-5 h-5" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ card.label }}</p>
              <p class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ card.value }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Category filter tabs -->
      <OTabs v-model="activeCategory" :tabs="categoryTabs" class="mb-0" />

      <!-- Prediction cards grid -->
      <div v-if="filteredPredictions.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
        <TrendPredictionCard
          v-for="prediction in filteredPredictions"
          :key="prediction.id"
          :prediction="prediction"
          @select="handleSelect"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
      >
        <ChartBarSquareIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          예측 데이터가 없습니다
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">
          해당 카테고리의 트렌드 예측이 아직 생성되지 않았습니다.
        </p>
      </div>

      <!-- Selected prediction - topics -->
      <section v-if="selectedPrediction && store.topics.length > 0">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            "{{ selectedPrediction.keyword }}" 관련 토픽
          </h2>
          <button
            @click="selectedPredictionId = null"
            class="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
          >
            닫기
          </button>
        </div>

        <div class="space-y-3">
          <TrendTopicRow
            v-for="topic in store.topics"
            :key="topic.id"
            :topic="topic"
          />
        </div>
      </section>
    </div>
  </div>
</template>
