<template>
  <div>
    <PageHeader :title="$t('subtitleEditor.title')" :description="$t('subtitleEditor.description')" />

    <PageGuide
      :title="$t('subtitleEditor.pageGuideTitle')"
      :items="($tm('subtitleEditor.pageGuide') as string[])"
    />

    <!-- 영상 선택 -->
    <section class="card mb-4">
      <label
        class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300"
        for="subtitle-video"
      >
        {{ $t('subtitleEditor.selectVideo') }}
      </label>
      <select
        id="subtitle-video"
        v-model.number="selectedVideoId"
        class="input-field w-full"
        @change="onVideoChange"
      >
        <option :value="0" disabled>{{ $t('subtitleEditor.selectVideoPlaceholder') }}</option>
        <option v-for="v in videos" :key="v.id" :value="v.id">{{ v.title }}</option>
      </select>
      <p v-if="!videosLoading && videos.length === 0" class="mt-1 text-body-xs text-gray-400">
        {{ $t('subtitleEditor.noVideos') }}
      </p>
    </section>

    <!-- 자막 트랙 목록 -->
    <section v-if="selectedVideoId > 0" class="card mb-4">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <h2 class="font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('subtitleEditor.subtitleTracks') }}
        </h2>
        <div class="ml-auto flex items-center gap-2">
          <select v-model="newTrackLanguage" class="input-field" aria-label="language">
            <option v-for="lang in LANGUAGES" :key="lang" :value="lang">
              {{ $t(`subtitleEditor.generate.languageOptions.${lang}`) }}
            </option>
          </select>
          <button class="btn-primary" :disabled="creating" @click="createTrack">
            {{ creating ? $t('subtitleEditor.creating') : $t('subtitleEditor.createTrack') }}
          </button>
        </div>
      </div>

      <LoadingSpinner v-if="tracksLoading" />
      <EmptyState
        v-else-if="tracks.length === 0"
        variant="compact"
        :title="$t('subtitleEditor.noTracks')"
        :description="$t('subtitleEditor.noTracksDescription')"
      />
      <ul v-else class="space-y-2">
        <li
          v-for="track in tracks"
          :key="track.id"
          class="flex flex-wrap items-center gap-2 rounded-xl border p-3 transition-colors"
          :class="track.id === selectedTrackId
            ? 'border-primary-500 ring-2 ring-primary-500/30'
            : 'border-gray-200 hover:border-primary-300 dark:border-gray-700 dark:hover:border-primary-700'"
        >
          <button type="button" class="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-left" @click="selectTrack(track)">
            <span class="font-medium text-gray-900 dark:text-gray-100">
              {{ languageLabel(track.language) }}
            </span>
            <span :class="['rounded-full px-2 py-0.5 text-caption', statusBadgeClass(track.status)]">
              {{ $t(`subtitleEditor.status.${track.status}`) }}
            </span>
            <span class="text-body-xs text-gray-400">
              {{ $t('subtitleEditor.trackCues', { count: cueCountOf(track) }) }}
            </span>
            <span class="text-body-xs text-gray-400">
              {{ $t('subtitleEditor.trackWords', { count: track.wordCount }) }}
            </span>
            <span v-if="track.updatedAt" class="text-body-xs text-gray-400">
              {{ $t('subtitleEditor.lastModified') }}: {{ formatDate(track.updatedAt) }}
            </span>
          </button>
          <button
            type="button"
            class="btn-danger inline-flex items-center gap-1"
            @click="askDeleteTrack(track)"
          >
            <TrashIcon class="h-4 w-4" />
            {{ $t('subtitleEditor.deleteTrack') }}
          </button>
        </li>
      </ul>
    </section>

    <!-- 큐 편집 / 납출 -->
    <section v-if="selectedTrack" class="card">
      <OTabs v-model="activeTab" :tabs="tabs" :aria-label="$t('subtitleEditor.cueEditor')" class="mb-4" />

      <!-- 편집 탭 -->
      <div v-if="activeTab === 'edit'">
        <EmptyState
          v-if="cues.length === 0"
          variant="compact"
          :title="$t('subtitleEditor.noCues')"
          :description="$t('subtitleEditor.noCuesDescription')"
        />
        <div v-else class="space-y-2">
          <div
            v-for="(cue, i) in cues"
            :key="i"
            class="grid items-center gap-2 tablet:grid-cols-[40px_110px_110px_minmax(0,1fr)_auto]"
          >
            <span class="font-mono text-body-xs text-gray-400">#{{ i + 1 }}</span>
            <input
              v-model.number="cue.start"
              type="number"
              min="0"
              step="0.1"
              class="input-field"
              :aria-label="$t('subtitleEditor.cueStart')"
              :placeholder="$t('subtitleEditor.cueStart')"
            />
            <input
              v-model.number="cue.end"
              type="number"
              min="0"
              step="0.1"
              class="input-field"
              :aria-label="$t('subtitleEditor.cueEnd')"
              :placeholder="$t('subtitleEditor.cueEnd')"
            />
            <input
              v-model="cue.text"
              type="text"
              class="input-field"
              :placeholder="$t('subtitleEditor.editCueText')"
            />
            <button
              type="button"
              class="btn-danger inline-flex items-center justify-center"
              :aria-label="$t('subtitleEditor.deleteCue')"
              @click="cues.splice(i, 1)"
            >
              <TrashIcon class="h-4 w-4" />
            </button>
          </div>
        </div>
        <div class="mt-4 flex justify-end gap-2">
          <button class="btn-secondary" @click="addCue">
            {{ $t('subtitleEditor.addCue') }}
          </button>
          <button class="btn-primary" :disabled="saving" @click="saveCues">
            {{ saving ? $t('subtitleEditor.saving') : $t('subtitleEditor.save') }}
          </button>
        </div>
      </div>

      <!-- 납출 탭 -->
      <div v-else>
        <label
          class="mb-1 block text-body font-medium text-gray-700 dark:text-gray-300"
          for="subtitle-export-format"
        >
          {{ $t('subtitleEditor.export.format') }}
        </label>
        <select id="subtitle-export-format" v-model="exportFormat" class="input-field w-full tablet:max-w-xs">
          <option v-for="f in EXPORT_FORMATS" :key="f" :value="f">
            {{ $t(`subtitleEditor.export.formats.${f}`) }}
          </option>
        </select>
        <pre
          class="mt-3 max-h-64 overflow-auto rounded-xl border border-gray-200 bg-gray-50 p-3 font-mono text-body-xs text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
        >{{ exportPreview }}</pre>
        <div class="mt-4 flex justify-end">
          <button class="btn-primary" :disabled="cues.length === 0 || exporting" @click="downloadExport">
            {{ exporting ? $t('subtitleEditor.exporting') : $t('subtitleEditor.export.button') }}
          </button>
        </div>
      </div>
    </section>

    <!-- 트랙 삭제 확인 -->
    <ConfirmModal
      v-model="deleteConfirmOpen"
      danger
      :title="$t('subtitleEditor.deleteTrack')"
      :message="$t('subtitleEditor.deleteTrackConfirm')"
      @confirm="confirmDeleteTrack"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrashIcon } from '@heroicons/vue/24/outline'
