<template>
  <div class="card overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-gray-100 p-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-100 dark:bg-purple-900/30">
          <SparklesIcon class="h-4 w-4 text-purple-600 dark:text-purple-400" />
        </div>
        <div>
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">AI 주간 다이제스트</h3>
          <p v-if="digest" class="text-xs text-gray-500 dark:text-gray-400">{{ digest.weekRange }}</p>
        </div>
      </div>
      <button
        v-if="digest"
        class="text-xs text-primary-600 hover:underline dark:text-primary-400"
        @click="showDetail = true"
      >
        전체 보기
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="space-y-3 p-4">
      <div class="h-4 w-3/4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
      <div class="h-4 w-1/2 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
      <div class="h-4 w-2/3 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
    </div>

    <!-- No Data State -->
    <div v-else-if="!digest" class="py-8 text-center">
      <SparklesIcon class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600" />
      <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">아직 다이제스트가 없습니다</p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">Pro/Business 플랜에서 매주 월요일 자동 생성됩니다</p>
    </div>

    <!-- Digest Content -->
    <div v-else class="space-y-3 p-4">
      <!-- Summary -->
      <p class="text-sm leading-relaxed text-gray-700 dark:text-gray-300">
        {{ truncatedSummary }}
      </p>

      <!-- Top Video Highlight -->
      <div v-if="digest.topVideos.length > 0" class="rounded-lg bg-blue-50 p-3 dark:bg-blue-900/20">
        <p class="text-xs font-medium text-blue-700 dark:text-blue-300">
          Top 영상
        </p>
        <p class="mt-1 text-xs text-blue-600 dark:text-blue-400">
          {{ digest.topVideos[0] }}
        </p>
      </div>

      <!-- Anomalies -->
      <div v-if="digest.anomalies.length > 0" class="rounded-lg bg-amber-50 p-3 dark:bg-amber-900/20">
        <p class="text-xs font-medium text-amber-700 dark:text-amber-300">
          주목할 변화
        </p>
        <p class="mt-1 text-xs text-amber-600 dark:text-amber-400">
          {{ digest.anomalies[0] }}
        </p>
      </div>

      <!-- Action Items (Checklist) -->
      <div v-if="digest.actionItems.length > 0">
        <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">실행 항목</p>
        <div class="space-y-1.5">
          <label
            v-for="(item, idx) in digest.actionItems.slice(0, 3)"
            :key="idx"
            class="flex items-start gap-2 cursor-pointer group"
          >
            <input
              type="checkbox"
              class="mt-0.5 h-3.5 w-3.5 rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600"
              :checked="checkedItems.has(idx)"
              @change="toggleItem(idx)"
            />
            <span
              class="text-xs text-gray-600 dark:text-gray-400 transition-all"
              :class="{ 'line-through text-gray-400 dark:text-gray-500': checkedItems.has(idx) }"
            >
              {{ item }}
            </span>
          </label>
        </div>
      </div>
    </div>

    <!-- Detail Modal -->
    <Teleport to="body">
      <div
        v-if="showDetail && digest"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="showDetail = false"
      >
        <div class="relative max-h-[80vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl dark:bg-gray-800">
          <button
            class="absolute right-4 top-4 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
            @click="showDetail = false"
          >
            <XMarkIcon class="h-5 w-5" />
          </button>

          <div class="flex items-center gap-2">
            <SparklesIcon class="h-5 w-5 text-purple-600 dark:text-purple-400" />
            <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">주간 다이제스트</h2>
          </div>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ digest.weekRange }}</p>

          <div class="mt-4 space-y-4">
            <div>
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">요약</h3>
              <p class="mt-1 text-sm leading-relaxed text-gray-700 dark:text-gray-300">{{ digest.summary }}</p>
            </div>

            <div v-if="digest.topVideos.length > 0">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">상위 영상</h3>
              <ul class="mt-1 space-y-1">
                <li
                  v-for="(video, idx) in digest.topVideos"
                  :key="idx"
                  class="flex items-start gap-2 text-sm text-gray-700 dark:text-gray-300"
                >
                  <span class="flex-shrink-0 text-blue-500">{{ idx + 1 }}.</span>
                  {{ video }}
                </li>
              </ul>
            </div>

            <div v-if="digest.anomalies.length > 0">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">주목할 변화</h3>
              <ul class="mt-1 space-y-1">
                <li
                  v-for="(anomaly, idx) in digest.anomalies"
                  :key="idx"
                  class="text-sm text-gray-700 dark:text-gray-300"
                >
                  {{ anomaly }}
                </li>
              </ul>
            </div>

            <div v-if="digest.actionItems.length > 0">
              <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">실행 항목</h3>
              <div class="mt-1 space-y-2">
                <label
                  v-for="(item, idx) in digest.actionItems"
                  :key="idx"
                  class="flex items-start gap-2 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    class="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 dark:border-gray-600"
                    :checked="checkedItems.has(idx)"
                    @change="toggleItem(idx)"
                  />
                  <span
                    class="text-sm text-gray-700 dark:text-gray-300 transition-all"
                    :class="{ 'line-through text-gray-400 dark:text-gray-500': checkedItems.has(idx) }"
                  >
                    {{ item }}
                  </span>
                </label>
              </div>
            </div>
          </div>

          <p class="mt-4 text-xs text-gray-400 dark:text-gray-500">
            {{ dayjs(digest.generatedAt).format('YYYY.MM.DD HH:mm') }} 생성
          </p>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { SparklesIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import { aiApi } from '@/api/ai'
import type { WeeklyDigestResponse } from '@/types/ai'
import dayjs from 'dayjs'

const digest = ref<WeeklyDigestResponse | null>(null)
const loading = ref(true)
const showDetail = ref(false)
const checkedItems = ref(new Set<number>())

const truncatedSummary = computed(() => {
  if (!digest.value) return ''
  const summary = digest.value.summary
  return summary.length > 120 ? summary.slice(0, 120) + '...' : summary
})

function toggleItem(idx: number) {
  const newSet = new Set(checkedItems.value)
  if (newSet.has(idx)) {
    newSet.delete(idx)
  } else {
    newSet.add(idx)
  }
  checkedItems.value = newSet
}

onMounted(async () => {
  try {
    digest.value = await aiApi.getLatestWeeklyDigest()
  } catch {
    // No digest available
  } finally {
    loading.value = false
  }
})
</script>
