<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  HashtagIcon,
  SparklesIcon,
  PlusIcon,
  XMarkIcon,
  ArrowTrendingUpIcon,
  HeartIcon,
  TrophyIcon,
  MagnifyingGlassIcon,
} from '@heroicons/vue/24/outline'
import { useHashtagAnalyticsStore } from '@/stores/hashtagAnalytics'
import HashtagCard from '@/components/hashtaganalytics/HashtagCard.vue'
import RecommendationList from '@/components/hashtaganalytics/RecommendationList.vue'
import HashtagGroupCard from '@/components/hashtaganalytics/HashtagGroupCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useHashtagAnalyticsStore()
const { performances, recommendations, groups, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'performance' | 'recommendations' | 'groups'>('performance')

// ---- Analyze Modal ----
const showAnalyzeModal = ref(false)
const analyzeTopic = ref('')
const analyzePlatform = ref('YOUTUBE')
const analyzeCount = ref(10)

const platformOptions = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'NAVERCLIP', label: 'Naver Clip' },
]

const openAnalyzeModal = () => {
  analyzeTopic.value = ''
  analyzePlatform.value = 'YOUTUBE'
  analyzeCount.value = 10
  showAnalyzeModal.value = true
}

const closeAnalyzeModal = () => {
  showAnalyzeModal.value = false
}

const handleAnalyze = async () => {
  if (!analyzeTopic.value.trim()) return
  await store.analyzeHashtags({
    topic: analyzeTopic.value.trim(),
    platform: analyzePlatform.value,
    count: analyzeCount.value,
  })
  closeAnalyzeModal()
  activeTab.value = 'recommendations'
}

// ---- Recommendation Tab inline form ----
const recTopic = ref('')
const recPlatform = ref('YOUTUBE')

const handleQuickAnalyze = async () => {
  if (!recTopic.value.trim()) return
  await store.analyzeHashtags({
    topic: recTopic.value.trim(),
    platform: recPlatform.value,
    count: 10,
  })
}

// ---- Group Modal ----
const showGroupModal = ref(false)
const newGroupName = ref('')
const newGroupHashtags = ref('')
const newGroupPlatform = ref('YOUTUBE')

const openGroupModal = () => {
  newGroupName.value = ''
  newGroupHashtags.value = ''
  newGroupPlatform.value = 'YOUTUBE'
  showGroupModal.value = true
}

const closeGroupModal = () => {
  showGroupModal.value = false
}

const handleCreateGroup = async () => {
  if (!newGroupName.value.trim() || !newGroupHashtags.value.trim()) return
  const hashtags = newGroupHashtags.value
    .split(',')
    .map((h) => h.trim())
    .filter(Boolean)
  await store.createGroup(newGroupName.value.trim(), hashtags, newGroupPlatform.value)
  closeGroupModal()
}

const handleDeleteGroup = (id: number) => {
  if (confirm('이 그룹을 삭제하시겠습니까?')) {
    store.deleteGroup(id)
  }
}

const handleHashtagClick = (_id: number) => {
  // future: show detail modal
}

