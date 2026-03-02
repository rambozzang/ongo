<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  FilmIcon,
  XMarkIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import SeriesCard from '@/components/contentseries/SeriesCard.vue'
import EpisodeList from '@/components/contentseries/EpisodeList.vue'
import SeriesAnalyticsPanel from '@/components/contentseries/SeriesAnalyticsPanel.vue'
import { useContentSeriesStore } from '@/stores/contentSeries'
import { useLocale } from '@/composables/useLocale'
import type { ContentSeries, CreateSeriesRequest } from '@/types/contentSeries'

const store = useContentSeriesStore()
const { t } = useLocale()

const showCreateModal = ref(false)
const showDetailModal = ref(false)
const selectedSeries = ref<ContentSeries | null>(null)

/* ---- Create form ---- */
const form = ref<CreateSeriesRequest>({
  title: '',
  description: '',
  platform: 'youtube',
  frequency: 'WEEKLY',
  plannedEpisodes: 12,
  tags: [],
})
const tagInput = ref('')

onMounted(() => {
  store.fetchAll()
})

const activeList = computed(() => store.activeSeries)
const completedList = computed(() => store.completedSeries)

/* ---- Handlers ---- */
function openCreate() {
  form.value = {
    title: '',
    description: '',
    platform: 'youtube',
    frequency: 'WEEKLY',
    plannedEpisodes: 12,
    tags: [],
  }
  tagInput.value = ''
  showCreateModal.value = true
}

async function handleCreate() {
  if (!form.value.title.trim()) return
  await store.createSeries({ ...form.value })
  showCreateModal.value = false
}

function handleSelect(id: number) {
  const series = store.seriesList.find((s) => s.id === id)
  if (series) {
    selectedSeries.value = series
    store.fetchAnalytics(id)
    showDetailModal.value = true
  }
}

function handleDelete(id: number) {
  if (confirm(t('contentSeries.deleteConfirm'))) {
    store.deleteSeries(id)
    if (selectedSeries.value?.id === id) {
      showDetailModal.value = false
      selectedSeries.value = null
    }
  }
}

function addTag() {
  const tag = tagInput.value.trim()
  if (tag && !form.value.tags?.includes(tag)) {
    form.value.tags = [...(form.value.tags ?? []), tag]
  }
  tagInput.value = ''
}

function removeTag(tag: string) {
  form.value.tags = form.value.tags?.filter((t) => t !== tag)
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('contentSeries.title')" :description="$t('contentSeries.description')">
      <template #actions>
        <button
          @click="openCreate"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('contentSeries.newSeries') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('contentSeries.pageGuideTitle')" :items="($tm('contentSeries.pageGuide') as string[])" />

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <div v-else class="mt-6 space-y-8">

      <!-- Active Series -->
      <section>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          {{ $t('contentSeries.activeSeries') }}
          <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ activeList.length }})</span>
        </h2>

        <div v-if="activeList.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
          <SeriesCard
            v-for="series in activeList"
            :key="series.id"
            :series="series"
            @select="handleSelect"
            @delete="handleDelete"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
        >
          <FilmIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
          <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
            {{ $t('contentSeries.emptyActiveTitle') }}
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">
            {{ $t('contentSeries.emptyActiveDesc') }}
          </p>
          <button @click="openCreate" class="btn-primary inline-flex items-center gap-2">
            <PlusIcon class="w-5 h-5" />
            {{ $t('contentSeries.createFirst') }}
          </button>
        </div>
      </section>

      <!-- Completed / Archived -->
      <section v-if="completedList.length > 0">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
          {{ $t('contentSeries.completedArchived') }}
          <span class="text-sm font-normal text-gray-500 dark:text-gray-400 ml-1">({{ completedList.length }})</span>
        </h2>

        <div class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
          <SeriesCard
            v-for="series in completedList"
            :key="series.id"
            :series="series"
            @select="handleSelect"
            @delete="handleDelete"
          />
        </div>
      </section>
    </div>

    <!-- ============ Create Series Modal ============ -->
    <BaseModal v-model="showCreateModal" :title="$t('contentSeries.createModalTitle')" max-width="lg">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formTitle') }}</label>
          <input v-model="form.title" type="text" :placeholder="$t('contentSeries.formTitlePlaceholder')" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formDescription') }}</label>
          <textarea v-model="form.description" rows="3" :placeholder="$t('contentSeries.formDescriptionPlaceholder')" class="input-field resize-none" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formPlatform') }}</label>
          <select v-model="form.platform" class="input-field">
            <option value="youtube">YouTube</option>
            <option value="tiktok">TikTok</option>
            <option value="instagram">Instagram</option>
            <option value="naverclip">Naver Clip</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formFrequency') }}</label>
          <select v-model="form.frequency" class="input-field">
            <option value="DAILY">{{ $t('contentSeries.freqDaily') }}</option>
            <option value="WEEKLY">{{ $t('contentSeries.freqWeekly') }}</option>
            <option value="BIWEEKLY">{{ $t('contentSeries.freqBiweekly') }}</option>
            <option value="MONTHLY">{{ $t('contentSeries.freqMonthly') }}</option>
            <option value="CUSTOM">{{ $t('contentSeries.freqCustom') }}</option>
          </select>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formPlannedEpisodes') }}</label>
          <input v-model.number="form.plannedEpisodes" type="number" min="1" max="999" class="input-field" />
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">{{ $t('contentSeries.formTags') }}</label>
          <div class="flex items-center gap-2">
            <div class="relative flex-1">
              <TagIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input v-model="tagInput" type="text" :placeholder="$t('contentSeries.formTagPlaceholder')" class="input-field pl-9" @keydown.enter.prevent="addTag" />
            </div>
            <button type="button" @click="addTag" class="btn-secondary px-3 py-2 text-sm">{{ $t('contentSeries.add') }}</button>
          </div>
          <div v-if="form.tags && form.tags.length > 0" class="flex flex-wrap gap-1.5 mt-2">
            <span v-for="tag in form.tags" :key="tag" class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
              #{{ tag }}
              <button @click="removeTag(tag)" class="hover:text-primary-900 dark:hover:text-primary-100"><XMarkIcon class="w-3 h-3" /></button>
            </span>
          </div>
        </div>
      </div>
      <template #footer>
        <button @click="showCreateModal = false" class="btn-secondary">{{ $t('contentSeries.cancel') }}</button>
        <button @click="handleCreate" :disabled="!form.title.trim()" class="btn-primary">{{ $t('contentSeries.create') }}</button>
      </template>
    </BaseModal>

    <!-- ============ Detail / Analytics Modal ============ -->
    <BaseModal v-model="showDetailModal" :title="selectedSeries?.title ?? ''" max-width="xl">
      <template v-if="selectedSeries">
        <p class="text-sm text-gray-600 dark:text-gray-400 mb-6">{{ selectedSeries.description }}</p>
        <div class="space-y-6">
          <EpisodeList :episodes="selectedSeries.episodes" @select-episode="() => {}" />
          <SeriesAnalyticsPanel
            v-if="store.analytics && store.analytics.seriesId === selectedSeries.id"
            :analytics="store.analytics"
          />
        </div>
      </template>
    </BaseModal>
  </div>
</template>
