<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  MusicalNoteIcon,
  CheckCircleIcon,
  SparklesIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import RecommendationCard from '@/components/musicrecommender/RecommendationCard.vue'
import MusicTrackRow from '@/components/musicrecommender/MusicTrackRow.vue'
import MoodSelector from '@/components/musicrecommender/MoodSelector.vue'
import { useMusicRecommenderStore } from '@/stores/musicRecommender'
import type { MusicRecommendation } from '@/types/musicRecommender'

const store = useMusicRecommenderStore()

const selectedRecommendation = ref<MusicRecommendation | null>(null)

const moods = ['밝은', '편안한', '집중', '에너지', '차분한', '드라마틱', '활기찬', '감성적', '로맨틱', '웅장한']

onMounted(() => {
  store.fetchRecommendations()
  store.fetchSummary()
})

/* ---- Summary stats ---- */
const totalRecommendations = computed(() => store.summary?.totalRecommendations ?? 0)
const appliedCount = computed(() => store.summary?.appliedTracks ?? 0)
const popularGenre = computed(() => store.summary?.topGenre ?? '-')
const avgMatch = computed(() => store.summary?.avgMatchScore ?? 0)

/* ---- Handlers ---- */
function handleSelectRecommendation(id: number) {
  const rec = store.recommendations.find((r) => r.id === id)
  if (rec) {
    selectedRecommendation.value = rec
  }
}

function handleMoodSelect(_mood: string) {
  // Mood filtering can be extended later
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          AI 음악 추천
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          영상에 어울리는 배경음악을 AI가 자동으로 추천합니다
        </p>
      </div>
    </div>

    <PageGuide
      title="AI 음악 추천 가이드"
      :items="[
        '영상 분위기에 맞는 배경음악을 AI가 자동으로 추천합니다',
        '무드 필터로 원하는 분위기의 트랙을 검색할 수 있습니다',
        '매치 점수가 높을수록 영상과 잘 어울리는 트랙입니다',
        '라이센스 유형(Free/Standard/Premium)을 확인 후 적용하세요',
      ]"
    />

    <!-- Loading -->
    <div v-if="store.loading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- Summary Cards -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Recommendations -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
              <MusicalNoteIcon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 추천</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ totalRecommendations }}건
          </p>
        </div>

        <!-- Applied -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">적용됨</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ appliedCount }}건
          </p>
        </div>

        <!-- Popular Genre -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
              <SparklesIcon class="w-4 h-4 text-purple-600 dark:text-purple-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">인기 장르</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ popularGenre }}
          </p>
        </div>

        <!-- Average Match -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2 mb-2">
            <div class="p-2 rounded-lg bg-orange-100 dark:bg-orange-900/30">
              <ChartBarIcon class="w-4 h-4 text-orange-600 dark:text-orange-400" />
            </div>
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 매치</span>
          </div>
          <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
            {{ avgMatch.toFixed(1) }}%
          </p>
        </div>
      </div>

      <!-- Mood Selector -->
      <div class="mb-6 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <h2 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-3">무드 필터</h2>
        <MoodSelector :moods="moods" @select="handleMoodSelect" />
      </div>

      <!-- Recommendations Grid -->
      <section class="mb-6">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          추천 목록
          <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ store.recommendations.length }})</span>
        </h2>

        <div v-if="store.recommendations.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-4">
          <RecommendationCard
            v-for="rec in store.recommendations"
            :key="rec.id"
            :recommendation="rec"
            @select="handleSelectRecommendation"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
        >
          <MusicalNoteIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
          <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
            추천 내역이 없습니다
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            영상을 업로드하면 AI가 자동으로 음악을 추천합니다
          </p>
        </div>
      </section>

      <!-- Selected Recommendation Detail: Track List -->
      <section v-if="selectedRecommendation">
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm overflow-hidden">
          <!-- Header -->
          <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                "{{ selectedRecommendation.videoTitle }}" 추천 트랙
              </h3>
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ selectedRecommendation.tracks.length }}곡
              </span>
            </div>
          </div>

          <!-- Track list -->
          <div class="p-4 space-y-2">
            <MusicTrackRow
              v-for="track in selectedRecommendation.tracks"
              :key="track.id"
              :track="track"
            />
          </div>
        </div>
      </section>
    </template>
  </div>
</template>
