<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('hashtagStrategy.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('hashtagStrategy.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('hashtagStrategy.creditsRemaining') }}: {{ creditBalance }}
        </span>
      </div>
    </div>

    <PageGuide :title="$t('hashtagStrategy.pageGuideTitle')" :items="($tm('hashtagStrategy.pageGuide') as string[])" />

    <!-- Input Section -->
    <div class="card mb-6">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('hashtagStrategy.inputTitle') }}
      </h2>

      <div class="grid gap-4 tablet:grid-cols-2">
        <!-- Content Description -->
        <div class="tablet:col-span-2">
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('hashtagStrategy.contentLabel') }}
          </label>
          <textarea
            v-model="form.content"
            rows="3"
            :placeholder="$t('hashtagStrategy.contentPlaceholder')"
            class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 resize-none"
          />
        </div>

        <!-- Platform -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('hashtagStrategy.platformLabel') }}
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

        <!-- Target Audience -->
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('hashtagStrategy.audienceLabel') }}
          </label>
          <input
            v-model="form.targetAudience"
            type="text"
            :placeholder="$t('hashtagStrategy.audiencePlaceholder')"
            class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
          />
        </div>
      </div>

      <!-- Generate Button -->
      <div class="mt-4 flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="!canAnalyze || store.analyzing"
          @click="handleAnalyze"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ store.analyzing ? $t('hashtagStrategy.analyzing') : $t('hashtagStrategy.generateButton') }}
        </button>
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('hashtagStrategy.creditCost', { cost: 3 }) }}
        </span>
      </div>
    </div>

    <!-- Analysis Results -->
    <div v-if="store.analysisResult.length > 0" class="mb-6">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('hashtagStrategy.resultsTitle') }}
      </h2>
      <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <HashtagSetCard
          v-for="set in store.analysisResult"
          :key="set.id"
          :set="set"
          @copy="handleCopy"
          @save="handleSave"
        />
      </div>
    </div>

    <!-- Saved Sets History -->
    <div v-if="store.savedSets.length > 0">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('hashtagStrategy.savedSetsTitle') }}
      </h2>
      <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <HashtagSetCard
          v-for="set in store.savedSets"
          :key="set.id"
          :set="set"
          :is-saved="true"
          @copy="handleCopy"
          @delete="handleDelete"
        />
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-if="store.analysisResult.length === 0 && store.savedSets.length === 0 && !store.analyzing"
      class="py-12 text-center"
    >
      <HashtagIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
      <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('hashtagStrategy.emptyState') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { SparklesIcon, HashtagIcon } from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import HashtagSetCard from '@/components/hashtagstrategy/HashtagSetCard.vue'
import { useHashtagStrategyStore } from '@/stores/hashtagStrategy'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { HashtagSet } from '@/types/hashtagStrategy'

const { t } = useI18n({ useScope: 'global' })
const store = useHashtagStrategyStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()

const form = reactive({
  content: '',
  platform: 'YOUTUBE',
  targetAudience: '',
})

const canAnalyze = computed(() => {
  return form.content.trim().length > 0
})

async function handleAnalyze() {
  const canUse = await checkAndUse(3, t('hashtagStrategy.title'))
  if (!canUse) return

  try {
    await store.analyze({
      content: form.content,
      platform: form.platform,
      targetAudience: form.targetAudience || undefined,
    })
  } catch {
    notification.error(t('hashtagStrategy.analyzeError'))
  }
}

function handleCopy(set: HashtagSet) {
  const text = set.hashtags.map((h) => h.tag).join(' ')
  navigator.clipboard.writeText(text).then(() => {
    notification.success(t('hashtagStrategy.copiedSuccess'))
  })
}

async function handleSave(set: HashtagSet) {
  try {
    await store.saveSet(set)
    notification.success(t('hashtagStrategy.savedSuccess'))
  } catch {
    notification.error(t('hashtagStrategy.saveError'))
  }
}

async function handleDelete(set: HashtagSet) {
  try {
    await store.deleteSet(set.id)
    notification.success(t('hashtagStrategy.deletedSuccess'))
  } catch {
    notification.error(t('hashtagStrategy.deleteError'))
  }
}

onMounted(() => {
  store.fetchSavedSets()
})
</script>
