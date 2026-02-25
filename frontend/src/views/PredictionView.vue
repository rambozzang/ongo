<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('prediction.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ $t('prediction.description') }}</p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('prediction.creditsRemaining') }}: {{ creditBalance }}
        </span>
      </div>
    </div>

    <PageGuide :title="$t('prediction.pageGuideTitle')" :items="($tm('prediction.pageGuide') as string[])" />

    <LoadingSpinner v-if="loading" full-page />

    <template v-else>
      <!-- Prediction Input Form -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('prediction.inputTitle') }}
        </h2>

        <div class="grid gap-4 tablet:grid-cols-2">
          <!-- Title -->
          <div class="tablet:col-span-2">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('prediction.videoTitle') }}
            </label>
            <input
              v-model="form.title"
              type="text"
              :placeholder="$t('prediction.videoTitlePlaceholder')"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            />
          </div>

          <!-- Description -->
          <div class="tablet:col-span-2">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('prediction.videoDescription') }}
            </label>
            <textarea
              v-model="form.description"
              rows="3"
              :placeholder="$t('prediction.videoDescriptionPlaceholder')"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
            />
          </div>

          <!-- Platform -->
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('prediction.platform') }}
            </label>
            <select
              v-model="form.platform"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              <option value="YOUTUBE">YouTube</option>
              <option value="TIKTOK">TikTok</option>
              <option value="INSTAGRAM">Instagram</option>
              <option value="NAVER_CLIP">Naver Clip</option>
            </select>
          </div>

          <!-- Category -->
          <div>
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('prediction.category') }}
            </label>
            <select
              v-model="form.category"
              class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
            >
              <option value="">{{ $t('prediction.selectCategory') }}</option>
              <option value="vlog">{{ $t('prediction.categories.vlog') }}</option>
              <option value="cooking">{{ $t('prediction.categories.cooking') }}</option>
              <option value="travel">{{ $t('prediction.categories.travel') }}</option>
              <option value="beauty">{{ $t('prediction.categories.beauty') }}</option>
              <option value="tech">{{ $t('prediction.categories.tech') }}</option>
              <option value="gaming">{{ $t('prediction.categories.gaming') }}</option>
              <option value="education">{{ $t('prediction.categories.education') }}</option>
              <option value="music">{{ $t('prediction.categories.music') }}</option>
              <option value="fitness">{{ $t('prediction.categories.fitness') }}</option>
            </select>
          </div>

          <!-- Tags -->
          <div class="tablet:col-span-2">
            <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('prediction.tags') }}
            </label>
            <div class="flex flex-wrap gap-2 mb-2">
              <span
                v-for="(tag, idx) in form.tags"
                :key="idx"
                class="inline-flex items-center gap-1 rounded-full bg-primary-100 dark:bg-primary-900/30 px-2.5 py-0.5 text-xs font-medium text-primary-700 dark:text-primary-400"
              >
                {{ tag }}
                <button
                  class="hover:text-primary-900 dark:hover:text-primary-200"
                  @click="removeTag(idx)"
                >
                  <XMarkIcon class="h-3 w-3" />
                </button>
              </span>
            </div>
            <div class="flex gap-2">
              <input
                v-model="tagInput"
                type="text"
                :placeholder="$t('prediction.tagPlaceholder')"
                class="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                @keydown.enter.prevent="addTag"
              />
              <button
                class="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="addTag"
              >
                {{ $t('prediction.addTag') }}
              </button>
            </div>
          </div>
        </div>

        <!-- Predict Button -->
        <div class="mt-4 flex items-center gap-3">
          <button
            class="btn-primary inline-flex items-center gap-2"
            :disabled="!canPredict || predicting"
            @click="handlePredict"
          >
            <SparklesIcon class="h-4 w-4" />
            {{ predicting ? $t('prediction.predicting') : $t('prediction.predictButton') }}
          </button>
          <span class="text-xs text-gray-400 dark:text-gray-500">
            {{ $t('prediction.creditCost', { cost: 5 }) }}
          </span>
        </div>
      </div>

      <!-- Prediction Result -->
      <PredictionCard
        v-if="predictionResult"
        :result="predictionResult"
        class="mb-6"
      />

      <!-- Tag Suggestions -->
      <div v-if="tagSuggestions.length > 0" class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('prediction.tagSuggestionsTitle') }}
        </h2>
        <div class="grid gap-3 tablet:grid-cols-2 desktop:grid-cols-3">
          <div
            v-for="suggestion in tagSuggestions"
            :key="suggestion.tag"
            class="flex items-center justify-between rounded-lg border border-gray-200 dark:border-gray-700 p-3 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            @click="addSuggestedTag(suggestion.tag)"
          >
            <div>
              <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ suggestion.tag }}</span>
              <div class="mt-1 flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                <span>{{ $t('prediction.trend') }}: {{ suggestion.trendScore }}</span>
                <span>{{ $t('prediction.relevance') }}: {{ suggestion.relevanceScore }}</span>
              </div>
            </div>
            <span
              class="rounded-full px-2 py-0.5 text-xs font-medium"
              :class="competitionClass(suggestion.competitionLevel)"
            >
              {{ competitionLabel(suggestion.competitionLevel) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Title Suggestions -->
      <div v-if="titleSuggestions.length > 0" class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('prediction.titleSuggestionsTitle') }}
        </h2>
        <div class="space-y-3">
          <div
            v-for="(suggestion, idx) in titleSuggestions"
            :key="idx"
            class="flex items-center justify-between rounded-lg border border-gray-200 dark:border-gray-700 p-3 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
            @click="form.title = suggestion.suggested"
          >
            <div class="min-w-0 flex-1 mr-3">
              <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                {{ suggestion.suggested }}
              </p>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ suggestion.reason }}</p>
            </div>
            <span class="flex-shrink-0 inline-flex items-center gap-1 text-sm font-semibold text-green-600 dark:text-green-400">
              <ArrowTrendingUpIcon class="h-4 w-4" />
              +{{ suggestion.expectedImprovement }}%
            </span>
          </div>
        </div>
      </div>

      <!-- Two-column: Heatmap + Competitor Comparison -->
      <div class="mb-6 grid gap-6 desktop:grid-cols-2">
        <PredictionHeatmap
          :data="heatmapData"
          :optimal-times="optimalTimes"
          :selected-platform="heatmapPlatform"
          :loading="heatmapLoading"
          @platform-change="handleHeatmapPlatformChange"
        />

        <PredictionComparison :comparisons="competitors" />
      </div>

      <!-- Prediction History -->
      <PredictionHistory
        :history="history"
        :summary="summary"
        :loading="historyLoading"
        class="mb-6"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  SparklesIcon,
  XMarkIcon,
  ArrowTrendingUpIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PredictionCard from '@/components/prediction/PredictionCard.vue'
