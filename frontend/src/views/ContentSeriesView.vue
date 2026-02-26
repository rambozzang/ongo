<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  PlusIcon,
  FilmIcon,
  XMarkIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import SeriesCard from '@/components/contentseries/SeriesCard.vue'
import EpisodeList from '@/components/contentseries/EpisodeList.vue'
import SeriesAnalyticsPanel from '@/components/contentseries/SeriesAnalyticsPanel.vue'
import { useContentSeriesStore } from '@/stores/contentSeries'
import type { ContentSeries, CreateSeriesRequest } from '@/types/contentSeries'

const store = useContentSeriesStore()

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
  if (confirm('이 시리즈를 삭제하시겠습니까?')) {
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
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('contentSeries.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('contentSeries.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          @click="openCreate"
          class="btn-primary inline-flex items-center gap-2"
        >
          <PlusIcon class="w-5 h-5" />
          {{ $t('contentSeries.newSeries') }}
        </button>
      </div>
    </div>

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
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showCreateModal"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showCreateModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showCreateModal"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Modal Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
                <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
                  {{ $t('contentSeries.createModalTitle') }}
                </h2>
                <button
                  @click="showCreateModal = false"
                  class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                >
                  <XMarkIcon class="w-5 h-5" />
                </button>
              </div>

              <!-- Modal Body -->
              <div class="flex-1 overflow-y-auto px-6 py-4 space-y-4">
                <!-- Title -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formTitle') }}
                  </label>
                  <input
                    v-model="form.title"
                    type="text"
                    :placeholder="$t('contentSeries.formTitlePlaceholder')"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Description -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formDescription') }}
                  </label>
                  <textarea
                    v-model="form.description"
                    rows="3"
                    :placeholder="$t('contentSeries.formDescriptionPlaceholder')"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400 resize-none"
                  ></textarea>
                </div>

                <!-- Platform -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formPlatform') }}
                  </label>
                  <select
                    v-model="form.platform"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option value="youtube">YouTube</option>
                    <option value="tiktok">TikTok</option>
                    <option value="instagram">Instagram</option>
                    <option value="naverclip">Naver Clip</option>
                  </select>
                </div>

                <!-- Frequency -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formFrequency') }}
                  </label>
                  <select
                    v-model="form.frequency"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  >
                    <option value="DAILY">{{ $t('contentSeries.freqDaily') }}</option>
                    <option value="WEEKLY">{{ $t('contentSeries.freqWeekly') }}</option>
                    <option value="BIWEEKLY">{{ $t('contentSeries.freqBiweekly') }}</option>
                    <option value="MONTHLY">{{ $t('contentSeries.freqMonthly') }}</option>
                    <option value="CUSTOM">{{ $t('contentSeries.freqCustom') }}</option>
                  </select>
                </div>

                <!-- Planned Episodes -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formPlannedEpisodes') }}
                  </label>
                  <input
                    v-model.number="form.plannedEpisodes"
                    type="number"
                    min="1"
                    max="999"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                  />
                </div>

                <!-- Tags -->
                <div>
                  <label class="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                    {{ $t('contentSeries.formTags') }}
                  </label>
                  <div class="flex items-center gap-2">
                    <div class="relative flex-1">
                      <TagIcon class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                      <input
                        v-model="tagInput"
                        type="text"
                        :placeholder="$t('contentSeries.formTagPlaceholder')"
                        class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 pl-9 pr-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-primary-500 dark:focus:ring-primary-400"
                        @keydown.enter.prevent="addTag"
                      />
                    </div>
                    <button
                      type="button"
                      @click="addTag"
                      class="btn-secondary px-3 py-2 text-sm"
                    >
                      {{ $t('contentSeries.add') }}
                    </button>
                  </div>
                  <div v-if="form.tags && form.tags.length > 0" class="flex flex-wrap gap-1.5 mt-2">
                    <span
                      v-for="tag in form.tags"
                      :key="tag"
                      class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300"
                    >
                      #{{ tag }}
                      <button @click="removeTag(tag)" class="hover:text-primary-900 dark:hover:text-primary-100">
                        <XMarkIcon class="w-3 h-3" />
                      </button>
                    </span>
                  </div>
                </div>
              </div>

              <!-- Modal Footer -->
              <div class="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-end gap-3">
                <button
                  @click="showCreateModal = false"
                  class="btn-secondary"
                >
                  {{ $t('contentSeries.cancel') }}
                </button>
                <button
                  @click="handleCreate"
                  :disabled="!form.title.trim()"
                  class="btn-primary"
                >
                  {{ $t('contentSeries.create') }}
                </button>
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>

    <!-- ============ Detail / Analytics Modal ============ -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition-opacity duration-200"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition-opacity duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDetailModal && selectedSeries"
          class="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
          @click="showDetailModal = false"
        >
          <Transition
            enter-active-class="transition-all duration-200"
            enter-from-class="opacity-0 scale-95"
            enter-to-class="opacity-100 scale-100"
            leave-active-class="transition-all duration-200"
            leave-from-class="opacity-100 scale-100"
            leave-to-class="opacity-0 scale-95"
          >
            <div
              v-if="showDetailModal && selectedSeries"
              class="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col"
              @click.stop
            >
              <!-- Detail Header -->
              <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
                <div class="flex items-start justify-between">
                  <div class="flex-1 min-w-0">
                    <h2 class="text-xl font-semibold text-gray-900 dark:text-gray-100 truncate">
                      {{ selectedSeries.title }}
                    </h2>
                    <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {{ selectedSeries.description }}
                    </p>
                  </div>
                  <button
                    @click="showDetailModal = false"
                    class="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors ml-4"
                  >
                    <XMarkIcon class="w-5 h-5" />
                  </button>
                </div>
              </div>

              <!-- Detail Body -->
              <div class="flex-1 overflow-y-auto px-6 py-6 space-y-6">
                <!-- Episodes -->
                <EpisodeList
                  :episodes="selectedSeries.episodes"
                  @select-episode="() => {}"
                />

                <!-- Analytics -->
                <SeriesAnalyticsPanel
                  v-if="store.analytics && store.analytics.seriesId === selectedSeries.id"
                  :analytics="store.analytics"
                />
              </div>
            </div>
          </Transition>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>