import {
  subtitleEditorApi,
  parseCues,
  serializeCues,
  countWords,
  totalDurationOf,
  exportCues,
  type SubtitleCue,
  type SubtitleExportFormat,
  type SubtitleTrackResponse,
} from '@/api/subtitleEditor'
import { videoApi } from '@/api/video'
import type { Video } from '@/types/video'
import { useNotificationStore } from '@/stores/notification'
import PageHeader from '@/components/common/PageHeader.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import OTabs from '@/components/ui/OTabs.vue'

const { t, te } = useI18n({ useScope: 'global' })
const notify = useNotificationStore()

const LANGUAGES = ['ko', 'en', 'ja', 'zh'] as const
const EXPORT_FORMATS: SubtitleExportFormat[] = ['SRT', 'VTT', 'ASS', 'TXT']

const videos = ref<Video[]>([])
const videosLoading = ref(false)
const selectedVideoId = ref(0)

const tracks = ref<SubtitleTrackResponse[]>([])
const tracksLoading = ref(false)
const selectedTrackId = ref<number | null>(null)
const cues = ref<SubtitleCue[]>([])

const newTrackLanguage = ref<string>('ko')
const creating = ref(false)
const saving = ref(false)
const exporting = ref(false)
const activeTab = ref('edit')
const exportFormat = ref<SubtitleExportFormat>('SRT')
const deleteConfirmOpen = ref(false)
const pendingDeleteTrack = ref<SubtitleTrackResponse | null>(null)

const tabs = computed(() => [
  { key: 'edit', label: t('subtitleEditor.cueEditor') },
  { key: 'export', label: t('subtitleEditor.export.title') },
])

const selectedTrack = computed(
  () => tracks.value.find((track) => track.id === selectedTrackId.value) ?? null,
)

const exportPreview = computed(() => exportCues(cues.value, exportFormat.value))