import PredictionHeatmap from '@/components/prediction/PredictionHeatmap.vue'
import PredictionComparison from '@/components/prediction/PredictionComparison.vue'
import PredictionHistory from '@/components/prediction/PredictionHistory.vue'
import { usePredictionStore } from '@/stores/prediction'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { Platform } from '@/types/channel'

const { t } = useI18n({ useScope: 'global' })
const predictionStore = usePredictionStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()

const {
  predictionResult,
  heatmapData,
  optimalTimes,
  titleSuggestions,
  tagSuggestions,
  competitors,
  history,
  summary,
  loading,
  heatmapLoading,
  historyLoading,
  predicting,
} = storeToRefs(predictionStore)

// Form
const form = reactive({
  title: '',
  description: '',
  platform: 'YOUTUBE' as Platform,
  category: '',
  tags: [] as string[],
})

const tagInput = ref('')
const heatmapPlatform = ref('all')

const canPredict = computed(() => {
  return form.title.trim().length > 0 && form.description.trim().length > 0
})

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !form.tags.includes(tag)) {
    form.tags.push(tag.startsWith('#') ? tag : `#${tag}`)
    tagInput.value = ''
  }
}

function removeTag(index: number) {
  form.tags.splice(index, 1)
}

function addSuggestedTag(tag: string) {
  if (!form.tags.includes(tag)) {
    form.tags.push(tag)
  }
}

async function handlePredict() {
  const canUse = await checkAndUse(5, 'AI 성과 예측')
  if (!canUse) return

  try {
    await predictionStore.predict({
      title: form.title,
      description: form.description,
      tags: form.tags,
      platform: form.platform,
      category: form.category || undefined,
    })

    // Also fetch title/tag suggestions based on input
    if (form.title) {
      predictionStore.fetchTitleSuggestions(form.title, form.platform)
    }
    if (form.tags.length > 0) {
      predictionStore.fetchTagSuggestions(form.tags, form.platform)
    }

    predictionStore.fetchCompetitors()
  } catch {
    notification.error(t('prediction.predictError'))
  }
}

function handleHeatmapPlatformChange(platform: string) {
  heatmapPlatform.value = platform
  predictionStore.fetchHeatmap(platform === 'all' ? undefined : platform)
}

function competitionClass(level: string): string {
  switch (level) {
    case 'low': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'medium': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'high': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
}

function competitionLabel(level: string): string {
  switch (level) {
    case 'low': return t('prediction.competitionLow')
    case 'medium': return t('prediction.competitionMedium')
    case 'high': return t('prediction.competitionHigh')
    default: return level
  }
}

onMounted(() => {
  predictionStore.fetchAll()
})
</script>