onMounted(() => {
  store.fetchPerformances()
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
            AI 해시태그 분석기
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          AI 기반 해시태그 성과 분석 및 추천으로 콘텐츠 도달률을 극대화하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openAnalyzeModal"
        >
          <MagnifyingGlassIcon class="h-5 w-5" />
          해시태그 분석
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <HashtagIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 해시태그</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalHashtags.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowTrendingUpIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">트렌딩</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.trendingCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <HeartIcon class="h-5 w-5 text-pink-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 참여율</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgEngagementRate.toFixed(1) }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TrophyIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">탑 퍼포머</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.topPerformer || '-' }}
        </p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-8">
        <button
          @click="activeTab = 'performance'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'performance'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          성과
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'performance'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ performances.length }}
          </span>
        </button>

        <button
          @click="activeTab = 'recommendations'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'recommendations'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          추천
          <span
            v-if="recommendations.length > 0"
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'recommendations'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ recommendations.length }}
          </span>
        </button>

        <button
          @click="activeTab = 'groups'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'groups'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          그룹
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'groups'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ groups.length }}
          </span>
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Performance Tab -->
    <div v-else-if="activeTab === 'performance'">
      <div v-if="performances.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <HashtagCard
          v-for="p in performances"
          :key="p.id"
          :performance="p"
          @click="handleHashtagClick"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <HashtagIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          해시태그 데이터가 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠를 업로드하면 해시태그 성과가 분석됩니다
        </p>
      </div>
    </div>

    <!-- Recommendations Tab -->
    <div v-else-if="activeTab === 'recommendations'">
      <!-- Inline search form -->
      <div class="mb-6 flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900 tablet:flex-row tablet:items-end">
        <div class="flex-1">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">주제</label>
          <input
            v-model="recTopic"
            type="text"
            placeholder="예: 여행, 먹방, 패션..."
            class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
            @keyup.enter="handleQuickAnalyze"
          />
        </div>
        <div class="w-full tablet:w-40">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">플랫폼</label>
          <select
            v-model="recPlatform"
            class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
          >
            <option v-for="opt in platformOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
        </div>
        <button
          class="btn-primary inline-flex items-center gap-2 whitespace-nowrap"
          :disabled="!recTopic.trim()"
          @click="handleQuickAnalyze"
        >
          <SparklesIcon class="h-4 w-4" />
          분석
        </button>
      </div>

      <RecommendationList :recommendations="recommendations" />
    </div>

    <!-- Groups Tab -->
    <div v-else-if="activeTab === 'groups'">
      <div class="mb-4 flex justify-end">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openGroupModal"
        >
          <PlusIcon class="h-5 w-5" />
          새 그룹
        </button>
      </div>

      <div v-if="groups.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <HashtagGroupCard
          v-for="g in groups"
          :key="g.id"
          :group="g"
          @delete="handleDeleteGroup"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <HashtagIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          해시태그 그룹이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          자주 사용하는 해시태그를 그룹으로 관리하세요
        </p>
        <button
          class="btn-primary mt-4 inline-flex items-center gap-2"
          @click="openGroupModal"
        >
          <PlusIcon class="h-5 w-5" />
          첫 그룹 만들기
        </button>
      </div>
    </div>

    <!-- Analyze Modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showAnalyzeModal"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeAnalyzeModal"
        >
          <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                해시태그 분석
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeAnalyzeModal"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  주제
                </label>
                <input
                  v-model="analyzeTopic"
                  type="text"
                  placeholder="분석할 주제를 입력하세요"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  플랫폼
                </label>
                <select
                  v-model="analyzePlatform"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option v-for="opt in platformOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>

              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  추천 개수
                </label>
                <input
                  v-model.number="analyzeCount"
                  type="number"
                  min="1"
                  max="30"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
            </div>

            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeAnalyzeModal"
              >
                취소
              </button>
              <button
                class="btn-primary text-sm"
                :disabled="!analyzeTopic.trim()"
                @click="handleAnalyze"
              >
                분석 시작
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Group Create Modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showGroupModal"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeGroupModal"
        >
          <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                새 해시태그 그룹
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeGroupModal"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  그룹명
                </label>
                <input
                  v-model="newGroupName"
                  type="text"
                  placeholder="예: 여행 세트"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  해시태그 (쉼표 구분)
                </label>
                <textarea
                  v-model="newGroupHashtags"
                  rows="3"
                  placeholder="#여행, #여행브이로그, #travel, #vlog"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  플랫폼
                </label>
                <select
                  v-model="newGroupPlatform"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                >
                  <option v-for="opt in platformOptions" :key="opt.value" :value="opt.value">
                    {{ opt.label }}
                  </option>
                </select>
              </div>
            </div>

            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeGroupModal"
              >
                취소
              </button>
              <button
                class="btn-primary text-sm"
                :disabled="!newGroupName.trim() || !newGroupHashtags.trim()"
                @click="handleCreateGroup"
              >
                그룹 생성
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