function cueCountOf(track: SubtitleTrackResponse): number {
  return parseCues(track.cues).length
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'READY':
      return 'bg-success-subtle text-success-strong'
    case 'GENERATING':
      return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
    case 'EXPORTED':
      return 'bg-warning-subtle text-warning-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatDate(iso: string): string {
  // 백엔드 LocalDateTime 은 타임존 없는 ISO 문자열이다
  return iso.slice(0, 16).replace('T', ' ')
}

/** 언어 코드 → 라벨. 목록에 없는 코드는 원문을 그대로 보여 준다 */
function languageLabel(language: string): string {
  const key = `subtitleEditor.generate.languageOptions.${language}`
  return te(key) ? t(key) : language
}

async function loadVideos() {
  videosLoading.value = true
  try {
    const res = await videoApi.list({ page: 0, size: 50 })
    // 자막은 동영상에만 단다 (이미지 에셋 제외)
    videos.value = res.content.filter((v) => v.mediaType === 'VIDEO')
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.loadFailed'))
  } finally {
    videosLoading.value = false
  }
}

async function onVideoChange() {
  selectedTrackId.value = null
  cues.value = []
  tracksLoading.value = true
  try {
    tracks.value = await subtitleEditorApi.listTracksByVideo(selectedVideoId.value)
  } catch (e) {
    tracks.value = []
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.loadFailed'))
  } finally {
    tracksLoading.value = false
  }
}

function selectTrack(track: SubtitleTrackResponse) {
  selectedTrackId.value = track.id
  cues.value = parseCues(track.cues)
  activeTab.value = 'edit'
}

async function createTrack() {
  if (selectedVideoId.value <= 0) return
  creating.value = true
  try {
    const video = videos.value.find((v) => v.id === selectedVideoId.value)
    const created = await subtitleEditorApi.createTrack({
      videoId: selectedVideoId.value,
      videoTitle: video?.title ?? null,
      language: newTrackLanguage.value,
      cues: '[]',
    })
    tracks.value = [...tracks.value, created]
    selectTrack(created)
    notify.success(t('subtitleEditor.created'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.createFailed'))
  } finally {
    creating.value = false
  }
}

function addCue() {
  const lastEnd = cues.value.length > 0 ? Math.max(...cues.value.map((c) => c.end)) : 0
  cues.value.push({ start: lastEnd, end: lastEnd + 2, text: '' })
}

/** 저장 — 큐 직렬화와 지표(단어 수·총 길이)를 함께 올린다. 큐가 있으면 READY 로 본다 */
async function saveCues() {
  const track = selectedTrack.value
  if (!track) return
  saving.value = true
  try {
    const hasCues = cues.value.length > 0
    const updated = await subtitleEditorApi.updateTrack(track.id, {
      cues: serializeCues(cues.value),
      wordCount: countWords(cues.value),
      totalDuration: totalDurationOf(cues.value),
      status: hasCues && track.status === 'DRAFT' ? 'READY' : undefined,
    })
    tracks.value = tracks.value.map((item) => (item.id === updated.id ? updated : item))
    notify.success(t('subtitleEditor.saved'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.saveFailed'))
  } finally {
    saving.value = false
  }
}

/** 납출 — 포맷 변환은 클라이언트에서 하고, 성공하면 트랙 상태를 EXPORTED 로 올린다 */
async function downloadExport() {
  const track = selectedTrack.value
  if (!track || cues.value.length === 0) return
  exporting.value = true
  try {
    const content = exportCues(cues.value, exportFormat.value)
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `subtitle-track-${track.id}.${exportFormat.value.toLowerCase()}`
    a.click()
    URL.revokeObjectURL(url)

    const updated = await subtitleEditorApi.updateTrack(track.id, { status: 'EXPORTED' })
    tracks.value = tracks.value.map((item) => (item.id === updated.id ? updated : item))
    notify.success(t('subtitleEditor.exported'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.exportFailed'))
  } finally {
    exporting.value = false
  }
}

function askDeleteTrack(track: SubtitleTrackResponse) {
  pendingDeleteTrack.value = track
  deleteConfirmOpen.value = true
}

async function confirmDeleteTrack() {
  const track = pendingDeleteTrack.value
  if (!track) return
  try {
    await subtitleEditorApi.deleteTrack(track.id)
    tracks.value = tracks.value.filter((item) => item.id !== track.id)
    if (selectedTrackId.value === track.id) {
      selectedTrackId.value = null
      cues.value = []
    }
    notify.success(t('subtitleEditor.deleted'))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('subtitleEditor.deleteFailed'))
  }
}

onMounted(loadVideos)
</script>
