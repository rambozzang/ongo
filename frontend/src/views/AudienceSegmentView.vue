<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  UserGroupIcon,
  PlusIcon,
  XMarkIcon,
  ChartBarIcon,
  UsersIcon,
  TrophyIcon,
  HeartIcon,
} from '@heroicons/vue/24/outline'
import { useAudienceSegmentStore } from '@/stores/audienceSegment'
import SegmentCard from '@/components/audiencesegment/SegmentCard.vue'
import InsightPanel from '@/components/audiencesegment/InsightPanel.vue'
import SegmentCompareChart from '@/components/audiencesegment/SegmentCompareChart.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { AudienceSegment, SegmentType } from '@/types/audienceSegment'

const store = useAudienceSegmentStore()
const { segments, selectedInsight, isLoading, totalAudience, topSegment } = storeToRefs(store)

// ─── UI State ──────────────────────────────────────
const selectedSegment = ref<AudienceSegment | null>(null)
const showCreateModal = ref(false)
const showInsightPanel = ref(false)
const activeTab = ref<'grid' | 'compare'>('grid')

// ─── Create Modal ──────────────────────────────────
const newSegmentName = ref('')
const newSegmentType = ref<SegmentType>('CUSTOM')
const newAgeMin = ref(18)
const newAgeMax = ref(65)
const newGender = ref<'MALE' | 'FEMALE' | 'ALL'>('ALL')
const newRegions = ref('')
const newInterests = ref('')

const typeOptions: { value: SegmentType; label: string }[] = [
  { value: 'AGE', label: '연령' },
  { value: 'GENDER', label: '성별' },
  { value: 'REGION', label: '지역' },
  { value: 'INTEREST', label: '관심사' },
  { value: 'BEHAVIOR', label: '행동' },
  { value: 'CUSTOM', label: '커스텀' },
]

