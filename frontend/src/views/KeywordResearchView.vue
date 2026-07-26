<template>
  <div>
    <PageHeader :title="$t('keywordResearch.title')" :description="$t('keywordResearch.description')">
      <template #actions>
        <button
          :disabled="store.researching"
          class="btn-primary inline-flex items-center gap-2 disabled:opacity-50"
          @click="handleResearch"
        >
          <SparklesIcon class="h-5 w-5" />
          {{ store.researching ? $t('keywordResearch.analyzing') : $t('keywordResearch.analyzeButton') }}
        </button>
      </template>
    </PageHeader>

    <!-- Search Form -->
    <div class="card mb-6">
      <div class="space-y-4">
        <!-- Keyword Input -->
        <div>
          <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">
            {{ $t('keywordResearch.keywordLabel') }}
          </label>
          <input
            v-model="keyword"
            type="text"
            :placeholder="$t('keywordResearch.keywordPlaceholder')"
            class="input-field w-full"
            @keyup.enter="handleResearch"
          />
        </div>

        <!-- Platform Selection -->
        <div>
          <label class="mb-2 block text-body font-medium text-gray-700 dark:text-gray-300">
            {{ $t('keywordResearch.platformLabel') }}
          </label>
          <div class="flex flex-wrap gap-3">
            <label
              v-for="p in availablePlatforms"
              :key="p.value"
              class="flex cursor-pointer items-center gap-2"
            >
              <input
                type="checkbox"
                :value="p.value"
                :checked="selectedPlatforms.includes(p.value)"
                class="h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500"
                @change="togglePlatform(p.value)"
              />
              <span class="text-body text-gray-700 dark:text-gray-300">{{ p.label }}</span>
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- Result -->
    <div v-if="store.currentResult" class="mb-6 space-y-4">
      <h2 class="text-body-lg font-semibold text-gray-900 dark:text-white">
        {{ $t('keywordResearch.resultTitle', { keyword: store.currentResult.keyword }) }}
      </h2>
      <div class="card overflow-x-auto">
        <table class="w-full text-left text-body">
          <thead class="border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800/50">
            <tr>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">{{ $t('keywordResearch.platform') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 text-right">{{ $t('keywordResearch.searchVolume') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 text-center">{{ $t('keywordResearch.competition') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 text-center">{{ $t('keywordResearch.trend') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400 text-right">{{ $t('keywordResearch.opportunityScore') }}</th>
              <th class="px-4 py-3 font-medium text-gray-600 dark:text-gray-400">{{ $t('keywordResearch.relatedKeywords') }}</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
            <tr
              v-for="row in store.currentResult.platforms"
              :key="row.platform"
              class="hover:bg-gray-50 dark:hover:bg-gray-800/50"
            >
              <td class="px-4 py-3 font-medium text-gray-900 dark:text-white">{{ platformLabel(row.platform) }}</td>
              <td class="px-4 py-3 text-right tabular-nums text-gray-700 dark:text-gray-300">
                {{ searchVolumeLabel(row.searchVolume) }}
              </td>
              <td class="px-4 py-3 text-center">
                <span
                  class="rounded-full px-2 py-0.5 text-body-xs font-medium"
                  :class="{
                    'bg-success-subtle text-success-strong': row.competition === 'LOW',
                    'bg-warning-subtle text-warning-strong': row.competition === 'MEDIUM',
                    'bg-error-subtle text-error-strong': row.competition === 'HIGH',
                  }"
                >
                  {{ $t(`keywordResearch.competition_${row.competition}`) }}
                </span>
              </td>
              <td class="px-4 py-3 text-center">
                <span
                  class="rounded-full px-2 py-0.5 text-body-xs font-medium"
                  :class="{
                    'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300': row.trend === 'RISING',
                    'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300': row.trend === 'STABLE',
                    'bg-error-subtle text-error-strong': row.trend === 'DECLINING',
                  }"
                >
                  {{ $t(`keywordResearch.trend_${row.trend}`) }}
                </span>
              </td>
              <td class="px-4 py-3 text-right tabular-nums">
                <span class="text-body font-semibold text-primary-600 dark:text-primary-400">
                  {{ row.opportunityScore }}
                </span>
              </td>
              <td class="px-4 py-3">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="kw in row.relatedKeywords.slice(0, 3)"
                    :key="kw"
                    class="rounded bg-gray-100 px-1.5 py-0.5 text-[11px] text-gray-600 dark:bg-gray-700 dark:text-gray-400"
                  >
                    {{ kw }}
                  </span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- History -->
    <div>
      <h2 class="mb-3 text-body-lg font-semibold text-gray-900 dark:text-white">
        {{ $t('keywordResearch.historyTitle') }}
      </h2>

      <LoadingSpinner v-if="store.loading" />

      <EmptyState
        v-else-if="store.history.length === 0"
        :title="$t('keywordResearch.historyEmpty')"
        :description="$t('keywordResearch.historyEmptyDesc')"
      />

      <div v-else class="space-y-2">
        <div
          v-for="item in store.history"
          :key="item.id"
          class="card flex items-center justify-between gap-3 cursor-pointer hover:border-primary-300 dark:hover:border-primary-700 transition-colors"
          @click="loadHistoryItem(item)"
        >
          <div>
            <p class="text-body font-medium text-gray-900 dark:text-white">{{ item.keyword }}</p>
            <p class="text-body-xs text-gray-500 dark:text-gray-400 mt-0.5">
              {{ item.platforms.map(platformLabel).join(', ') }} · {{ formatDate(item.createdAt) }}
            </p>
          </div>
          <ChevronRightIcon class="h-4 w-4 text-gray-400 shrink-0" />
        </div>

        <!-- Pagination -->
        <div v-if="store.totalPages > 1" class="flex items-center justify-center gap-3 pt-2">
          <button :disabled="!store.hasPrevPage" class="btn-secondary disabled:opacity-40" @click="store.prevPage()">
            {{ $t('action.prev') }}
          </button>
          <span class="text-body text-gray-500 dark:text-gray-400">
            {{ store.page }} / {{ store.totalPages }}
          </span>
          <button :disabled="!store.hasNextPage" class="btn-secondary disabled:opacity-40" @click="store.nextPage()">
            {{ $t('action.next') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { SparklesIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import { useKeywordResearchStore } from '@/stores/keywordResearch'
import type { KeywordPlatform, KeywordHistoryItem } from '@/types/keywordResearch'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const store = useKeywordResearchStore()

const keyword = ref('')
const selectedPlatforms = ref<KeywordPlatform[]>(['YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'])

const availablePlatforms: { value: KeywordPlatform; label: string }[] = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

const platformLabelMap: Record<KeywordPlatform, string> = {
  YOUTUBE: 'YouTube',
  TIKTOK: 'TikTok',
  INSTAGRAM: 'Instagram',
  NAVER_CLIP: 'Naver Clip',
}

function platformLabel(p: KeywordPlatform): string {
  return platformLabelMap[p] ?? p
}

function searchVolumeLabel(value: string): string {
  const labels: Record<string, string> = {
    HIGH: '높음',
    MEDIUM: '보통',
    LOW: '낮음',
  }
  return labels[value.toUpperCase()] ?? value
}

function togglePlatform(p: KeywordPlatform) {
  const idx = selectedPlatforms.value.indexOf(p)
  if (idx === -1) {
    selectedPlatforms.value.push(p)
  } else {
    selectedPlatforms.value.splice(idx, 1)
  }
}

async function handleResearch() {
  if (!keyword.value.trim() || selectedPlatforms.value.length === 0) return
  await store.research(keyword.value.trim(), selectedPlatforms.value)
  await store.fetchHistory()
}

function loadHistoryItem(item: KeywordHistoryItem) {
  if (item.result) {
    store.currentResult = item.result
    keyword.value = item.keyword
  }
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return d.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
}

onMounted(() => {
  store.fetchHistory()
})
</script>
