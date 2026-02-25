<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('thumbnailGenerator.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('thumbnailGenerator.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('thumbnailGenerator.creditsRemaining') }}: {{ creditBalance }}
        </span>
      </div>
    </div>

    <PageGuide
      :title="$t('thumbnailGenerator.pageGuideTitle')"
      :items="($tm('thumbnailGenerator.pageGuide') as string[])"
    />

    <LoadingSpinner v-if="loading" full-page />

    <template v-else>
      <!-- Two-column layout: Generator (left) + Results/History (right) -->
      <div class="grid gap-6 desktop:grid-cols-5">
        <!-- Generator Panel (Left) -->
        <div class="desktop:col-span-2">
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('thumbnailGenerator.generatorTitle') }}
            </h2>

            <!-- Video Title Input -->
            <div class="mb-4">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('thumbnailGenerator.videoTitle') }}
              </label>
              <input
                v-model="form.videoTitle"
                type="text"
                :placeholder="$t('thumbnailGenerator.videoTitlePlaceholder')"
                class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>

            <!-- Style Selector -->
            <div class="mb-4">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('thumbnailGenerator.styleLabel') }}
              </label>
              <p class="mb-2 text-xs text-gray-500 dark:text-gray-400">
                {{ $t('thumbnailGenerator.styleDescription') }}
              </p>
              <div class="grid grid-cols-2 gap-3 tablet:grid-cols-3">
                <StyleCard
                  v-for="tmpl in templates"
                  :key="tmpl.id"
                  :template="tmpl"
                  :selected="form.style === tmpl.style"
                  @select="form.style = tmpl.style"
                />
              </div>
            </div>

            <!-- Keywords Input -->
            <div class="mb-4">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('thumbnailGenerator.keywords') }}
              </label>
              <input
                v-model="keywordsInput"
                type="text"
                :placeholder="$t('thumbnailGenerator.keywordsPlaceholder')"
                class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
              <div v-if="parsedKeywords.length > 0" class="mt-2 flex flex-wrap gap-1.5">
                <span
                  v-for="(kw, idx) in parsedKeywords"
                  :key="idx"
                  class="inline-flex items-center rounded-full bg-primary-100 dark:bg-primary-900/30 px-2 py-0.5 text-xs font-medium text-primary-700 dark:text-primary-400"
                >
                  {{ kw }}
                </span>
              </div>
            </div>

            <!-- Platform Select -->
            <div class="mb-4">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('thumbnailGenerator.platform') }}
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

            <!-- Text Overlay Toggle + Input -->
            <div class="mb-4">
              <label class="flex items-center gap-2 cursor-pointer">
                <input
                  v-model="form.includeText"
                  type="checkbox"
                  class="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-primary-600 focus:ring-primary-500"
                />
                <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('thumbnailGenerator.textOverlayToggle') }}
                </span>
              </label>
              <input
                v-if="form.includeText"
                v-model="form.textOverlay"
                type="text"
                :placeholder="$t('thumbnailGenerator.textOverlayPlaceholder')"
                class="mt-2 w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>

            <!-- Color Scheme Picker -->
            <div class="mb-4">
              <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('thumbnailGenerator.colorScheme') }}
              </label>
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="scheme in colorSchemes"
                  :key="scheme.value"
                  class="flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors"
                  :class="form.colorScheme === scheme.value
                    ? 'border-primary-500 bg-primary-50 text-primary-700 dark:border-primary-400 dark:bg-primary-900/20 dark:text-primary-400'
                    : 'border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-400 dark:hover:border-gray-500'"
                  @click="form.colorScheme = scheme.value"
                >
                  <span class="h-3 w-3 rounded-full" :class="scheme.dotClass" />
                  {{ scheme.label }}
                </button>
              </div>
            </div>

            <!-- Generate Button -->
            <div class="flex items-center gap-3">
              <button
                class="btn-primary inline-flex items-center gap-2"
                :disabled="!canGenerate || generating"
                @click="handleGenerate"
              >
                <SparklesIcon class="h-4 w-4" />
                {{ generating ? $t('thumbnailGenerator.generating') : $t('thumbnailGenerator.generateButton') }}
              </button>
              <span class="text-xs text-gray-400 dark:text-gray-500">
                {{ $t('thumbnailGenerator.creditCost', { cost: 3 }) }}
              </span>
            </div>
          </div>
        </div>

        <!-- Results + History Panel (Right) -->
        <div class="desktop:col-span-3 space-y-6">
          <!-- Generated Results -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('thumbnailGenerator.resultsTitle') }}
            </h2>

            <!-- Loading state -->
            <div v-if="generating" class="flex flex-col items-center justify-center py-16">
              <LoadingSpinner />
              <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
                {{ $t('thumbnailGenerator.generating') }}
              </p>
            </div>

            <!-- Empty state -->
            <div
              v-else-if="generatedThumbnails.length === 0"
              class="flex flex-col items-center justify-center py-16 text-gray-400 dark:text-gray-500"
            >
              <PhotoIcon class="mb-3 h-12 w-12" />
              <p class="text-sm font-medium">{{ $t('thumbnailGenerator.noResults') }}</p>
              <p class="mt-1 text-xs">{{ $t('thumbnailGenerator.noResultsDesc') }}</p>
            </div>

            <!-- Thumbnails Grid -->
            <div v-else class="grid grid-cols-2 gap-4">
              <ThumbnailPreview
                v-for="thumb in generatedThumbnails"
                :key="thumb.id"
                :thumbnail="thumb"
                :is-selected="selectedThumbnailId === thumb.id"
                @select="handleSelectThumbnail(thumb.id)"
                @download="handleDownload(thumb)"
              />
            </div>
          </div>

          <!-- History -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('thumbnailGenerator.historyTitle') }}
            </h2>

            <div
              v-if="history.length === 0"
              class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500"
            >
              <ClockIcon class="mb-3 h-12 w-12" />
              <p class="text-sm font-medium">{{ $t('thumbnailGenerator.noHistory') }}</p>
              <p class="mt-1 text-xs">{{ $t('thumbnailGenerator.noHistoryDesc') }}</p>
            </div>

            <div v-else class="space-y-3">
              <HistoryItem
                v-for="item in history"
                :key="item.id"
                :item="item"
              />
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  SparklesIcon,
  PhotoIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import StyleCard from '@/components/thumbnailgenerator/StyleCard.vue'
