<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  LanguageIcon,
  CheckCircleIcon,
  ArrowPathIcon,
  SparklesIcon,
  CreditCardIcon,
  PlusIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import { useSubtitleTranslationStore } from '@/stores/subtitleTranslation'
import { useLocale } from '@/composables/useLocale'
import TranslationCard from '@/components/subtitletranslation/TranslationCard.vue'
import TranslationLineEditor from '@/components/subtitletranslation/TranslationLineEditor.vue'
import LanguageSelector from '@/components/subtitletranslation/LanguageSelector.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const { t } = useLocale()
const store = useSubtitleTranslationStore()
const { translations, currentLines, supportedLanguages, summary, isLoading } = storeToRefs(store)

const statusFilter = ref<string>('ALL')
const showCreateModal = ref(false)
const showDetailPanel = ref(false)
const selectedTranslationId = ref<number | null>(null)

// Create form
const newVideoId = ref(0)
const newVideoTitle = ref('')
const newSourceLang = ref('ko')
const newTargetLang = ref('')

const statusFilters = [
  { value: 'ALL', label: t('subtitleTranslation.filterAll') },
  { value: 'PENDING', label: t('subtitleTranslation.filterPending') },
  { value: 'TRANSLATING', label: t('subtitleTranslation.filterTranslating') },
  { value: 'COMPLETED', label: t('subtitleTranslation.filterCompleted') },
  { value: 'REVIEWING', label: t('subtitleTranslation.filterReviewing') },
]

const filteredTranslations = computed(() => {
  if (statusFilter.value === 'ALL') return translations.value
  return translations.value.filter((t) => t.status === statusFilter.value)
})

const handleFilterChange = (status: string) => {
  statusFilter.value = status
  if (status === 'ALL') {
    store.fetchTranslations()
  } else {
    store.fetchTranslations(status)
  }
}

const openCreateModal = () => {
  newVideoId.value = 0
  newVideoTitle.value = ''
  newSourceLang.value = 'ko'
  newTargetLang.value = ''
  showCreateModal.value = true
}

const closeCreateModal = () => {
  showCreateModal.value = false
}

const handleCreate = async () => {
  if (!newVideoTitle.value.trim() || !newTargetLang.value) return
  await store.createTranslation({
    videoId: newVideoId.value || Date.now(),
    videoTitle: newVideoTitle.value.trim(),
    sourceLanguage: newSourceLang.value,
    targetLanguage: newTargetLang.value,
  })
  closeCreateModal()
}

const handleCardClick = async (id: number) => {
  selectedTranslationId.value = id
  await store.fetchLines(id)
  showDetailPanel.value = true
}

const closeDetailPanel = () => {
  showDetailPanel.value = false
  selectedTranslationId.value = null
}

const handleLineUpdate = (lineId: number, translatedText: string) => {
  if (selectedTranslationId.value) {
    store.updateLine(selectedTranslationId.value, lineId, translatedText)
  }
}

onMounted(() => {
  store.fetchTranslations()
  store.fetchSupportedLanguages()
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
            {{ $t('subtitleTranslation.title') }}
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('subtitleTranslation.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="openCreateModal"
        >
          <PlusIcon class="h-5 w-5" />
          {{ $t('subtitleTranslation.newRequest') }}
        </button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <LanguageIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleTranslation.totalTranslations') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalTranslations.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckCircleIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleTranslation.completed') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.completedCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowPathIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleTranslation.inProgress') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-blue-600 dark:text-blue-400">
          {{ summary.inProgressCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <SparklesIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleTranslation.avgQuality') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgQuality.toFixed(1) }}{{ $t('subtitleTranslation.pointUnit') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CreditCardIcon class="h-5 w-5 text-purple-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('subtitleTranslation.creditsUsed') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-purple-600 dark:text-purple-400">
          {{ summary.totalCreditsUsed.toLocaleString('ko-KR') }}
        </p>
      </div>
    </div>

    <!-- Status Filter Tabs -->
    <div class="mb-4 flex flex-wrap gap-2">
      <button
        v-for="filter in statusFilters"
        :key="filter.value"
        :class="[
          'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
          statusFilter === filter.value
            ? 'bg-primary-600 text-white'
            : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700',
        ]"
        @click="handleFilterChange(filter.value)"
      >
        {{ filter.label }}
      </button>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Translation Cards Grid -->
    <div v-else-if="filteredTranslations.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
      <TranslationCard
        v-for="t in filteredTranslations"
        :key="t.id"
        :translation="t"
        :languages="supportedLanguages"
        @click="handleCardClick"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <LanguageIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('subtitleTranslation.noTranslations') }}
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ $t('subtitleTranslation.noTranslationsDesc') }}
      </p>
      <button
        class="btn-primary mt-4 inline-flex items-center gap-2"
        @click="openCreateModal"
      >
        <PlusIcon class="h-5 w-5" />
        {{ $t('subtitleTranslation.startFirstTranslation') }}
      </button>
    </div>

    <!-- Create Translation Modal -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showCreateModal"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="closeCreateModal"
        >
          <div class="w-full max-w-lg rounded-xl bg-white p-6 shadow-xl dark:bg-gray-900">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ $t('subtitleTranslation.newRequest') }}
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeCreateModal"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
                  {{ $t('subtitleTranslation.videoTitle') }}
                </label>
                <input
                  v-model="newVideoTitle"
                  type="text"
                  :placeholder="$t('subtitleTranslation.videoTitlePlaceholder')"
                  class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-100 dark:placeholder-gray-500"
                />
              </div>

              <LanguageSelector
                v-model="newSourceLang"
                :label="$t('subtitleTranslation.sourceLanguage')"
                :languages="[{ code: 'ko', name: 'Korean', nativeName: '한국어' }, ...supportedLanguages]"
                :placeholder="$t('subtitleTranslation.sourceLanguagePlaceholder')"
              />

              <LanguageSelector
                v-model="newTargetLang"
                :label="$t('subtitleTranslation.targetLanguage')"
                :languages="supportedLanguages"
                :placeholder="$t('subtitleTranslation.targetLanguagePlaceholder')"
              />
            </div>

            <div class="mt-6 flex items-center justify-end gap-3">
              <button
                class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
                @click="closeCreateModal"
              >
                {{ $t('subtitleTranslation.cancel') }}
              </button>
              <button
                class="btn-primary text-sm"
                :disabled="!newVideoTitle.trim() || !newTargetLang"
                @click="handleCreate"
              >
                {{ $t('subtitleTranslation.requestTranslation') }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Translation Detail Side Panel -->
    <Teleport to="body">
      <Transition name="fade">
        <div
          v-if="showDetailPanel"
          class="fixed inset-0 z-50 flex justify-end bg-black/50"
          @click.self="closeDetailPanel"
        >
          <div class="h-full w-full max-w-xl overflow-y-auto bg-white p-6 shadow-xl dark:bg-gray-900">
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                {{ $t('subtitleTranslation.editLines') }}
              </h2>
              <button
                class="rounded-lg p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
                @click="closeDetailPanel"
              >
                <XMarkIcon class="h-5 w-5" />
              </button>
            </div>

            <div v-if="currentLines.length > 0" class="space-y-3">
              <TranslationLineEditor
                v-for="line in currentLines"
                :key="line.id"
                :line="line"
                @update="handleLineUpdate"
              />
            </div>

            <div
              v-else
              class="py-12 text-center"
            >
              <LanguageIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ $t('subtitleTranslation.noLines') }}
              </p>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
