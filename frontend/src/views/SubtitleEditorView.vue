<template>
  <!-- Mobile Layout -->
  <div v-if="!isTablet" class="space-y-4">
    <!-- Header -->
    <div>
      <h1 class="text-lg font-bold text-gray-900 dark:text-gray-100">
        {{ $t('subtitleEditor.title') }}
      </h1>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ $t('subtitleEditor.description') }}
      </p>
    </div>

    <PageGuide
      :title="$t('subtitleEditor.pageGuideTitle')"
      :items="($tm('subtitleEditor.pageGuide') as string[])"
    />

    <!-- Credit Display -->
    <div
      class="flex items-center gap-2 rounded-lg border px-3 py-2 text-xs"
      :class="isLow
        ? 'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800'"
    >
      <SparklesIcon class="h-4 w-4" :class="isLow ? 'text-red-500' : 'text-primary-600'" />
      <span class="text-gray-600 dark:text-gray-300">{{ $t('subtitleEditor.remaining') }}</span>
      <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
        {{ balance.toLocaleString() }}
      </span>
    </div>

    <!-- Error Message -->
    <div
      v-if="error"
      class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400"
    >
      {{ error }}
      <button class="ml-2 underline" @click="store.clearError()">{{ $t('subtitleEditor.dismiss') }}</button>
    </div>

    <!-- Video Selector -->
    <VideoSelector
      :videos="videos"
      :selected-video="selectedVideo"
      @select="handleSelectVideo"
      @change="handleChangeVideo"
    />

    <!-- Generate Panel -->
    <GeneratePanel
      v-if="selectedVideo"
      :processing="processing"
      :selected-video="selectedVideo"
      @generate="handleGenerate"
    />

    <!-- Subtitle Tracks List -->
    <div v-if="selectedVideo" class="space-y-3">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('subtitleEditor.subtitleTracks') }}
      </h3>
      <TrackList
        :tracks="selectedVideoTracks"
        :selected-track="selectedTrack"
        @select="handleSelectTrack"
        @delete="handleDeleteTrack"
      />
    </div>

    <!-- Timeline -->
    <SubtitleTimeline
      v-if="selectedTrack && selectedTrack.cues.length > 0"
      :cues="selectedTrack.cues"
      :total-duration="selectedTrack.totalDuration"
      :selected-cue-id="selectedCueId"
      @select-cue="handleSelectCue"
    />

    <!-- Cue Editor -->
    <div v-if="selectedTrack" class="space-y-3">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('subtitleEditor.cueEditor') }}
        </h3>
        <ExportButton
          v-if="selectedTrack.cues.length > 0"
          @export="handleExport"
        />
      </div>
      <SubtitleCueList
        :cues="selectedTrack.cues"
        :selected-cue-id="selectedCueId"
        @select-cue="handleSelectCue"
        @update-cue="handleUpdateCue"
        @delete-cue="handleDeleteCue"
      />
    </div>
  </div>

  <!-- Desktop/Tablet Layout -->
  <div v-else>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('subtitleEditor.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('subtitleEditor.description') }}
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
          <span class="text-gray-600 dark:text-gray-300">{{ $t('subtitleEditor.remaining') }}</span>
          <span class="font-bold" :class="isLow ? 'text-red-600' : 'text-primary-600'">
            {{ balance.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <PageGuide
      :title="$t('subtitleEditor.pageGuideTitle')"
      :items="($tm('subtitleEditor.pageGuide') as string[])"
    />

    <!-- Error Message -->
    <div
      v-if="error"
      class="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400"
    >
      {{ error }}
      <button class="ml-2 underline" @click="store.clearError()">{{ $t('subtitleEditor.dismiss') }}</button>
    </div>

    <!-- Two Column Layout -->
    <div class="grid grid-cols-12 gap-6">
      <!-- Left: Video + Controls -->
      <div class="col-span-4 space-y-4">
        <!-- Video Selector -->
        <VideoSelector
          :videos="videos"
          :selected-video="selectedVideo"
          @select="handleSelectVideo"
          @change="handleChangeVideo"
        />

        <!-- Generate Panel -->
        <GeneratePanel
          v-if="selectedVideo"
          :processing="processing"
          :selected-video="selectedVideo"
          @generate="handleGenerate"
        />

        <!-- Subtitle Tracks List -->
        <div v-if="selectedVideo" class="space-y-3">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('subtitleEditor.subtitleTracks') }}
          </h3>
          <TrackList
            :tracks="selectedVideoTracks"
            :selected-track="selectedTrack"
            @select="handleSelectTrack"
            @delete="handleDeleteTrack"
          />
        </div>
      </div>

      <!-- Right: Editor -->
      <div class="col-span-8 space-y-4">
        <!-- Timeline -->
        <SubtitleTimeline
          v-if="selectedTrack && selectedTrack.cues.length > 0"
          :cues="selectedTrack.cues"
          :total-duration="selectedTrack.totalDuration"
          :selected-cue-id="selectedCueId"
          @select-cue="handleSelectCue"
        />

        <!-- Cue Editor -->
        <div v-if="selectedTrack" class="space-y-3">
          <div class="flex items-center justify-between">
            <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('subtitleEditor.cueEditor') }}
              <span class="ml-2 text-xs font-normal text-gray-500 dark:text-gray-400">
                ({{ selectedTrack.cues.length }} cues)
              </span>
            </h3>
            <ExportButton
              v-if="selectedTrack.cues.length > 0"
              @export="handleExport"
            />
          </div>
          <div class="max-h-[600px] overflow-y-auto rounded-lg border border-gray-200 p-3 dark:border-gray-700">
            <SubtitleCueList
              :cues="selectedTrack.cues"
              :selected-cue-id="selectedCueId"
              @select-cue="handleSelectCue"
              @update-cue="handleUpdateCue"
              @delete-cue="handleDeleteCue"
            />
          </div>
        </div>

        <!-- Empty State -->
        <div
          v-if="!selectedTrack"
          class="rounded-lg border border-dashed border-gray-300 bg-gray-50 px-6 py-16 text-center dark:border-gray-600 dark:bg-gray-900"
        >
          <LanguageIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-500" />
          <h3 class="mb-1 text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ $t('subtitleEditor.cueEditor') }}
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('subtitleEditor.selectTrack') }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import { SparklesIcon, LanguageIcon } from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import GeneratePanel from '@/components/subtitleeditor/GeneratePanel.vue'
import SubtitleCueList from '@/components/subtitleeditor/SubtitleCueList.vue'
import SubtitleTimeline from '@/components/subtitleeditor/SubtitleTimeline.vue'
import TrackList from '@/components/subtitleeditor/TrackList.vue'
import ExportButton from '@/components/subtitleeditor/ExportButton.vue'
import VideoSelector from '@/components/subtitleeditor/VideoSelector.vue'
import { useSubtitleEditorStore } from '@/stores/subtitleEditor'
import { useCredit } from '@/composables/useCredit'
import type { SubtitleLanguage, SubtitleFormat, SubtitleTrack, VideoForSubtitle } from '@/types/subtitleEditor'