import ThumbnailPreview from '@/components/thumbnailgenerator/ThumbnailPreview.vue'
import HistoryItem from '@/components/thumbnailgenerator/HistoryItem.vue'
import { useThumbnailGeneratorStore } from '@/stores/thumbnailGenerator'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { ThumbnailStyle, GeneratedThumbnail } from '@/types/thumbnailGenerator'

const { t } = useI18n({ useScope: 'global' })
const store = useThumbnailGeneratorStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()

const {
  templates,
  generatedThumbnails,
  history,
  loading,
  generating,
} = storeToRefs(store)

// Form state
const form = reactive({
  videoTitle: '',
  style: 'BOLD_TEXT' as ThumbnailStyle,
  platform: 'YOUTUBE',
  includeText: false,
  textOverlay: '',
  colorScheme: 'vibrant',
})

const keywordsInput = ref('')
const selectedThumbnailId = ref<number | null>(null)

// Parse comma-separated keywords
const parsedKeywords = computed(() => {
  return keywordsInput.value
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0)
})

const canGenerate = computed(() => {
  return form.videoTitle.trim().length > 0
})

// Color scheme options
const colorSchemes = computed(() => [
  { value: 'vibrant', label: t('thumbnailGenerator.colorSchemes.vibrant'), dotClass: 'bg-gradient-to-r from-red-500 to-yellow-500' },
  { value: 'dark', label: t('thumbnailGenerator.colorSchemes.dark'), dotClass: 'bg-gradient-to-r from-gray-700 to-gray-900' },
  { value: 'pastel', label: t('thumbnailGenerator.colorSchemes.pastel'), dotClass: 'bg-gradient-to-r from-pink-300 to-blue-300' },
  { value: 'monochrome', label: t('thumbnailGenerator.colorSchemes.monochrome'), dotClass: 'bg-gradient-to-r from-gray-400 to-gray-600' },
  { value: 'warm', label: t('thumbnailGenerator.colorSchemes.warm'), dotClass: 'bg-gradient-to-r from-orange-400 to-red-400' },
  { value: 'cool', label: t('thumbnailGenerator.colorSchemes.cool'), dotClass: 'bg-gradient-to-r from-blue-400 to-cyan-400' },
])

async function handleGenerate() {
  const canUse = await checkAndUse(3, t('thumbnailGenerator.title'))
  if (!canUse) return

  selectedThumbnailId.value = null

  try {
    await store.generate({
      videoTitle: form.videoTitle,
      style: form.style,
      keywords: parsedKeywords.value,
      platform: form.platform,
      includeText: form.includeText,
      textOverlay: form.includeText ? form.textOverlay : undefined,
      colorScheme: form.colorScheme,
    })
  } catch {
    notification.error(t('thumbnailGenerator.generateError'))
  }
}

function handleSelectThumbnail(thumbnailId: number) {
  selectedThumbnailId.value = thumbnailId
}

function handleDownload(thumbnail: GeneratedThumbnail) {
  if (thumbnail.imageUrl) {
    const link = document.createElement('a')
    link.href = thumbnail.imageUrl
    link.download = `thumbnail_${thumbnail.id}.png`
    link.click()
  }
}

onMounted(async () => {
  await Promise.allSettled([
    store.fetchTemplates(),
    store.fetchHistory(),
  ])
})
</script>