// ─── Computed ──────────────────────────────────────
const avgEngagement = computed(() => {
  if (segments.value.length === 0) return 0
  const sum = segments.value.reduce((acc, s) => acc + s.avgEngagement, 0)
  return (sum / segments.value.length).toFixed(1)
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString()
}

// ─── Handlers ──────────────────────────────────────
async function handleSelectSegment(segment: AudienceSegment) {
  selectedSegment.value = segment
  showInsightPanel.value = true
  await store.fetchInsight(segment.id)
}

function closeInsightPanel() {
  showInsightPanel.value = false
  selectedSegment.value = null
}

function openCreateModal() {
  newSegmentName.value = ''
  newSegmentType.value = 'CUSTOM'
  newAgeMin.value = 18
  newAgeMax.value = 65
  newGender.value = 'ALL'
  newRegions.value = ''
  newInterests.value = ''
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

async function handleCreateSegment() {
  if (!newSegmentName.value.trim()) return

  const regions = newRegions.value
    .split(',')
    .map((r) => r.trim())
    .filter(Boolean)

  const interests = newInterests.value
    .split(',')
    .map((i) => i.trim())
    .filter(Boolean)

  await store.createSegment({
    name: newSegmentName.value.trim(),
    type: newSegmentType.value,
    criteria: {
      ageRange: { min: newAgeMin.value, max: newAgeMax.value },
      gender: newGender.value,
      regions: regions.length > 0 ? regions : undefined,
      interests: interests.length > 0 ? interests : undefined,
    },
  })

  closeCreateModal()
}

onMounted(() => {
  store.fetchSegments()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            오디언스 세분화
          </h1>
          <span
            v-if="totalAudience > 0"
            class="rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:bg-blue-900/30 dark:text-blue-400"
          >
            {{ formatNumber(totalAudience) }}명
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          시청자를 세그먼트로 분류하고 각 그룹에 맞는 AI 인사이트를 확인하세요
        </p>
      </div>
      <div class="flex items-center gap-3">
        <!-- View toggle -->
        <div class="flex rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          <button
            :class="[
              'px-3 py-1.5 text-sm font-medium transition-colors',
              activeTab === 'grid'
                ? 'bg-primary-600 text-white'
                : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700',
            ]"
            @click="activeTab = 'grid'"
          >
            카드
          </button>
          <button
            :class="[
              'px-3 py-1.5 text-sm font-medium transition-colors',
              activeTab === 'compare'
                ? 'bg-primary-600 text-white'
                : 'bg-white dark:bg-gray-800 text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700',
            ]"
            @click="activeTab = 'compare'"
          >
            비교
          </button>
        </div>
        <button class="btn-primary inline-flex items-center gap-2" @click="openCreateModal">
          <PlusIcon class="h-5 w-5" />
          새 세그먼트
        </button>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="mb-6 grid grid-cols-2 gap-4 tablet:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-blue-100 p-2 dark:bg-blue-900/30">
            <ChartBarIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">총 세그먼트</p>
            <p class="text-xl font-bold text-gray-900 dark:text-white">{{ segments.length }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-green-100 p-2 dark:bg-green-900/30">
            <UsersIcon class="h-5 w-5 text-green-600 dark:text-green-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">총 오디언스</p>
            <p class="text-xl font-bold text-gray-900 dark:text-white">{{ formatNumber(totalAudience) }}</p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-amber-100 p-2 dark:bg-amber-900/30">
            <TrophyIcon class="h-5 w-5 text-amber-600 dark:text-amber-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">상위 세그먼트</p>
            <p class="text-sm font-bold text-gray-900 dark:text-white truncate max-w-[120px]">
              {{ topSegment?.name ?? '-' }}
            </p>
          </div>
        </div>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
        <div class="flex items-center gap-3">
          <div class="rounded-lg bg-pink-100 p-2 dark:bg-pink-900/30">
            <HeartIcon class="h-5 w-5 text-pink-600 dark:text-pink-400" />
          </div>
          <div>
            <p class="text-xs text-gray-500 dark:text-gray-400">평균 참여율</p>
            <p class="text-xl font-bold text-gray-900 dark:text-white">{{ avgEngagement }}%</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Grid View -->
    <template v-else>
      <div v-if="activeTab === 'grid'">
        <div
          v-if="segments.length > 0"
          class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3"
        >
          <SegmentCard
            v-for="segment in segments"
            :key="segment.id"
            :segment="segment"
            :selected="selectedSegment?.id === segment.id"
            @select="handleSelectSegment"
          />
        </div>

        <!-- Empty state -->
        <div
          v-else
          class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-300 py-16 dark:border-gray-600"
        >
          <UserGroupIcon class="mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            세그먼트가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            새 세그먼트를 만들어 시청자를 분석해보세요
          </p>
          <button class="btn-primary mt-4 inline-flex items-center gap-2" @click="openCreateModal">
            <PlusIcon class="h-5 w-5" />
            첫 세그먼트 만들기
          </button>
        </div>
      </div>

      <!-- Compare View -->
      <div v-if="activeTab === 'compare'">
        <SegmentCompareChart :segments="segments" />
      </div>
    </template>

    <!-- Insight Sidebar / Panel -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-300 ease-out"
        enter-from-class="translate-x-full opacity-0"
        enter-to-class="translate-x-0 opacity-100"
        leave-active-class="transition duration-200 ease-in"
        leave-from-class="translate-x-0 opacity-100"
        leave-to-class="translate-x-full opacity-0"
      >
        <div
          v-if="showInsightPanel && selectedSegment"
          class="fixed inset-y-0 right-0 z-50 flex"
        >
          <!-- Backdrop -->
          <div
            class="fixed inset-0 bg-black/30"
            @click="closeInsightPanel"
          />
          <!-- Panel -->
          <div class="relative ml-auto w-full max-w-lg overflow-y-auto bg-white shadow-xl dark:bg-gray-900">
            <div class="sticky top-0 z-10 flex items-center justify-between border-b border-gray-200 bg-white px-6 py-4 dark:border-gray-700 dark:bg-gray-900">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ selectedSegment.name }}
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeInsightPanel"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>
            <div class="p-6">
              <InsightPanel
                v-if="selectedInsight"
                :insight="selectedInsight"
                :segment-name="selectedSegment.name"
              />
              <div
                v-else
                class="flex items-center justify-center py-12"
              >
                <LoadingSpinner size="md" />
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Create Segment Modal -->
    <Teleport to="body">
      <div
        v-if="showCreateModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="closeCreateModal"
      >
        <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
          <!-- Modal header -->
          <div class="mb-5 flex items-center justify-between">
            <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
              새 세그먼트 만들기
            </h2>
            <button
              class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
              @click="closeCreateModal"
            >
              <XMarkIcon class="h-5 w-5" />
            </button>
          </div>

          <!-- Form -->
          <div class="space-y-4">
            <!-- Segment name -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                세그먼트 이름
              </label>
              <input
                v-model="newSegmentName"
                type="text"
                placeholder="예: 20대 남성 게이머"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>

            <!-- Type -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                세그먼트 유형
              </label>
              <select
                v-model="newSegmentType"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>

            <!-- Age range -->
            <div class="grid grid-cols-2 gap-4">
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  최소 연령
                </label>
                <input
                  v-model.number="newAgeMin"
                  type="number"
                  min="13"
                  max="99"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  최대 연령
                </label>
                <input
                  v-model.number="newAgeMax"
                  type="number"
                  min="13"
                  max="99"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
                />
              </div>
            </div>

            <!-- Gender -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                성별
              </label>
              <select
                v-model="newGender"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100"
              >
                <option value="ALL">전체</option>
                <option value="MALE">남성</option>
                <option value="FEMALE">여성</option>
              </select>
            </div>

            <!-- Regions -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                지역 (쉼표로 구분)
              </label>
              <input
                v-model="newRegions"
                type="text"
                placeholder="예: 서울, 경기, 부산"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>

            <!-- Interests -->
            <div>
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                관심사 (쉼표로 구분)
              </label>
              <input
                v-model="newInterests"
                type="text"
                placeholder="예: 게임, 뷰티, 테크"
                class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>
          </div>

          <!-- Modal actions -->
          <div class="mt-6 flex items-center justify-end gap-3">
            <button
              class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
              @click="closeCreateModal"
            >
              취소
            </button>
            <button
              class="btn-primary text-sm"
              :disabled="!newSegmentName.trim()"
              @click="handleCreateSegment"
            >
              세그먼트 생성
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