useI18n({ useScope: 'global' })
const store = useSubtitleEditorStore()
const { balance, isLow, checkAndUse, fetchBalance } = useCredit()

const isTablet = useMediaQuery('(min-width: 768px)')

const {
  videos,
  selectedVideo,
  selectedTrack,
  selectedVideoTracks,
  processing,
  error,
} = storeToRefs(store)

const selectedCueId = ref<string | null>(null)

// --- Handlers ---
function handleSelectVideo(id: number) {
  const video = videos.value.find((v: VideoForSubtitle) => v.id === id)
  if (video) {
    store.selectVideo(video)
    store.fetchTracks()
  }
}

function handleChangeVideo() {
  store.selectVideo(null)
  selectedCueId.value = null
}

function handleSelectTrack(track: SubtitleTrack) {
  store.selectTrack(track)
  selectedCueId.value = null
}

async function handleDeleteTrack(trackId: number) {
  await store.deleteTrack(trackId)
}

async function handleGenerate(params: { language: SubtitleLanguage; translateTo?: SubtitleLanguage; speakerDiarization: boolean }) {
  const canUse = await checkAndUse(10, 'AI 자막 생성')
  if (!canUse) return

  await store.generateSubtitles(params.language, params.translateTo, params.speakerDiarization)
  await fetchBalance()
}

function handleSelectCue(id: string) {
  selectedCueId.value = id
}

async function handleUpdateCue(cueId: string, text: string) {
  await store.updateCue(cueId, text)
}

async function handleDeleteCue(cueId: string) {
  await store.deleteCue(cueId)
  if (selectedCueId.value === cueId) {
    selectedCueId.value = null
  }
}

async function handleExport(format: string) {
  await store.exportSubtitles(format as SubtitleFormat)
}

onMounted(() => {
  store.fetchVideos()
  fetchBalance()
})
</script>
