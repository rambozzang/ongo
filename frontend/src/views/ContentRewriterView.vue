<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentRewriter.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentRewriter.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <div
          class="flex items-center gap-2 rounded-lg border px-4 py-2 text-sm"
          :class="isLow
            ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
            : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
        >
          <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
          <span class="text-gray-600 dark:text-gray-300">{{ $t('contentRewriter.creditsRemaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600 dark:text-primary-400'">
            {{ creditBalance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('contentRewriter.pageGuideTitle')"
      :items="($tm('contentRewriter.pageGuide') as string[])"
    />

    <!-- Two-Column Layout -->
    <div class="grid gap-6 desktop:grid-cols-5">
      <!-- Left: Input Form -->
      <div class="desktop:col-span-2">
        <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentRewriter.inputTitle') }}
          </h2>

          <div class="space-y-4">
            <!-- Source Text -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('contentRewriter.sourceTextLabel') }}
              </label>
              <textarea
                v-model="form.sourceText"
                rows="6"
                :placeholder="$t('contentRewriter.sourceTextPlaceholder')"
                class="w-full resize-y rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                :disabled="rewriting"
              />
              <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                {{ form.sourceText.length }} {{ $t('contentRewriter.characters') }}
              </p>
            </div>

            <!-- Source Type -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('contentRewriter.sourceTypeLabel') }}
              </label>
              <select
                v-model="form.sourceType"
                class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                :disabled="rewriting"
              >
                <option value="VIDEO_SCRIPT">{{ $t('contentRewriter.sourceType.VIDEO_SCRIPT') }}</option>
                <option value="BLOG">{{ $t('contentRewriter.sourceType.BLOG') }}</option>
                <option value="TRANSCRIPT">{{ $t('contentRewriter.sourceType.TRANSCRIPT') }}</option>
                <option value="RAW_TEXT">{{ $t('contentRewriter.sourceType.RAW_TEXT') }}</option>
              </select>
            </div>

            <!-- Output Formats -->
            <FormatSelector v-model="form.outputFormats" />

            <!-- Tone -->
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('contentRewriter.toneLabel') }}
              </label>
              <select
                v-model="form.tone"
                class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                :disabled="rewriting"
              >
                <option value="professional">{{ $t('contentRewriter.tone.professional') }}</option>
                <option value="casual">{{ $t('contentRewriter.tone.casual') }}</option>
                <option value="humorous">{{ $t('contentRewriter.tone.humorous') }}</option>
                <option value="educational">{{ $t('contentRewriter.tone.educational') }}</option>
              </select>
            </div>

            <!-- Generate Button -->
            <div class="flex items-center gap-3 pt-2">
              <button
                class="btn-primary inline-flex items-center gap-2"
                :disabled="!canGenerate || rewriting"
                @click="handleGenerate"
              >
                <SparklesIcon class="h-4 w-4" />
                {{ rewriting ? $t('contentRewriter.generating') : $t('contentRewriter.generateButton') }}
              </button>
              <span class="text-xs text-gray-400 dark:text-gray-500">
                {{ $t('contentRewriter.creditCost', { cost: creditCost }) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Results + History -->
      <div class="space-y-6 desktop:col-span-3">
        <!-- Results Section -->
        <div>
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentRewriter.resultsTitle') }}
          </h2>

          <!-- Loading -->
          <div v-if="rewriting" class="flex items-center justify-center py-12">
            <LoadingSpinner size="lg" />
          </div>

          <!-- Results List -->
          <div v-else-if="results.length > 0" class="space-y-4">
            <ResultCard
              v-for="item in results"
              :key="item.id"
              :result="item"
              @copy="handleCopy"
              @delete="handleDelete"
            />
          </div>

          <!-- Empty Results -->
          <div
            v-else
            class="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-6 py-12 text-center"
          >
            <DocumentTextIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-500" />
            <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ $t('contentRewriter.emptyResultsTitle') }}
            </h3>
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ $t('contentRewriter.emptyResultsDescription') }}
            </p>
          </div>
        </div>

        <!-- History Section -->
        <div>
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('contentRewriter.historyTitle') }}
          </h2>

          <!-- History Loading -->
          <div v-if="loading" class="flex items-center justify-center py-8">
            <LoadingSpinner />
          </div>

          <!-- History List -->
          <div v-else-if="history.length > 0" class="space-y-2">
            <HistoryItem
              v-for="item in history"
              :key="item.id"
              :item="item"
              @select="handleSelectHistory"
            />
          </div>

          <!-- Empty History -->
          <div
            v-else
            class="rounded-lg border border-dashed border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-4 py-8 text-center"
          >
            <ClockIcon class="mx-auto mb-2 h-8 w-8 text-gray-400 dark:text-gray-500" />
            <p class="text-sm text-gray-500 dark:text-gray-400">
              {{ $t('contentRewriter.emptyHistory') }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  SparklesIcon,
  DocumentTextIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import FormatSelector from '@/components/contentrewriter/FormatSelector.vue'
import ResultCard from '@/components/contentrewriter/ResultCard.vue'
import HistoryItem from '@/components/contentrewriter/HistoryItem.vue'
import { useContentRewriterStore } from '@/stores/contentRewriter'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { OutputFormat, RewriteResult } from '@/types/contentRewriter'

const { t } = useI18n({ useScope: 'global' })
const store = useContentRewriterStore()
const { balance: creditBalance, isLow, checkAndUse, fetchBalance } = useCredit()
const notification = useNotification()

const { results, history, rewriting, loading } = storeToRefs(store)

const form = reactive({
  sourceText: '',
  sourceType: 'RAW_TEXT' as 'VIDEO_SCRIPT' | 'BLOG' | 'TRANSCRIPT' | 'RAW_TEXT',
  outputFormats: [] as OutputFormat[],
  tone: 'professional',
})

const creditCost = computed(() => Math.max(form.outputFormats.length * 2, 2))

const canGenerate = computed(() => {
  return form.sourceText.trim().length > 0 && form.outputFormats.length > 0
})

async function handleGenerate() {
  const canUse = await checkAndUse(creditCost.value, t('contentRewriter.title'))
  if (!canUse) return

  try {
    await store.rewrite({
      sourceText: form.sourceText,
      sourceType: form.sourceType,
      outputFormats: form.outputFormats,
      targetPlatforms: [],
      tone: form.tone,
    })
    notification.success(t('contentRewriter.generateSuccess'))
    await fetchBalance()
  } catch {
    notification.error(t('contentRewriter.generateError'))
  }
}

function handleCopy(result: RewriteResult) {
  const text = result.hashtags.length > 0
    ? `${result.text}\n\n${result.hashtags.join(' ')}`
    : result.text
  navigator.clipboard.writeText(text).then(() => {
    notification.success(t('contentRewriter.copiedSuccess'))
  })
}

async function handleDelete(result: RewriteResult) {
  try {
    await store.deleteResult(result.id)
    notification.success(t('contentRewriter.deletedSuccess'))
  } catch {
    notification.error(t('contentRewriter.deleteError'))
  }
}

function handleSelectHistory(item: RewriteResult) {
  results.value = [item]
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  store.fetchHistory()
  fetchBalance()
})
</script>
