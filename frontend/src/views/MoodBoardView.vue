<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  SwatchIcon,
  PhotoIcon,
  SparklesIcon,
  Squares2X2Icon,
  ArrowLeftIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import MoodBoardCard from '@/components/moodboard/MoodBoardCard.vue'
import MoodBoardGrid from '@/components/moodboard/MoodBoardGrid.vue'
import { useMoodBoardStore } from '@/stores/moodBoard'
import type { MoodBoard } from '@/types/moodBoard'

const store = useMoodBoardStore()

const selectedBoard = ref<MoodBoard | null>(null)

onMounted(async () => {
  await Promise.all([
    store.fetchBoards(),
    store.fetchSummary(),
  ])
})

async function handleSelectBoard(id: number) {
  const board = store.boards.find((b) => b.id === id)
  if (board) {
    selectedBoard.value = board
    await store.fetchBoardItems(id)
  }
}

function handleBack() {
  selectedBoard.value = null
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div v-if="selectedBoard" class="flex items-center gap-3 mb-1">
          <button
            @click="handleBack"
            class="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <ArrowLeftIcon class="w-5 h-5" />
          </button>
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ selectedBoard.name }}
          </h1>
        </div>
        <h1 v-else class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          크리에이터 무드보드
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          <template v-if="selectedBoard">{{ selectedBoard.description }}</template>
          <template v-else>콘텐츠 스타일과 비주얼 아이디어를 핀보드처럼 관리하세요</template>
        </p>
      </div>
    </div>

    <PageGuide
      v-if="!selectedBoard"
      title="크리에이터 무드보드 활용 가이드"
      :items="[
        '이미지, 컬러, 텍스트 등 다양한 형태의 레퍼런스를 카테고리별로 수집하세요.',
        '촬영 전 무드보드를 확인하면 일관된 스타일을 유지할 수 있습니다.',
        '팀원과 보드를 공유하여 콘텐츠 방향성을 맞춰보세요.',
      ]"
    />

    <!-- Loading -->
    <div v-if="store.isLoading" class="flex items-center justify-center py-20">
      <LoadingSpinner size="lg" />
    </div>

    <template v-else>
      <!-- ===== Board List Mode ===== -->
      <template v-if="!selectedBoard">
        <!-- Summary Cards -->
        <div class="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <!-- Total Boards -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="flex items-center gap-2 mb-2">
              <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
                <Squares2X2Icon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
              </div>
              <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 보드</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
              {{ store.summary.totalBoards }}개
            </p>
          </div>

          <!-- Total Items -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="flex items-center gap-2 mb-2">
              <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
                <PhotoIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
              </div>
              <span class="text-xs font-medium text-gray-500 dark:text-gray-400">총 아이템</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
              {{ store.summary.totalItems }}개
            </p>
          </div>

          <!-- Top Category -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="flex items-center gap-2 mb-2">
              <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
                <SparklesIcon class="w-4 h-4 text-purple-600 dark:text-purple-400" />
              </div>
              <span class="text-xs font-medium text-gray-500 dark:text-gray-400">인기 카테고리</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
              {{ store.summary.topCategory }}
            </p>
          </div>

          <!-- Avg Items Per Board -->
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <div class="flex items-center gap-2 mb-2">
              <div class="p-2 rounded-lg bg-orange-100 dark:bg-orange-900/30">
                <SwatchIcon class="w-4 h-4 text-orange-600 dark:text-orange-400" />
              </div>
              <span class="text-xs font-medium text-gray-500 dark:text-gray-400">보드당 평균 아이템</span>
            </div>
            <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
              {{ store.summary.avgItemsPerBoard.toFixed(1) }}개
            </p>
          </div>
        </div>

        <!-- Board Grid -->
        <section>
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
            내 무드보드
            <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ store.boards.length }})</span>
          </h2>

          <div v-if="store.boards.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <MoodBoardCard
              v-for="board in store.boards"
              :key="board.id"
              :board="board"
              @select="handleSelectBoard"
            />
          </div>

          <div
            v-else
            class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
          >
            <SwatchIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
              아직 무드보드가 없습니다
            </h3>
            <p class="text-sm text-gray-600 dark:text-gray-400">
              콘텐츠 스타일과 비주얼 레퍼런스를 정리할 무드보드를 만들어보세요.
            </p>
          </div>
        </section>
      </template>

      <!-- ===== Board Detail Mode (Item Grid) ===== -->
      <template v-else>
        <!-- Board metadata -->
        <div class="mb-6 flex flex-wrap items-center gap-3">
          <span
            class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400"
          >
            {{ selectedBoard.category }}
          </span>
          <span class="text-sm text-gray-500 dark:text-gray-400">
            {{ selectedBoard.itemCount }}개 아이템
          </span>
          <span
            v-if="selectedBoard.isPublic"
            class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
          >
            공개
          </span>
          <span
            v-else
            class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
          >
            비공개
          </span>
        </div>

        <!-- Tags -->
        <div v-if="selectedBoard.tags.length > 0" class="mb-6 flex flex-wrap gap-1.5">
          <span
            v-for="tag in selectedBoard.tags"
            :key="tag"
            class="inline-flex items-center px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
          >
            #{{ tag }}
          </span>
        </div>

        <!-- Item Grid (Masonry) -->
        <section>
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
            아이템 목록
            <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ store.boardItems.length }})</span>
          </h2>

          <MoodBoardGrid
            v-if="store.boardItems.length > 0"
            :items="store.boardItems"
          />

          <div
            v-else
            class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
          >
            <PhotoIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
            <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
              이 보드에 아이템이 없습니다
            </h3>
            <p class="text-sm text-gray-600 dark:text-gray-400">
              이미지, 컬러, 텍스트 등 다양한 레퍼런스를 추가해보세요.
            </p>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>
